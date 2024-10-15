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

    fun connect(retries: Int = 3, delayMillis: Long = 2000) {
        var attempt = 0
        while (attempt < retries) {
            try {
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(OBD_UUID)
                bluetoothSocket?.connect()
                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream
                Log.d(TAG, "OBD II 連接成功")
                return
            } catch (e: IOException) {
                Log.e(TAG, "OBD II 連接失敗, 嘗試次數: ${attempt + 1}", e)
                close()
                attempt++
                Thread.sleep(delayMillis) // 延遲後再次嘗試
            }
        }
        Log.e(TAG, "OBD II 連接失敗，超出重試次數")
    }
    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected ?: false
    }

    fun disconnect() {
        try {
            bluetoothSocket?.close()
            Log.d(TAG, "OBD II 已中斷連接")
        } catch (e: IOException) {
            Log.e(TAG, "中斷連接失敗", e)
        }
    }

    fun getDeviceName(): String {
        return bluetoothDevice.name ?: "Unknown Device"
    }

    fun sendCommandWithRetry(command: String, retries: Int = 3): String {
        var attempts = 0
        var response: String
        do {
            response = sendCommand(command)
            attempts++
        } while (response.isEmpty() && attempts < retries)
        return response
    }

    fun sendCommand(command: String): String {
        try {
            outputStream?.write((command + "\r").toByteArray())
            Thread.sleep(200) // 減少等待時間，必要時可以調整
            val buffer = ByteArray(1024)
            val bytesRead = inputStream?.read(buffer) ?: 0
            val response = String(buffer, 0, bytesRead).trim()
            Log.d(TAG, "OBD II 回應: $response")
            return response
        } catch (e: IOException) {
            Log.e(TAG, "指令傳送失敗", e)
            return ""
        }
    }

    // Coroutine 版本的非阻塞指令發送
    suspend fun sendCommandAsync(command: String): String {
        return withContext(Dispatchers.IO) {
            try {
                outputStream?.write((command + "\r").toByteArray())
                delay(200) // 使用 Coroutine 的 delay 非阻塞等待
                val buffer = ByteArray(1024)
                val bytesRead = inputStream?.read(buffer) ?: 0
                val response = String(buffer, 0, bytesRead).trim()
                Log.d(TAG, "OBD II 回應: $response")
                response
            } catch (e: IOException) {
                Log.e(TAG, "指令傳送失敗", e)
                ""
            }
        }
    }

    // 車速解析
    fun getVehicleSpeed(): String {
        return sendCommand("010D")
    }

    fun parseVehicleSpeed(response: String): Int {
        return parseOBDResponse(response, "41 0D")
    }

    // 引擎轉速解析
    fun getEngineRPM(): String {
        return sendCommand("010C")
    }

    fun parseEngineRPM(response: String): Int {
        return parseOBDResponse(response, "41 0C")
    }

    // 引擎冷卻液溫度解析
    fun getEngineTemp(): String {
        return sendCommand("0105")
    }

    fun parseEngineTemp(response: String): Int {
        return parseOBDResponse(response, "41 05")
    }

    // 電瓶電壓解析
    fun getBatteryVoltage(): String {
        return sendCommand("ATRV") // OBD 電瓶電壓指令
    }

    fun parseBatteryVoltage(response: String): Double {
        return try {
            val regex = Regex("ATRV(\\d+\\.\\d+)V")
            val matchResult = regex.find(response)
            matchResult?.groupValues?.get(1)?.toDoubleOrNull()?.also {
                Log.d(TAG, "解析到的電壓: $it V")
            } ?: run {
                Log.e(TAG, "無效的電壓回應: $response")
                -1.0
            }
        } catch (e: Exception) {
            Log.e(TAG, "電壓解析失敗: $response", e)
            -1.0
        }
    }

    // 通用的 OBD 解析邏輯，適合車速、引擎轉速和溫度
    private fun parseOBDResponse(response: String, expectedPrefix: String): Int {
        val cleanResponse = response.replace(">", "").trim()
        val bytes = cleanResponse.split(" ").filter { it.isNotBlank() }

        // 確保解析時不會有位移錯誤，避免 IndexOutOfBoundsException
        val index = bytes.indexOfFirst { it == expectedPrefix.split(" ")[1] }
        return if (index != -1 && bytes.size > index + 2) {
            try {
                val hexValue = bytes[index + 2]
                Log.d(TAG, "解析到的數值: $hexValue")
                Integer.parseInt(hexValue, 16)
            } catch (e: NumberFormatException) {
                Log.e(TAG, "數值解析錯誤: $response", e)
                -1
            }
        } else {
            Log.e(TAG, "無效的回應: $response")
            -1
        }
    }

    fun close() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
            Log.d(TAG, "OBD II 連線關閉")
        } catch (e: IOException) {
            Log.e(TAG, "OBD II 連線關閉失敗", e)
        }
    }
}
