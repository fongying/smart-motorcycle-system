package com.example.smartbikesystem.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class OBDService : Service() {

    companion object {
        const val ACTION_SEND_COMMAND = "com.example.smartbikesystem.SEND_COMMAND"
        const val EXTRA_COMMAND = "com.example.smartbikesystem.EXTRA_COMMAND"
        const val ACTION_COMMAND_RESPONSE = "com.example.smartbikesystem.COMMAND_RESPONSE"
        const val EXTRA_RESPONSE = "com.example.smartbikesystem.EXTRA_RESPONSE"
        const val OBD_CONNECTION_STATUS = "OBD_CONNECTION_STATUS"
    }

    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var isObdConnected: Boolean = false  // 確保在服務中追蹤連接狀態
    private var invalidResponseCount = 0  // 無效回應計數
    private val maxInvalidResponseCount = 3  // 允許的最大無效回應次數


    private var lastCommandTime: Long = 0L  // 上次發送指令的時間戳

    private val channelId = "OBD_Service_Channel"
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceWithNotification()
        connectToOBD()
    }

    /** 建立前景服務通知 */
    private fun startForegroundServiceWithNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId,
            "OBD-II Background Service",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("OBD-II Service")
            .setContentText("正在監控 OBD-II 數據...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(1, notification)
    }

    /** 連接 OBD 裝置並進行重試 */
    private fun connectToOBD(retries: Int = 3) {
        coroutineScope.launch {
            repeat(retries) { attempt ->
                try {
                    Log.d("OBDService", "嘗試連接 OBD-II (第 ${attempt + 1} 次)")
                    val device = getBluetoothDevice()
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(
                        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                    )
                    bluetoothSocket?.connect()

                    inputStream = bluetoothSocket?.inputStream
                    outputStream = bluetoothSocket?.outputStream

                    updateConnectionState(true)
                    Log.d("OBDService", "OBD-II 連接成功")
                    return@launch  // 連接成功後退出重試循環
                } catch (e: IOException) {
                    Log.e("OBDService", "OBD-II 連接失敗 (第 ${attempt + 1} 次): ${e.message}")
                    if (attempt == retries - 1) updateConnectionState(false)  // 最後一次重試失敗
                    delay(2000)  // 等待後重試
                }
            }
        }
    }

    /** 更新 OBD 連接狀態並透過廣播傳遞 */
    private fun updateConnectionState(isConnected: Boolean) {
        isObdConnected = isConnected
        val intent = Intent(OBD_CONNECTION_STATUS).apply {
            putExtra("isConnected", isConnected)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    /** 處理收到的指令 */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.takeIf { it.action == ACTION_SEND_COMMAND }?.let {
            val command = it.getStringExtra(EXTRA_COMMAND).orEmpty()
            coroutineScope.launch { sendCommandWithDelay(command) }
        }
        return START_STICKY
    }

    private fun reconnectObd() {
        try {
            bluetoothSocket?.close()  // 確保關閉舊的 socket
        } catch (e: IOException) {
            Log.e("OBDService", "Socket 關閉失敗: ${e.message}")
        }

        connectToOBD()  // 重試連接
    }



    /** 發送指令並確保間隔時間 */
    private suspend fun sendCommandWithDelay(command: String): String {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastCommand = currentTime - lastCommandTime

        if (timeSinceLastCommand < 100) {
            delay(100 - timeSinceLastCommand)
        }

        val response = sendCommand(command)
        if (response.isBlank()) {
            Log.e("OBDService", "無法取得回應，嘗試重新連接...")
            reconnectObd()  // 嘗試重新連接
            return sendCommand(command)  // 再次發送指令
        }

        lastCommandTime = System.currentTimeMillis()
        return response
    }


    /** 發送指令並取得回應 */
    private fun sendCommand(command: String): String {
        return try {
            Log.d("OBDService", "發送指令: $command")
            outputStream?.write("$command\r".toByteArray())

            val buffer = ByteArray(1024)
            val bytesRead = inputStream?.read(buffer) ?: 0
            val response = String(buffer, 0, bytesRead).trim()

            if (response.isNotEmpty() && response.contains("41 0D")) {
                Log.d("OBDService", "接收到的有效封包: $response")
                sendResponse(response)  // 廣播回應給 HomeFragment
            } else {
                Log.e("OBDService", "無效的 OBD 回應: $response")
            }

            response
        } catch (e: IOException) {
            Log.e("OBDService", "傳送或接收指令失敗: ${e.message}")
            ""
        }
    }

    /** 發送 OBD 回應廣播 */
    private fun sendResponse(response: String) {
        val intent = Intent(ACTION_COMMAND_RESPONSE).apply {
            putExtra(EXTRA_RESPONSE, response)
        }
        Log.d("OBDService", "廣播 OBD 回應: $response")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


    /** 取得已配對的 OBD-II 裝置 */
    private fun getBluetoothDevice(): BluetoothDevice {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        return bluetoothAdapter.bondedDevices.firstOrNull { it.name == "OBDII" }
            ?: throw IllegalStateException("找不到已配對的 OBD-II 裝置")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("OBDService", "關閉藍牙連接時發生錯誤: ${e.message}")
        }
        coroutineScope.cancel()
    }


}
