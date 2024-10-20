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

    // **連接至 OBD-II 裝置**
    fun connect(): Boolean {
        try {
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(OBD_UUID)
            bluetoothSocket?.connect()
            inputStream = bluetoothSocket?.inputStream
            outputStream = bluetoothSocket?.outputStream
            Log.d(TAG, "OBD II 連接成功")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "OBD II 連接失敗: ${e.message}", e)
            close()
            return false
        }
    }

    // **獲取 OBD-II 數據**
    suspend fun fetchOBDData(): Map<String, Any?> = withContext(Dispatchers.IO) {
        val data = mutableMapOf<String, Any?>()
        val commands = listOf("010D", "010C", "0105", "ATRV")  // 每個指令間隔200ms

        for (command in commands) {
            val response = sendAndLogCommand(command)
            if (response.isNotEmpty()) {
                data.putAll(parseResponse(command, response))
            } else {
                Log.e(TAG, "收到空回應，延遲後重新發送指令: $command")
                delay(200)  // 增加延遲避免過快發送
                sendAndLogCommand(command)
            }
//            delay(200)  // 每個指令間增加200ms延遲
        }
        data
    }

    // **發送指令並記錄回應**
    suspend fun sendAndLogCommand(command: String): List<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "發送指令: $command")
            outputStream?.write("$command\r".toByteArray())

            // 增加延遲以避免指令過於頻繁
            delay(500)

            val buffer = ByteArray(1024)
            val bytesRead = inputStream?.read(buffer) ?: 0
            val response = String(buffer, 0, bytesRead).trim()

            Log.d(TAG, "接收到回應: $response")
            return@withContext parseMultiResponse(response)
        } catch (e: IOException) {
            Log.e(TAG, "傳送或接收資料時發生錯誤: ${e.message}", e)
            return@withContext emptyList()
        }
    }


    // **解析多條回應**
    private fun parseMultiResponse(response: String): List<String> {
        val responses = response.split(">").map { it.trim() }.filter { it.isNotEmpty() }
        Log.d(TAG, "拆分後的回應: $responses")
        return responses.flatMap { cleanResponse(it) }
    }

    // **清理回應中的雜訊**
    private fun cleanResponse(response: String): List<String> {
        return response.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    }

    // **解析指令回應**
    private fun parseResponse(command: String, response: List<String>): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()

        if (response.isEmpty() || response.contains("STOPPED")) {
            Log.e(TAG, "無效或停止的回應: $response")
            return result
        }

        try {
            when (command) {
                "010D" -> {
                    val speedHex = response.getOrNull(3) ?: "00"
                    val speed = speedHex.toIntOrNull(16) ?: 0
                    result["speed"] = speed
                    Log.d(TAG, "解析車速: $speed km/h")
                }
                "010C" -> {
                    val highByte = response.getOrNull(3)?.toIntOrNull(16) ?: 0
                    val lowByte = response.getOrNull(4)?.toIntOrNull(16) ?: 0
                    val rpm = ((highByte * 256) + lowByte) / 4
                    result["rpm"] = rpm
                    Log.d(TAG, "解析轉速: $rpm RPM (高位: $highByte, 低位: $lowByte)")
                }
                "0105" -> {
                    val tempHex = response.getOrNull(3) ?: "00"
                    val tempDec = tempHex.toIntOrNull(16) ?: 0 // 將Hex轉為十進位
                    val temp = tempDec - 40 // 減上40度的校正
                    result["temperature"] = temp
                    Log.d(TAG, "解析溫度: $temp °C")
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
            Log.e(TAG, "解析失敗: ${e.message}", e)
        }

        return result
    }

    // **檢查是否已連接**
    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected ?: false
    }

    // **關閉連接**
    fun close() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
            Log.d(TAG, "連線已關閉")
        } catch (e: IOException) {
            Log.e(TAG, "關閉連線時發生錯誤: ${e.message}", e)
        }
    }
}
