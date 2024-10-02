package com.example.smartbikesystem.ui.home

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartbikesystem.R

class HomeFragment : Fragment() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var deviceList: RecyclerView
    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    private lateinit var sensorDataText: TextView
    private var devices: MutableList<BluetoothDevice> = mutableListOf()

    // 用於處理藍牙權限請求結果的 ActivityResultLauncher
    private val requestBluetoothPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.BLUETOOTH_CONNECT] == true &&
                    permissions[Manifest.permission.BLUETOOTH_SCAN] == true

            if (granted) {
                // 權限授予後執行配對裝置顯示和掃描新裝置
                displayPairedDevices()
                scanForNewDevices()
            } else {
                Toast.makeText(context, "需要藍牙權限來顯示設備", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化藍牙適配器
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // 檢查藍牙是否開啟
        if (bluetoothAdapter.isEnabled) {
            // 初始化RecyclerView
            deviceList = view.findViewById(R.id.bluetooth_device_list)
            deviceList.layoutManager = LinearLayoutManager(requireContext())
            deviceAdapter = BluetoothDeviceAdapter(devices) { device ->
                // 連接按鈕點擊邏輯
                connectToDevice(device)
            }
            deviceList.adapter = deviceAdapter

            // 檢查藍牙權限
            checkBluetoothPermission()
        } else {
            // 如果藍牙未開啟，提示用戶開啟藍牙
            Toast.makeText(context, "請先開啟藍牙", Toast.LENGTH_SHORT).show()
            // 請求開啟藍牙
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 1)
        }
    }

    private fun checkBluetoothPermission() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 如果權限未被授予，請求權限
            requestBluetoothPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        } else {
            // 如果權限已被授予，執行取得配對裝置和掃描新裝置
            try {
                displayPairedDevices()
                scanForNewDevices()
            } catch (e: SecurityException) {
                Toast.makeText(requireContext(), "缺少藍牙權限，無法繼續操作", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayPairedDevices() {
        try {
            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
            if (pairedDevices != null && pairedDevices.isNotEmpty()) {
                for (device in pairedDevices) {
                    if (!isDeviceAlreadyAdded(device)) {
                        devices.add(device)
                    }
                }
                deviceAdapter.updateDevices(devices)
            } else {
                // 顯示沒有配對設備的提示
                Toast.makeText(context, "沒有找到配對的設備", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "缺少藍牙權限，無法顯示配對裝置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scanForNewDevices() {
        try {
            // 註冊發現裝置的廣播接收器
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            requireActivity().registerReceiver(receiver, filter)

            // 啟動裝置搜尋
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            bluetoothAdapter.startDiscovery()
            Toast.makeText(context, "開始掃描藍牙裝置...", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "缺少藍牙掃描權限", Toast.LENGTH_SHORT).show()
        }
    }

    // 廣播接收器來處理新發現的裝置
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (BluetoothDevice.ACTION_FOUND == intent?.action) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    if (!isDeviceAlreadyAdded(it)) {
                        devices.add(it)
                        deviceAdapter.updateDevices(devices)
                    }
                }
            }
        }
    }

    // 檢查裝置是否已經存在於列表中
    private fun isDeviceAlreadyAdded(device: BluetoothDevice): Boolean {
        for (existingDevice in devices) {
            if (existingDevice.address == device.address) {
                return true
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        // 取消註冊廣播接收器
        requireActivity().unregisterReceiver(receiver)
    }

    private fun connectToDevice(device: BluetoothDevice) {
        // 實作藍牙連接邏輯
    }
}
