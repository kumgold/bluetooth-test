package com.example.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.bluetoothtestapplication.databinding.ActivityMainBinding
import java.util.UUID

@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    companion object {
        private const val BLUETOOTH_LOG_TAG = "ScanCallback"
        private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
        private const val RUNTIME_PERMISSION_REQUEST_CODE = 2
    }

    private lateinit var binding: ActivityMainBinding

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private var isScanning = false
        set(value) {
            field = value
            runOnUiThread { binding.scanButton.text = if (value) "Stop Scan" else "Start Scan" }
        }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
                scanResultAdapter.notifyItemChanged(indexQuery)
            } else {
                with(result.device) {
                    Log.i(BLUETOOTH_LOG_TAG, "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                }
                scanResults.add(result)
                scanResultAdapter.notifyItemInserted(scanResults.size - 1)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(BLUETOOTH_LOG_TAG, "onScanFailed: code $errorCode")
        }
    }

    private val scanResults = mutableListOf<ScanResult>()
    private val scanResultAdapter: ScanResultAdapter by lazy {
        ScanResultAdapter(scanResults) { result ->
            // User tapped on a scan result
            if (isScanning) {
                stopBleScan()
            }
            with(result.device) {
                Log.w("ScanResultAdapter", "Connecting to $address")
                connectGatt(this@MainActivity, false, gattCallback)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        private var bluetoothGatt: BluetoothGatt? = null

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                    bluetoothGatt = gatt
                    Handler(Looper.getMainLooper()).post {
                        bluetoothGatt?.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    gatt.close()
                }
            } else {
                Log.w("BluetoothGattCallback", "Error $status encountered for $deviceAddress! Disconnecting...")
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.w("BluetoothGattCallback", "Discovered ${services.size} services for ${device.address}")
                printGattTable() // See implementation just above this section
                // Consider connection setup as complete here
            }
            readBatteryLevel()
        }

        private fun readBatteryLevel() {
            val batteryServiceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
            val batteryLevelCharUuid = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
            val batteryLevelChar = bluetoothGatt
                ?.getService(batteryServiceUuid)?.getCharacteristic(batteryLevelCharUuid)
            if (batteryLevelChar?.isReadable() == true) {
                bluetoothGatt?.readCharacteristic(batteryLevelChar)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "Read characteristic $uuid: ${value.toHexString()}, ${characteristic.value.first().toInt()}")
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status")
                    }
                }
            }
        }

        // ... somewhere outside BluetoothGattCallback
        fun ByteArray.toHexString(): String =
            joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()

        setScanResultListAdapter()
        setStartScanButtonClickListener()
    }

    private fun setScanResultListAdapter() {
        binding.scanResultList.adapter = scanResultAdapter
    }

    private fun setStartScanButtonClickListener() {
        binding.scanButton.setOnClickListener {
            if (!isScanning) {
                startBleScan()
            } else {
                stopBleScan()
            }

        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        if (!hasRequiredRuntimePermissions()) {
            requestRelevantRuntimePermissions()
        } else {
            promptEnableBluetooth()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    @SuppressLint("MissingPermission", "NotifyDataSetChanged")
    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        } else {
            scanResults.clear()
            scanResultAdapter.notifyDataSetChanged()
            bleScanner.startScan(null, scanSettings, scanCallback)
            isScanning = true
        }
    }

    private fun Activity.requestRelevantRuntimePermissions() {
        if (hasRequiredRuntimePermissions()) { return }
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                requestLocationPermission()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                requestBluetoothPermissions()
            }
        }
    }

    private fun requestLocationPermission() {
        runOnUiThread {
            AlertDialog.Builder(this).apply {
                title = "Location permission required"
                setMessage("Starting from Android M (6.0), the system requires apps to be granted " +
                        "location access in order to scan for BLE devices.")
                setCancelable(false)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    requestFineLocationPermission()
                }
            }.show()
        }
    }

    private fun requestFineLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            RUNTIME_PERMISSION_REQUEST_CODE
        )
    }

    private fun requestBluetoothPermissions() {
        runOnUiThread {
            AlertDialog.Builder(this).apply {
                title = "Bluetooth permissions required"
                setMessage("Starting from Android 12, the system requires apps to be granted " +
                        "Bluetooth access in order to scan for and connect to BLE devices.")
                setCancelable(false)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    requestBluetoothPermission()
                }
            }.show()
        }
    }

    private fun requestBluetoothPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ),
            RUNTIME_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            RUNTIME_PERMISSION_REQUEST_CODE -> {
                val containsPermanentDenial = permissions.zip(grantResults.toTypedArray()).any {
                    it.second == PackageManager.PERMISSION_DENIED &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(this, it.first)
                }
                val containsDenial = grantResults.any { it == PackageManager.PERMISSION_DENIED }
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                when {
                    containsPermanentDenial -> {
                        // TODO: Handle permanent denial (e.g., show AlertDialog with justification)
                        // Note: The user will need to navigate to App Settings and manually grant
                        // permissions that were permanently denied
                    }
                    containsDenial -> {
                        requestRelevantRuntimePermissions()
                    }
                    allGranted && hasRequiredRuntimePermissions() -> {
                        startBleScan()
                    }
                    else -> {
                        // Unexpected scenario encountered when handling permissions
                        recreate()
                    }
                }
            }
        }
    }
}

private fun BluetoothGatt.printGattTable() {
    if (services.isEmpty()) {
        Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?")
        return
    }
    services.forEach { service ->
        val characteristicsTable = service.characteristics.joinToString(
            separator = "\n|--",
            prefix = "|--"
        ) { it.uuid.toString() }
        Log.i("printGattTable", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
        )
    }
}