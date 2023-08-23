package com.example.bluetoothtestapplication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BluetoothDeviceListAdapter(
    private val devices: MutableList<BluetoothDevice>,
    private val onClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceListAdapter.ViewHolder>() {

    class ViewHolder(view: View, onClick: (BluetoothDevice) -> Unit) : RecyclerView.ViewHolder(view) {
        private var currentDevice: BluetoothDevice? = null

        private val deviceNameTextView: TextView = view.findViewById(R.id.device_name)
        private val deviceAddressTextView: TextView = view.findViewById(R.id.device_address)

        init {
            view.setOnClickListener {
                currentDevice?.let {
                    onClick(it)
                }
            }
        }

        @SuppressLint("MissingPermission")
        fun bind(device: BluetoothDevice) {
            currentDevice = device

            deviceNameTextView.text = device.name
            deviceAddressTextView.text = device.address
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.view_bluetooth_device_item, parent, false)

        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
    }

    override fun getItemCount(): Int = devices.size

    @SuppressLint("NotifyDataSetChanged")
    fun add(device: BluetoothDevice) {
        devices.add(device)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clear() {
        devices.clear()
        notifyDataSetChanged()
    }
}