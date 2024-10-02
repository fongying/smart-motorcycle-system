package com.example.smartbikesystem.ui.home

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
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
    private var devices: List<BluetoothDevice> = emptyList()

    // 權限請求代碼
    private val REQUEST_BLUETOOTH_PERMISSION = 1

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
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1)
        }
    }

    private fun checkBluetoothPermission() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 如果權限未被授予，請求權限
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BLUETOOTH_PERMISSION
            )
        } else {
            // 如果權限已被授予，執行取得配對裝置
            displayPairedDevices()
        }
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
                // 如果權限被授予，繼續執行
                displayPairedDevices()
            } else {
                // 如果權限被拒絕，提示用戶
                Toast.makeText(context, "需要藍牙權限來顯示設備", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayPairedDevices() {
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        if (pairedDevices != null && pairedDevices.isNotEmpty()) {
            devices = pairedDevices.toList()
            deviceAdapter.updateDevices(devices)
        } else {
            // 顯示沒有配對設備的提示
            Toast.makeText(context, "沒有找到配對的設備", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        // 實作藍牙連接邏輯
    }
}
