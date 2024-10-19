package com.example.smartbikesystem.ui.home

import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.smartbikesystem.R

class BluetoothDeviceAdapter(
    private val onDeviceSelected: (BluetoothDevice) -> Unit,
    private val onConnectClicked: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<BluetoothDevice>()
    private val connectedDevices = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_home_device_item, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
    }

    override fun getItemCount(): Int = devices.size

    fun updateDevices(deviceList: List<BluetoothDevice>) {
        val diffCallback = DeviceDiffCallback(devices, deviceList)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        devices.clear()
        devices.addAll(deviceList)
        diffResult.dispatchUpdatesTo(this)
    }

    fun updateConnectionState(deviceAddress: String, isConnected: Boolean) {
        if (isConnected) {
            connectedDevices.add(deviceAddress)
        } else {
            connectedDevices.remove(deviceAddress)
        }
        notifyDataSetChanged()
    }

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceName: TextView = itemView.findViewById(R.id.device_name)
        val connectButton: Button = itemView.findViewById(R.id.connect_button)

        fun bind(device: BluetoothDevice) {
            deviceName.text = device.name ?: "Unknown Device"
            connectButton.setOnClickListener {
                onConnectClicked(device)
            }

            itemView.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onDeviceSelected(device)
                }
            }

            if (connectedDevices.contains(device.address)) {
                itemView.setBackgroundResource(R.drawable.green_border)
            } else {
                itemView.setBackgroundResource(R.drawable.red_border)
            }
        }
    }

    class DeviceDiffCallback(
        private val oldList: List<BluetoothDevice>,
        private val newList: List<BluetoothDevice>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].address == newList[newItemPosition].address
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].name == newList[newItemPosition].name
        }
    }
}
