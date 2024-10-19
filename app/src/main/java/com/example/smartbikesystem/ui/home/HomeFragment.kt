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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartbikesystem.databinding.FragmentHomeBinding
import com.example.smartbikesystem.obdii.ObdIIManager

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    private var devices: MutableList<BluetoothDevice> = mutableListOf()

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

    // 用於請求藍牙權限的 Launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                // 權限已授權，繼續執行
                displayPairedDevices()
                scanForNewDevices()
            } else {
                Toast.makeText(context, "需要藍牙權限來顯示設備", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化藍牙適配器
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // 初始化 RecyclerView
        binding.bluetoothDeviceList.layoutManager = LinearLayoutManager(requireContext())
        deviceAdapter = BluetoothDeviceAdapter { device ->
            // 連接按鈕點擊邏輯
            connectToDevice(device)
        }
        binding.bluetoothDeviceList.adapter = deviceAdapter

        // 檢查藍牙是否開啟
        if (bluetoothAdapter.isEnabled) {
            // 檢查藍牙權限
            checkBluetoothPermission()
        } else {
            // 如果藍牙未開啟，請求用戶啟用藍牙
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetoothEnableLauncher.launch(enableBtIntent)
        }

        // 搜尋藍牙裝置按鈕
        binding.searchDevices.setOnClickListener {
            checkBluetoothPermission()
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
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            } else {
                // 權限已授權，開始掃描
                displayPairedDevices()
                scanForNewDevices()
            }
        } else {
            // Android 12 以下版本不需要這些權限
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

    private fun connectToDevice(device: BluetoothDevice) {
        // 嘗試建立 Bluetooth 連接
        val obdIIManager = ObdIIManager(device)
        obdIIManager.connect()

        if (obdIIManager.isConnected()) {
            Toast.makeText(requireContext(), "設備連接成功", Toast.LENGTH_SHORT).show()

            // 更新設備的外框顏色為綠色
            deviceAdapter.updateConnectionState(device.address, true)

            // 使用 Safe Args 導航到 SensorFragment
            val action = HomeFragmentDirections.actionHomeFragmentToSensorFragment()
            findNavController().navigate(action)
        } else {
            Toast.makeText(requireContext(), "無法連接設備", Toast.LENGTH_SHORT).show()
        }
    }

    // 藍牙掃描結果的 BroadcastReceiver
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String? = intent?.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? =
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
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
