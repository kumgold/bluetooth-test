package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetoothtestapplication.R

class ScanResultAdapter(
    private val list: List<ScanResult>,
    private val onClick: (ScanResult) -> Unit
) : RecyclerView.Adapter<ScanResultAdapter.ViewHolder>() {
    class ViewHolder(view: View, onClick: (ScanResult) -> Unit) : RecyclerView.ViewHolder(view) {
        private var scanResult: ScanResult? = null

        private val deviceNameTextView: TextView = view.findViewById(R.id.device_name)
        private val deviceAddressTextView: TextView = view.findViewById(R.id.device_address)

        init {
            view.setOnClickListener {
                scanResult?.let {
                    onClick(it)
                }
            }
        }

        @SuppressLint("MissingPermission")
        fun bind(result: ScanResult) {
            scanResult = result

            deviceNameTextView.text = scanResult?.device?.name
            deviceAddressTextView.text = scanResult?.device?.address
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_bluetooth_device_item, parent, false)

        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = list[position]
        holder.bind(result)
    }

    override fun getItemCount(): Int = list.size
}