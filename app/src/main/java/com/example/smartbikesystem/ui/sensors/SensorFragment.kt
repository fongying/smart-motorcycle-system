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
import java.io.IOException

class SensorFragment : Fragment() {

    private lateinit var obdIIManager: ObdIIManager
    private lateinit var speedTextView: TextView
    private lateinit var rpmTextView: TextView
    private lateinit var tempTextView: TextView
    private lateinit var voltageTextView: TextView

    // 定義權限請求代碼
    private val PERMISSION_REQUEST_CODE = 1001
    private val MAX_RETRY_COUNT = 3
    private var retryCount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sensors, container, false)

        speedTextView = view.findViewById(R.id.speedTextView)
        rpmTextView = view.findViewById(R.id.rpmTextView)
        tempTextView = view.findViewById(R.id.tempTextView)
        voltageTextView = view.findViewById(R.id.voltageTextView)

        // 開始連接 OBD II 並獲取數據
        connectAndFetchData()

        return view
    }

    // 使用 Coroutine 來處理 OBD II 連接
    private fun connectAndFetchData() {
        CoroutineScope(Dispatchers.IO).launch {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

            // 權限檢查 (Android 12 及以上)
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                withContext(Dispatchers.Main) {
                    showToast("OBD II 連接失敗，請授權藍牙權限")
                    // 如果沒有權限，請求權限
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                        PERMISSION_REQUEST_CODE
                    )
                }
                return@launch
            }

            // 獲取已配對的藍牙設備
            val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter?.bondedDevices ?: emptySet()
            val bluetoothDevice = pairedDevices.firstOrNull { it.name == "OBDII" } // 使用實際 OBD 設備名稱

            if (bluetoothDevice != null) {
                obdIIManager = ObdIIManager(bluetoothDevice)

                try {
                    retryCount = 0
                    while (retryCount < MAX_RETRY_COUNT) {
                        try {
                            obdIIManager.connect()

                            // 確保 OBD II 已成功連接
                            if (obdIIManager.isConnected()) {
                                withContext(Dispatchers.Main) {
                                    updateSensorData() // 更新數據的主線程操作
                                }
                                return@launch
                            } else {
                                retryCount++
                            }
                        } catch (e: IOException) {
                            retryCount++
                            if (retryCount >= MAX_RETRY_COUNT) {
                                break
                            }
                        }
                    }

                    // 超過重試次數，仍未連接成功
                    withContext(Dispatchers.Main) {
                        Log.e("SensorFragment", "OBD II 連接失敗，超出重試次數")
                        showToast("OBD II 連接失敗，請檢查設備")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Log.e("SensorFragment", "連接 OBD II 時發生錯誤", e)
                        showToast("連接 OBD II 時發生錯誤")
                    }
                }

            } else {
                withContext(Dispatchers.Main) {
                    Log.e("SensorFragment", "找不到配對的 OBD 裝置")
                    showToast("找不到配對的 OBD 裝置")
                }
            }
        }
    }

    // 處理權限請求結果
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 權限已授權，重新執行連接 OBDII 的邏輯
                connectAndFetchData()
            } else {
                // 權限被拒絕，顯示錯誤訊息
                showToast("藍牙權限被拒絕，無法連接 OBDII")
            }
        }
    }

    // 更新數據
    private fun updateSensorData() {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                // 獲取並解析車速
                val speedResponse = obdIIManager.getVehicleSpeed()
                val speed = obdIIManager.parseVehicleSpeed(speedResponse)
                speedTextView.text = "$speed km/h"

                // 獲取並解析引擎轉速
                val rpmResponse = obdIIManager.getEngineRPM()
                val rpm = obdIIManager.parseEngineRPM(rpmResponse)
                rpmTextView.text = "$rpm RPM"

                // 獲取並解析引擎冷卻液溫度
                val tempResponse = obdIIManager.getEngineTemp()
                val temp = obdIIManager.parseEngineTemp(tempResponse)
                tempTextView.text = "$temp °C"

                // 獲取並解析電瓶電壓
                val voltageResponse = obdIIManager.getBatteryVoltage()
                val voltage = obdIIManager.parseBatteryVoltage(voltageResponse)
                voltageTextView.text = "$voltage V"

                handler.postDelayed(this, 500) // 每 500 毫秒更新一次
            }
        }, 500)
    }

    // 顯示 Toast 的方法，避免 NullPointerException
    private fun showToast(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }
}
