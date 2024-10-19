package com.example.smartbikesystem.obdii

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class ObdIIManager(private val bluetoothDevice: BluetoothDevice) {

    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    companion object {
        private val OBD_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val TAG = "ObdIIManager"
    }

    fun connect() {
        try {
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(OBD_UUID)
            bluetoothSocket?.connect()
            inputStream = bluetoothSocket?.inputStream
            outputStream = bluetoothSocket?.outputStream
            Log.d(TAG, "OBD II 連接成功")
        } catch (e: IOException) {
            Log.e(TAG, "OBD II 連接失敗", e)
            close()
        }
    }

    suspend fun fetchOBDData(): Map<String, Any?> = withContext(Dispatchers.IO) {
        val data = mutableMapOf<String, Any?>()
        val commands = listOf("010D", "010C", "0105", "ATRV")

        for (command in commands) {
            val response = sendAndLogCommand(command)
            val parsedData = parseResponse(command, response)
            data.putAll(parsedData)
        }
        data
    }

    suspend fun sendAndLogCommand(command: String): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "發送指令: $command")
            outputStream?.write((command + "\r").toByteArray())

            // 等待 OBD 裝置回應，避免立即發送下一個指令
            delay(200)  // 可根據需要調整延遲時間

            val buffer = ByteArray(1024)
            val bytesRead = inputStream?.read(buffer) ?: 0
            val response = String(buffer, 0, bytesRead).trim()

            Log.d(TAG, "接收到回應: $response")

            if (response.isBlank() || response.contains("STOPPED")) {
                Log.e(TAG, "收到無效回應: $response")
                return@withContext emptyList()
            }

            val cleanedResponse = cleanResponse(response)
            Log.d(TAG, "清理後的回應: $cleanedResponse")
            cleanedResponse
        } catch (e: IOException) {
            Log.e(TAG, "傳送或接收資料時發生錯誤", e)
            emptyList()
        }
    }



    private fun cleanResponse(response: String): List<String> {
        Log.d(TAG, "正在清理回應: $response")
        return response.split(">").map { it.trim() }
            .filter { it.isNotBlank() }
            .flatMap { it.split("\\s+".toRegex()) }
    }

    fun parseResponse(command: String, response: List<String>): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()

        if (response.contains("STOPPED") || response.isEmpty()) {
            Log.e(TAG, "無效回應或設備停止: $response")
            return result
        }

        try {
            when (command) {
                "010D" -> {
                    val speedHex = response.getOrNull(3) ?: "00"
                    val speed = speedHex.toIntOrNull(16) ?: 0
                    result["speed"] = speed
                    Log.d(TAG, "解析車速: $speed km/h (Hex: $speedHex)")
                }
                "010C" -> {
                    val highByte = response.getOrNull(3)?.toIntOrNull(16) ?: 0
                    val lowByte = response.getOrNull(4)?.toIntOrNull(16) ?: 0
                    val rpm = ((highByte * 256) + lowByte) / 4
                    result["rpm"] = rpm
                    Log.d(TAG, "解析轉速: $rpm RPM (高位: $highByte, 低位: $lowByte)")
                }
                "0105" -> {
                    val tempHex = response.getOrNull(3) ?: "00" // 確保我們取得正確的Hex值
                    val tempDec = tempHex.toIntOrNull(16) ?: 0 // 將Hex轉為十進位
                    val temp = tempDec - 40 // 減上40度的校正
                    result["temperature"] = temp
                    Log.d(TAG, "解析溫度: $temp°C (Hex: $tempHex)")
                }
                "ATRV" -> {
                    val voltageString = response.getOrNull(1)?.replace("V", "")?.trim()
                    val voltage = voltageString?.toDoubleOrNull() ?: 0.0
                    result["voltage"] = voltage
                    Log.d(TAG, "解析電壓: $voltage V")

                }
                else -> {
                    Log.e(TAG, "未知指令: $command")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析失敗", e)
        }

        return result
    }



    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected ?: false
    }

    fun close() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
            Log.d(TAG, "連線已關閉")
        } catch (e: IOException) {
            Log.e(TAG, "關閉連線時發生錯誤", e)
        }
    }
}
