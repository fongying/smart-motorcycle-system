package com.example.smartbikesystem.ui.sensors

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.smartbikesystem.R
import com.example.smartbikesystem.obdii.ObdIIManager
import com.example.smartbikesystem.service.GForceViewModel
import kotlinx.coroutines.*
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.TimeUnit

class SensorFragment : Fragment() {

    private lateinit var speedTextView: TextView
    private lateinit var rpmTextView: TextView
    private lateinit var tempTextView: TextView
    private lateinit var voltageTextView: TextView
    private lateinit var gForceTextView: TextView

    private val gForceViewModel: GForceViewModel by activityViewModels()
    private val obdScope = CoroutineScope(Dispatchers.IO + Job())
    private val esp32Scope = CoroutineScope(Dispatchers.IO + Job())

    private lateinit var obdIIManager: ObdIIManager
    private var esp32Socket: BluetoothSocket? = null
    private var esp32InputStream: InputStream? = null

    private val CHANNEL_ID = "accident_alert_channel"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sensors, container, false)

        // 接收從 HomeFragment 傳遞的資料
        val name = arguments?.getString("name") ?: "使用者名稱"
        val ipAddress = arguments?.getString("ipAddress") ?: "192.168.0.100:80"
        val latitude = arguments?.getString("latitude") ?: "0.0"
        val longitude = arguments?.getString("longitude") ?: "0.0"
        val gForceThreshold = arguments?.getFloat("gForceThreshold") ?: 2.0f

        Log.d("SensorFragment", "收到的資料：name=$name, ipAddress=$ipAddress, latitude=$latitude, longitude=$longitude, gForceThreshold=$gForceThreshold")

        // 初始化 TextView
        speedTextView = view.findViewById(R.id.speedTextView)
        rpmTextView = view.findViewById(R.id.rpmTextView)
        tempTextView = view.findViewById(R.id.tempTextView)
        voltageTextView = view.findViewById(R.id.voltageTextView)
        gForceTextView = view.findViewById(R.id.gForceTextView)

        createNotificationChannel()
        setupObservers(gForceThreshold)
        connectToDevices()

        return view
    }

    private fun setupObservers(gForceThreshold: Float) {
        gForceViewModel.gForce.observe(viewLifecycleOwner) { gForce ->
            Log.d("SensorFragment", "G-force 數據更新: $gForce")
            updateGForceUI(gForce)
            checkGForceThreshold(gForce, gForceThreshold)
        }

        gForceViewModel.obdData.observe(viewLifecycleOwner) { data ->
            updateOBDUI(data)
        }
    }



    private fun connectToDevices() {
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (!hasBluetoothPermission()) {
            requestBluetoothPermission()
            return
        }

        connectDevice(bluetoothAdapter, "OBDII") { device -> connectOBDII(device) }
        connectDevice(bluetoothAdapter, "ESP32_GForce") { device -> connectESP32(device) }
    }

    private fun connectESP32(device: BluetoothDevice) {
        esp32Scope.launch {
            try {
                esp32Socket = device.createRfcommSocketToServiceRecord(
                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                )
                esp32Socket?.connect()
                esp32InputStream = esp32Socket?.inputStream
                Log.d("SensorFragment", "ESP32 已連接")
                startListeningToESP32()
            } catch (e: Exception) {
                Log.e("SensorFragment", "ESP32 連接失敗: ${e.message}")
            }
        }
    }

    private fun startListeningToESP32() {
        esp32Scope.launch {
            val buffer = ByteArray(1024)
            while (isActive) {
                try {
                    val bytesRead = esp32InputStream?.read(buffer) ?: 0
                    val receivedData = String(buffer, 0, bytesRead).trim()

                    if (receivedData.isNotEmpty()) {
                        Log.d("SensorFragment", "ESP32 Received: $receivedData")
                        val gForce = parseGForceData(receivedData)

                        if (gForce != null) {
                            gForceViewModel.processGForce(gForce)
                        }
                    }
                    delay(500)  // 增加延遲避免資料衝突
                } catch (e: Exception) {
                    Log.e("SensorFragment", "ESP32 資料接收錯誤: ${e.message}")
                }
            }
        }
    }

    private fun parseGForceData(data: String): Float? {
        return try {
            val parts = data.split(",")
            if (parts.isNotEmpty()) {
                parts[0].toFloatOrNull()  // 只取第一個數值 (x 軸)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("SensorFragment", "解析 G 力數據失敗: ${e.message}")
            null
        }
    }


    private var isDialogShown = false  // 用於追蹤對話框是否已顯示

    private fun showAccidentDialog(gForce: Float) {
        if (isDialogShown) {
            Log.d("SensorFragment", "事故對話框已顯示，忽略本次彈出")
            return  // 如果對話框已經顯示，則不再彈出
        }

        isDialogShown = true  // 設定旗標為已顯示

        val name = arguments?.getString("name") ?: "未命名"
        val ipAddress = arguments?.getString("ipAddress") ?: "192.168.0.100:80"
        val latitude = arguments?.getString("latitude")?.toFloatOrNull() ?: 0f
        val longitude = arguments?.getString("longitude")?.toFloatOrNull() ?: 0f
        val speed = gForceViewModel.obdData.value?.get("speed")?.toString() ?: "0 km/h"

        // 在偵測到事故時立即發送初始報告
        esp32Scope.launch {
            val success = sendAccidentReport(name, latitude, longitude, speed, ipAddress)
            withContext(Dispatchers.Main) {
                if (success) {
                    showToast("事故報告已成功發送")
                } else {
                    showToast("發送事故報告失敗")
                }
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("事故偵測")
            .setMessage("偵測到 G 力：$gForce g。是否需要協助？")
            .setPositiveButton("需要協助") { _, _ ->
                esp32Scope.launch {
                    sendHelpResponse(name, false, ipAddress)  // 標記為需要協助
                    isDialogShown = false  // 回應後重置旗標
                }
            }
            .setNegativeButton("不需要協助") { _, _ ->
                esp32Scope.launch {
                    sendHelpResponse(name, true, ipAddress)  // 標記為不需要協助
                    isDialogShown = false  // 回應後重置旗標
                }
            }
            .setOnDismissListener {
                isDialogShown = false  // 當對話框被手動關閉時重置旗標
            }
            .create()

        dialog.show()

        // 30 秒計時器，如果未回應則自動標記為需要協助
        esp32Scope.launch {
            delay(30_000)  // 等待 30 秒
            if (dialog.isShowing) {
                sendHelpResponse(name, false, ipAddress)  // 自動標記為需要協助
                isDialogShown = false  // 自動回應後重置旗標
            }
        }
    }


    // 發送事故報告的函數
    private suspend fun sendAccidentReport(
        name: String, latitude: Float, longitude: Float, speed: String, ipAddress: String?
    ): Boolean {
        val validIpAddress = ipAddress?.takeIf { it.isNotBlank() } ?: "192.168.0.100:80"
        val url = "http://$validIpAddress/accident"

        val requestBody = FormBody.Builder()
            .add("name", name)
            .add("latitude", latitude.toString())
            .add("longitude", longitude.toString())
            .add("speed", speed)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: IOException) {
            Log.e("SensorFragment", "報告發送失敗: ${e.message}")
            false
        }
    }


    // 發送協助回應的函數
    private suspend fun sendHelpResponse(name: String, noNeedHelp: Boolean, ipAddress: String?) {
        val validIpAddress = ipAddress?.takeIf { it.isNotBlank() } ?: "192.168.0.100:80"
        val url = "http://$validIpAddress/accident_response"

        val requestBody = FormBody.Builder()
            .add("name", name)
            .add("no_need_help", noNeedHelp.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                showToast("回應已成功發送")
            } else {
                showToast("發送回應失敗")
            }
        } catch (e: IOException) {
            Log.e("SensorFragment", "回應發送失敗: ${e.message}")
        }
    }



    private fun connectDevice(bluetoothAdapter: BluetoothAdapter, deviceName: String, onConnected: (BluetoothDevice) -> Unit) {
        val device = getPairedDevice(bluetoothAdapter, deviceName)
        if (device != null) {
            onConnected(device)
        } else {
            showToast("找不到 $deviceName 裝置")
        }
    }

    private fun connectOBDII(device: BluetoothDevice) {
        obdIIManager = ObdIIManager(device)
        obdScope.launch {
            if (obdIIManager.connect()) {
                Log.d("SensorFragment", "OBD II 已連接")
                monitorOBDData()
            } else {
                Log.e("SensorFragment", "OBD II 連接失敗")
                showToast("無法連接 OBD II 裝置")
            }
        }
    }

    private fun monitorOBDData() {
        obdScope.launch {
            while (isActive) {
                val data = obdIIManager.fetchOBDData()
                gForceViewModel.updateOBDData(data)  // 更新 OBD 資料到 ViewModel
                delay(300)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "事故警報"
            val descriptionText = "通知事故偵測警報"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getPairedDevice(adapter: BluetoothAdapter, deviceName: String): BluetoothDevice? {
        return adapter.bondedDevices.firstOrNull { it.name == deviceName }
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
            1001
        )
    }

    private fun updateGForceUI(gForce: Float) {
        requireActivity().runOnUiThread {
            gForceTextView.text = "G 力: $gForce g"
        }
    }

    private fun updateOBDUI(data: Map<String, Any?>) {
        requireActivity().runOnUiThread {
            data["speed"]?.let { speed -> speedTextView.text = "$speed km/h" }
            data["rpm"]?.let { rpm -> rpmTextView.text = "$rpm RPM" }
            data["temperature"]?.let { temp -> tempTextView.text = "$temp °C" }
            data["voltage"]?.let { voltage -> voltageTextView.text = "$voltage V" }
        }
    }

    private fun checkGForceThreshold(gForce: Float, threshold: Float) {
        Log.d("SensorFragment", "G-force 閾值: $threshold")

        if (gForce >= threshold) {
            Log.d("SensorFragment", "G-force 達到閾值，觸發通報")
            showAccidentDialog(gForce)
        } else {
            Log.d("SensorFragment", "G-force 未達閾值，無需通報")
        }
    }


    private fun showToast(message: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}
