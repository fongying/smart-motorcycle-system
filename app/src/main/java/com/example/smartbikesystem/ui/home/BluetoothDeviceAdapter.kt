package com.example.smartbikesystem.ui.home

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartbikesystem.R

class BluetoothDeviceAdapter(
    private var deviceList: List<BluetoothDevice>,
    private val connectCallback: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deviceName: TextView = view.findViewById(R.id.device_name)
        val deviceAddress: TextView = view.findViewById(R.id.device_address)
        val connectButton: Button = view.findViewById(R.id.connect_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_home_device_item, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = deviceList[position]
        holder.deviceName.text = device.name ?: "未知設備"
        holder.deviceAddress.text = device.address
        holder.connectButton.setOnClickListener {
            connectCallback(device)
        }
    }

    override fun getItemCount(): Int {
        return deviceList.size
    }

    fun updateDevices(newDevices: List<BluetoothDevice>) {
        deviceList = newDevices
        notifyDataSetChanged()
    }
}
