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
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.bluetoothtestapplication.databinding.ActivityBluetoothBinding

class BluetoothActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBluetoothBinding
    private val viewModel: BluetoothViewModel by viewModels()

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

        checkBluetoothFeature()
        launchBluetoothAdapterIntent()
        bluetoothScanListener()

        viewModel.scanResults.observe(this) { list ->
            list.forEach {
                Log.d("BLUETOOTH", "${it.name} ${it.uuids}")
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

    private fun bluetoothScanListener() {
        binding.scanButton.setOnClickListener {
            scanBluetoothLEDevices(bluetoothAdapter?.isEnabled ?: false)
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

    @SuppressLint("MissingPermission")
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

private fun PackageManager.missingSystemFeature(name: String): Boolean = !hasSystemFeature(name)

private val BluetoothAdapter.isDisabled: Boolean
    get() = !isEnabled