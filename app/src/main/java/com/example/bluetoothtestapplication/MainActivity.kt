package com.example.bluetoothtestapplication

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.bluetoothtestapplication.ui.theme.BluetoothTestApplicationTheme

private fun PackageManager.missingSystemFeature(name: String): Boolean = !hasSystemFeature(name)

private val BluetoothAdapter.isDisabled: Boolean
    get() = !isEnabled

class MainActivity : ComponentActivity() {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // TODO
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)


        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkBluetoothFeature()
        launchBluetoothAdapterIntent()

        scanBluetoothLEDevices(bluetoothAdapter?.isEnabled ?: false)

        setContent {
            BluetoothTestApplicationTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                }
            }
        }
    }

    private fun checkBluetoothFeature() {
        packageManager.takeIf { it.missingSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) }?.also {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun launchBluetoothAdapterIntent() {
        bluetoothAdapter?.takeIf { it.isDisabled }?.apply {
            val bluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activityResultLauncher.launch(bluetoothIntent)
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanBluetoothLEDevices(enable: Boolean) {
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
