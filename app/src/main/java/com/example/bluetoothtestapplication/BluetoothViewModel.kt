package com.example.bluetoothtestapplication

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BluetoothViewModel : ViewModel() {
    private val _scanResults = MutableLiveData<MutableList<BluetoothDevice>>(mutableListOf())
    val scanResults: LiveData<MutableList<BluetoothDevice>> = _scanResults

    fun addBluetoothDevice(device: BluetoothDevice) {
        _scanResults.value?.add(device)
    }

    fun clearScanResult() {
        _scanResults.value = mutableListOf()
    }
}