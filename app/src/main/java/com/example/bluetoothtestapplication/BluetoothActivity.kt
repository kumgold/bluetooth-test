package com.example.bluetoothtestapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.bluetoothtestapplication.databinding.ActivityBluetoothBinding

class BluetoothActivity : AppCompatActivity() {
    companion object {
        private const val BLUETOOTH_TAG = "BLUETOOTH TAG"
        private const val BLUETOOTH_REQUEST_CODE = 50

        private val BLUETOOTH_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        @RequiresApi(Build.VERSION_CODES.S)
        private val BLUETOOTH_S_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private lateinit var binding: ActivityBluetoothBinding
    private val viewModel: BluetoothViewModel by viewModels()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val handler = Handler(Looper.getMainLooper())

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            Log.d(BLUETOOTH_TAG, "scan callback on result")

            addScanResult(result)
        }
    }

    private val gattCallback = object  :  BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
        }
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            TODO("Not yet implemented")
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            TODO("Not yet implemented")
        }
    }

    private var bluetoothService: BluetoothLeService? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothBinding.inflate(layoutInflater)

        setContentView(binding.root)

        bluetoothScanListener()
        observeScanResult()
    }

    private fun bluetoothScanListener() {
        binding.scanButton.setOnClickListener {
            if (hasBluetoothPermissions()) {
                scanBluetoothLEDevices()
            } else {
                bluetoothPermissionDeniedMessage()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanBluetoothLEDevices() {
        when (isScanning) {
            true -> {
                isScanning = false
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            }
            else -> {
                handler.postDelayed({
                    isScanning = false
                    bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
                }, 5000)
                isScanning = true
                bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
            }
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

        device?.let { viewModel.addBluetoothDevice(it) }
        Log.d(BLUETOOTH_TAG, "scan result : $device ${device?.name} ${device?.uuids}")
    }

    @SuppressLint("MissingPermission")
    private fun connectGattService(result: ScanResult?) {
        bluetoothGatt = result?.device?.connectGatt(this, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    private fun observeScanResult() {
        viewModel.scanResults.observe(this) { list ->
            list.forEach {
                if (hasBluetoothPermissions()) {
                    Log.d(BLUETOOTH_TAG, "BLUETOOTH DEVICE : ${it.name} ${it.uuids}")
                }
            }
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(this, BLUETOOTH_S_PERMISSIONS)) {
               requestPermissions(BLUETOOTH_S_PERMISSIONS, BLUETOOTH_REQUEST_CODE)
                return false
            } else {
                return true
            }
        } else {
            if (!hasPermission(this, BLUETOOTH_PERMISSIONS)) {
                requestPermissions(BLUETOOTH_PERMISSIONS, BLUETOOTH_REQUEST_CODE)
                return false
            } else {
                return true
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
