package com.example.bluetoothtestapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.bluetoothtestapplication.databinding.ActivityBluetoothBinding

class BluetoothActivity : AppCompatActivity() {
    companion object {
        private const val BLUETOOTH_TAG = "BLUETOOTH TAG"
        private const val BLUETOOTH_REQUEST_CODE = 50
        private const val STOP_SCAN_DELAY = 10_000L

        private val BLUETOOTH_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        @RequiresApi(Build.VERSION_CODES.S)
        private val BLUETOOTH_S_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private lateinit var binding: ActivityBluetoothBinding
    private val viewModel: BluetoothViewModel by viewModels()

    private val handler = Handler(Looper.getMainLooper())
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val activityCallbackResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        when (it.resultCode) {
            RESULT_OK -> {
                startScan()
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            addScanResult(result)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(BLUETOOTH_TAG, "scan failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun addScanResult(result: ScanResult?) {
        val scanResults = viewModel.scanResults.value!!
        val device = result?.device
        val deviceAddress = device?.address

        for (dev in scanResults) {
            if (dev.address == deviceAddress) return
        }

        device?.let {
            deviceListAdapter.add(it)
            viewModel.addBluetoothDevice(it)
        }
    }

    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    private val deviceListAdapter = BluetoothDeviceListAdapter(mutableListOf()) { device ->
        adapterOnClick(device)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d(BLUETOOTH_TAG, "Bluetooth gatt call back state $newState")

            when (status) {
                BluetoothGatt.GATT_FAILURE, 133 -> {
                    Log.e(BLUETOOTH_TAG, "Bluetooth gatt failure $status $newState")
                    gatt?.disconnect()
                    gatt?.close()
                }
                BluetoothGatt.GATT_SUCCESS -> {
                    when (newState) {
                        BluetoothGatt.STATE_CONNECTED -> {
                            Log.d(BLUETOOTH_TAG, "Bluetooth gatt success $status $newState")
                            gatt?.discoverServices()
                        }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)

            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.d(BLUETOOTH_TAG, "onServiceDiscovered Gatt success : $status")

                    val services = gatt?.services

                    services?.forEach { service ->
                        service.characteristics.forEach {
                            gatt.readCharacteristic(it)
                            gatt.setCharacteristicNotification(it, true)
                            setCharacteristicDescriptorsNotification(gatt, it)
                            setCharacteristicDescriptorsIndication(gatt, it)
                        }
                    }
                }
                else -> {
                    Log.e(BLUETOOTH_TAG, "onServiceDiscovered failure : $status")
                }
            }
        }

        @SuppressLint("MissingPermission")
        private fun setCharacteristicDescriptorsNotification(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic) {
            characteristic.descriptors.forEach {
                val descriptor = characteristic.getDescriptor(it.uuid)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt?.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt?.writeDescriptor(descriptor)
                }
            }
        }

        @SuppressLint("MissingPermission")
        private fun setCharacteristicDescriptorsIndication(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic) {
            characteristic.descriptors.forEach {
                val descriptor = characteristic.getDescriptor(it.uuid)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt?.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                } else {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    gatt?.writeDescriptor(descriptor)
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, value, status)

            Log.d(BLUETOOTH_TAG, "on read characteristic = $value $gatt $characteristic")
        }

        /**
         * Android Tiramisu 이전 버전은 Deprecated 함수로 값이 들어온다.
         */
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)

            val data = characteristic?.value
            val sb = StringBuilder()

            data?.forEach { b ->
                sb.append(String.format("%02X", b))
            }

            Log.d(BLUETOOTH_TAG, "on read characteristic = $sb $gatt ${characteristic?.uuid}")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)

            Log.d(BLUETOOTH_TAG, "on characteristic changed = $value $gatt $characteristic")
        }

        /**
         * Android Tiramisu 이전 버전은 Deprecated 함수로 값이 들어온다.
         */
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)

            Log.d(BLUETOOTH_TAG, "on characteristic changed = ${characteristic?.value} $gatt $characteristic")
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorRead(gatt, descriptor, status)
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
            value: ByteArray
        ) {
            super.onDescriptorRead(gatt, descriptor, status, value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)

            Log.d(BLUETOOTH_TAG, "on descriptor write = ${descriptor?.value} $gatt ${descriptor?.uuid} ${descriptor?.characteristic?.uuid}")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)

            Log.d(BLUETOOTH_TAG, "on characteristic write = ${characteristic?.value} $gatt ${characteristic?.uuid}")
        }
    }
    private var bluetoothService: BluetoothLeService? = null
    private var isScanning = false

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // For Bluetooth Permissions
        getBluetoothFeature()

        // Set Bluetooth Actions
        setDeviceListRecyclerView()

        // Button Listener
        bluetoothScanListener()
        stopScanButtonListener()
    }

    private fun getBluetoothFeature() {
        packageManager.takeIf { !it.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }?.also {
            bluetoothPermissionDeniedMessage()
            finish()
        }

        bluetoothAdapter?.takeIf { it.isDisabled }?.apply {
            val bluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activityCallbackResult.launch(bluetoothIntent)
        }
    }

    private fun setDeviceListRecyclerView() {
        binding.deviceListView.adapter = deviceListAdapter
    }

    private fun hasProperty(characteristic: BluetoothGattCharacteristic, property: Int): Boolean {
        return characteristic.properties == property
    }

    @SuppressLint("MissingPermission")
    private fun adapterOnClick(device: BluetoothDevice) {
        device.connectGatt(this, false, gattCallback)
        device.createBond()
    }

    private fun bluetoothScanListener() {
        binding.scanButton.setOnClickListener {
            startScan()
        }
    }

    private fun startScan() {
        if (hasBluetoothPermissions()) {
            scanBluetoothLEDevices()
        } else {
            bluetoothPermissionDeniedMessage()
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanBluetoothLEDevices() {
        if (hasBluetoothPermissions()) {
            when (isScanning) {
                true -> {
                    stopScanAndDiscovery()
                }
                else -> {
                    handler.postDelayed({
                        stopScanAndDiscovery()
                    }, STOP_SCAN_DELAY)

                    startScanAndDiscovery()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanAndDiscovery() {
        bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
        bluetoothAdapter?.startDiscovery()
        isScanning = true
    }

    @SuppressLint("MissingPermission")
    private fun stopScanAndDiscovery() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        bluetoothAdapter?.cancelDiscovery()
        isScanning = false
    }

    private fun stopScanButtonListener() {
        binding.stopScanButton.setOnClickListener {
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        viewModel.clearScanResult()
        deviceListAdapter.clear()
        stopScanAndDiscovery()
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(this, BLUETOOTH_S_PERMISSIONS)) {
                requestPermissions(BLUETOOTH_S_PERMISSIONS, BLUETOOTH_REQUEST_CODE)
                false
            } else {
                true
            }
        } else {
            if (!hasPermission(this, BLUETOOTH_PERMISSIONS)) {
                requestPermissions(BLUETOOTH_PERMISSIONS, BLUETOOTH_REQUEST_CODE)
                false
            } else {
                true
            }
        }
    }

    private fun hasPermission(context: Context, permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            BLUETOOTH_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    bluetoothPermissionGrantedMessage()
                } else {
                    requestPermissions(permissions, BLUETOOTH_REQUEST_CODE)
                    Toast.makeText(this, "Permissions must be Granted!", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                bluetoothPermissionDeniedMessage()
            }
        }
    }

    private fun bluetoothPermissionGrantedMessage() {
        Toast.makeText(this, "Bluetooth permission granted", Toast.LENGTH_SHORT).show()
    }

    private fun bluetoothPermissionDeniedMessage() {
        Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
    }
}