package com.example.smartbikesystem.ui.sensors

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.smartbikesystem.R
import com.example.smartbikesystem.obdii.ObdIIManager
import kotlinx.coroutines.*

class SensorFragment : Fragment() {

    private lateinit var obdIIManager: ObdIIManager
    private lateinit var speedTextView: TextView
    private lateinit var rpmTextView: TextView
    private lateinit var tempTextView: TextView
    private lateinit var voltageTextView: TextView

    private val PERMISSION_REQUEST_CODE = 1001
    private val handler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sensors, container, false)

        speedTextView = view.findViewById(R.id.speedTextView)
        rpmTextView = view.findViewById(R.id.rpmTextView)
        tempTextView = view.findViewById(R.id.tempTextView)
        voltageTextView = view.findViewById(R.id.voltageTextView)

        connectAndFetchData()

        return view
    }

    private fun startPeriodicUpdates() {
        coroutineScope.launch {
            while (isActive) {  // 確保協程在活躍狀態時執行
                fetchAndParseSequentialData()
                delay(500)  // 每隔1秒更新一次數據，可依需求調整
            }
        }
    }


    @Suppress("DEPRECATION")
    private fun connectAndFetchData() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (!hasBluetoothPermission()) {
            requestBluetoothPermission()
            return
        }

        val device = getPairedOBDDevice(bluetoothAdapter)
        if (device != null) {
            obdIIManager = ObdIIManager(device)
            obdIIManager.connect()
            if (obdIIManager.isConnected()) {
                Log.d("SensorFragment", "OBD 已連接，開始定時更新感測資料")
                startPeriodicUpdates()  // 呼叫新的定時更新方法
            } else {
                showToast("無法連接 OBD II 裝置")
            }
        } else {
            showToast("找不到配對的 OBD II 裝置")
        }
    }

    private suspend fun fetchAndParseSequentialData() {
        val commands = listOf("010D", "010C", "0105", "ATRV")
        for (command in commands) {
            val response = obdIIManager.sendAndLogCommand(command)
            val result = obdIIManager.parseResponse(command, response)
            Log.d("SensorFragment", "回應資料: $result")
            updateUIFromResult(result)
            delay(300)  // 確保每個指令間有足夠的間隔
        }
    }



    private fun updateUIFromResult(result: Map<String, Any?>) {
        requireActivity().runOnUiThread {
            result["speed"]?.let { speed ->
                speedTextView.text = "$speed km/h"
                Log.d("SensorFragment", "更新速度: $speed km/h")
            }

            result["rpm"]?.let { rpm ->
                rpmTextView.text = "$rpm RPM"
                Log.d("SensorFragment", "更新轉速: $rpm RPM")
            }

            result["temperature"]?.let { temperature ->
                tempTextView.text = "$temperature °C"
                Log.d("SensorFragment", "更新溫度: $temperature °C")
            }

            result["voltage"]?.let { voltage ->
                voltageTextView.text = "$voltage V"
                Log.d("SensorFragment", "更新電壓: $voltage V")
            }
        }
    }


    private fun hasBluetoothPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            requireContext(), Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun getPairedOBDDevice(adapter: BluetoothAdapter?): BluetoothDevice? {
        if (!hasBluetoothPermission()) {
            Log.e("SensorFragment", "缺少藍牙權限")
            return null
        }
        val pairedDevices = adapter?.bondedDevices ?: emptySet()
        return pairedDevices.firstOrNull { it.name == "OBDII" }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            connectAndFetchData()
        } else {
            showToast("藍牙權限被拒絕，無法連接 OBD II")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}
