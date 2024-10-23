package com.example.smartbikesystem.obdii

import android.bluetooth.BluetoothAdapter
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

    // **連接至 OBD-II 裝置，並支援自動重試**
    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext false

        bluetoothAdapter.cancelDiscovery()
        var attempts = 0

        while (attempts < 3) {
            try {
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(OBD_UUID)
                bluetoothSocket?.connect()

                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream

                Log.d(TAG, "OBD II 連接成功")
                sendInitializationCommands()
                return@withContext true
            } catch (e: IOException) {
                Log.e(TAG, "OBD II 連接失敗: ${e.message}, 重試中...")
                close()
                delay(300)  // 延遲 300ms 後重試
                attempts++
            }
        }
        return@withContext false
    }

    private suspend fun sendInitializationCommands() = withContext(Dispatchers.IO) {
        val commands = listOf("ATZ", "ATE0")
        for (command in commands) {
            try {
                outputStream?.write("$command\r".toByteArray())
                delay(500)
                Log.d(TAG, "發送初始化指令: $command")
            } catch (e: IOException) {
                Log.e(TAG, "發送初始化指令失敗: ${e.message}")
            }
        }
    }

    suspend fun fetchOBDData(): Map<String, Any?> = withContext(Dispatchers.IO) {
        val data = mutableMapOf<String, Any?>()
        val commands = listOf("010D", "010C", "0105", "ATRV")

        for (command in commands) {
            val response = retryCommand(command)
            if (response.isNotEmpty()) {
                Log.d(TAG, "成功接收到回應: $response")
                data.putAll(parseResponse(command, response))
            } else {
                Log.e(TAG, "指令 $command 失敗")
            }
            delay(200)
        }
        return@withContext data
    }

    private suspend fun retryCommand(command: String, maxAttempts: Int = 3): List<String> {
        for (attempt in 1..maxAttempts) {
            Log.d(TAG, "發送指令: $command (第 $attempt 次)")
            val response = sendAndLogCommand(command)
            if (response.isNotEmpty()) return response
            Log.e(TAG, "收到空回應，延遲200ms後重試")
            delay(200)
        }
        return emptyList()
    }

    private suspend fun sendAndLogCommand(command: String): List<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            outputStream?.write("$command\r".toByteArray())
            delay(500)

            val buffer = ByteArray(1024)
            val bytesRead = inputStream?.read(buffer) ?: 0
            val response = String(buffer, 0, bytesRead).trim()

            Log.d(TAG, "接收到回應: $response")
            parseMultiResponse(response)
        } catch (e: IOException) {
            Log.e(TAG, "傳送或接收資料時發生錯誤: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseMultiResponse(response: String): List<String> {
        return response.split(">").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun parseResponse(command: String, response: List<String>): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()

        try {
            when (command) {
                "010D" -> {
                    val speedHex = response[0].split(" ")[2]
                    val speed = speedHex.toInt(16)
                    result["speed"] = speed
                    Log.d(TAG, "解析車速: $speed km/h")
                }
                "010C" -> {
                    val parts = response[0].split(" ")
                    val rpm = ((parts[2].toInt(16) * 256) + parts[3].toInt(16)) / 4
                    result["rpm"] = rpm
                    Log.d(TAG, "解析轉速: $rpm RPM")
                }
                "0105" -> {
                    val tempHex = response[0].split(" ")[2]
                    val temp = tempHex.toInt(16) - 40
                    result["temperature"] = temp
                    Log.d(TAG, "解析溫度: $temp °C")
                }
                "ATRV" -> {
                    val voltageString = response[0].removePrefix("ATRV").replace("V", "").trim()
                    val voltage = voltageString.toDoubleOrNull() ?: 0.0
                    result["voltage"] = voltage
                    Log.d(TAG, "解析電壓: $voltage V")
                }
                else -> Log.e(TAG, "未知指令: $command")
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析失敗: ${e.message}", e)
        }

        return result
    }

    fun isConnected(): Boolean = bluetoothSocket?.isConnected == true

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
