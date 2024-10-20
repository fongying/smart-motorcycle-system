package com.example.smartbikesystem.ui.sensors

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
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
import java.io.InputStream
import java.util.*

class SensorFragment : Fragment() {

    private lateinit var obdIIManager: ObdIIManager
    private lateinit var collisionDetectionModule: CollisionDetectionModule

    private lateinit var speedTextView: TextView
    private lateinit var rpmTextView: TextView
    private lateinit var tempTextView: TextView
    private lateinit var voltageTextView: TextView
    private lateinit var gForceTextView: TextView

    private val PERMISSION_REQUEST_CODE = 1001
    private val obdScope = CoroutineScope(Dispatchers.IO + Job())
    private val esp32Scope = CoroutineScope(Dispatchers.IO + Job())

    private var esp32Socket: BluetoothSocket? = null
    private var esp32InputStream: InputStream? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sensors, container, false)

        // 初始化 TextView
        speedTextView = view.findViewById(R.id.speedTextView)
        rpmTextView = view.findViewById(R.id.rpmTextView)
        tempTextView = view.findViewById(R.id.tempTextView)
        voltageTextView = view.findViewById(R.id.voltageTextView)
        gForceTextView = view.findViewById(R.id.gForceTextView)

        collisionDetectionModule = CollisionDetectionModule()
        connectToDevices()

        return view
    }

    private fun connectToDevices() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (!hasBluetoothPermission()) {
            requestBluetoothPermission()
            return
        }

        // 連接 OBD-II 裝置
        val obdDevice = getPairedDevice(bluetoothAdapter, "OBDII")
        obdDevice?.let { connectOBDII(it) } ?: showToast("找不到 OBD II 裝置")

        // 連接 ESP32 裝置
        val esp32Device = getPairedDevice(bluetoothAdapter, "ESP32_GForce")
        esp32Device?.let { connectESP32(it) } ?: showToast("找不到 ESP32 裝置")
    }

    private fun connectOBDII(device: BluetoothDevice) {
        obdIIManager = ObdIIManager(device)

        obdScope.launch {
            if (obdIIManager.connect()) {
                Log.d("SensorFragment", "OBD II 已連接")
                while (isActive) {
                    val data = obdIIManager.fetchOBDData()
                    updateOBDUI(data)
                    delay(300) // 控制資料更新頻率
                }
            } else {
                showToast("無法連接 OBD II 裝置")
            }
        }
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
            try {
                val buffer = ByteArray(1024)
                while (isActive) {
                    val bytes = esp32InputStream?.read(buffer) ?: 0
                    val receivedData = String(buffer, 0, bytes).trim()
                    Log.d("SensorFragment", "ESP32 Received: $receivedData")

                    val (gForce, gyroZ) = parseReceivedData(receivedData)
                    collisionDetectionModule.processSensorData(gForce, gyroZ)
                    updateGForceUI(gForce)
                }
            } catch (e: Exception) {
                Log.e("SensorFragment", "Error reading from ESP32: ${e.message}")
            }
        }
    }

    private fun parseReceivedData(data: String): Pair<Float, Float> {
        return try {
            val values = data.split(",")
            val gForce = values.getOrNull(0)?.toFloatOrNull() ?: 0f
            val gyroZ = values.getOrNull(1)?.toFloatOrNull() ?: 0f
            Pair(gForce, gyroZ)
        } catch (e: Exception) {
            Log.e("SensorFragment", "Failed to parse data: $data", e)
            Pair(0f, 0f)
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

    private fun updateGForceUI(gForce: Float) {
        requireActivity().runOnUiThread {
            gForceTextView.text = "G 力: $gForce g"
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

    private fun getPairedDevice(adapter: BluetoothAdapter, deviceName: String): BluetoothDevice? {
        return adapter.bondedDevices.firstOrNull { it.name == deviceName }
    }

    private fun showToast(message: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        obdScope.cancel()
        esp32Scope.cancel()
        esp32Socket?.close()
        esp32InputStream?.close()
    }
}
