package com.example.smartbikesystem.ui.home

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private var devices: MutableList<BluetoothDevice> = mutableListOf()

    // 權限請求代碼
    private val REQUEST_BLUETOOTH_PERMISSION = 1

    // 用於處理藍牙啟用的 ActivityResultLauncher
    private val requestBluetoothEnableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                // 藍牙已啟用
                scanForNewDevices()
            } else {
                // 藍牙未啟用
                Toast.makeText(requireContext(), "請啟用藍牙", Toast.LENGTH_SHORT).show()
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
            // 如果藍牙未開啟，請求用戶啟用藍牙
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetoothEnableLauncher.launch(enableBtIntent)
        }
    }

    private fun checkBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // 請求藍牙相關權限
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    ),
                    REQUEST_BLUETOOTH_PERMISSION
                )
            } else {
                // 權限已授權，開始掃描
                displayPairedDevices()
                scanForNewDevices()
            }
        } else {
            // 對於 Android 12 以下版本，不需要請求這些權限
            displayPairedDevices()
            scanForNewDevices()
        }
    }

    // 顯示已配對的裝置
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
                Toast.makeText(context, "沒有配對的設備", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "權限不足：無法顯示配對設備", Toast.LENGTH_SHORT).show()
        }
    }

    // 掃描新裝置
    private fun scanForNewDevices() {
        try {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            requireActivity().registerReceiver(receiver, filter)

            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            bluetoothAdapter.startDiscovery()

            Toast.makeText(context, "開始掃描藍牙裝置", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), "權限不足：無法掃描裝置", Toast.LENGTH_SHORT).show()
        }
    }

    // 檢查是否已經加入裝置列表
    private fun isDeviceAlreadyAdded(device: BluetoothDevice): Boolean {
        return devices.any { it.address == device.address }
    }

    // 處理權限請求結果
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // 權限已授權，繼續執行
                displayPairedDevices()
                scanForNewDevices()
            } else {
                // 權限被拒絕
                Toast.makeText(context, "需要藍牙權限來顯示設備", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        // 實作藍牙連接邏輯
    }

    // 藍牙掃描結果的 BroadcastReceiver
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String? = intent?.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? =
                    intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                device?.let {
                    if (!isDeviceAlreadyAdded(it)) {
                        devices.add(it)
                        deviceAdapter.updateDevices(devices)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 取消註冊 BroadcastReceiver
        requireActivity().unregisterReceiver(receiver)
    }
}
