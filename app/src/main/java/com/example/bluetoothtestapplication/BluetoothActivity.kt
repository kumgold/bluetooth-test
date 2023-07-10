package com.example.bluetoothtestapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
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

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            Log.d(BLUETOOTH_TAG, "scan callback on result")
            addScanResult(result)
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private var isScanning = false

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBluetoothBinding.inflate(layoutInflater)

        setContentView(binding.root)

        bluetoothScanListener()

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
                    Toast.makeText(this, "Permissions Granted!", Toast.LENGTH_SHORT).show()
                } else {
                    requestPermissions(permissions, BLUETOOTH_REQUEST_CODE)
                    Toast.makeText(this, "Permissions must be Granted!", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                Toast.makeText(this, "Permissions must be Granted!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bluetoothScanListener() {
        binding.scanButton.setOnClickListener {
            scanBluetoothLEDevices(isScanning)
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanBluetoothLEDevices(enable: Boolean) {
        if (hasBluetoothPermissions()) {
            when (enable) {
                true -> {
                    handler.postDelayed({
                        isScanning = false
                        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
                    }, 5000)
                }
                else -> {
                    isScanning = true
                    bluetoothAdapter?.bluetoothLeScanner?.startScan(scanCallback)
                }
            }
        }
    }

    private fun addScanResult(result: ScanResult?) {
        val scanResults = viewModel.scanResults.value!!
        val device = result?.device
        val deviceAddress = device?.address

        for (dev in scanResults) {
            if (dev.address == deviceAddress) return
        }

        device?.let { viewModel.addBluetoothDevice(it) }
    }
}
