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
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class OBDService : Service() {

    companion object {
        const val ACTION_SEND_COMMAND = "com.example.smartbikesystem.SEND_COMMAND"
        const val EXTRA_COMMAND = "com.example.smartbikesystem.EXTRA_COMMAND"
        const val ACTION_COMMAND_RESPONSE = "com.example.smartbikesystem.COMMAND_RESPONSE"
        const val EXTRA_RESPONSE = "com.example.smartbikesystem.EXTRA_RESPONSE"
    }

    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var bluetoothSocket: BluetoothSocket? = null

    private val channelId = "OBD_Service_Channel"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceWithNotification()
        connectToOBD()
    }

    private fun startForegroundServiceWithNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId,
            "OBD-II Background Service",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("OBD-II Service")
            .setContentText("正在監控 OBD-II 數據...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(1, notification)
    }

    private fun connectToOBD() {
        coroutineScope.launch {
            try {
                val device = getBluetoothDevice()
                bluetoothSocket = device.createRfcommSocketToServiceRecord(
                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                )
                bluetoothSocket?.connect()

                inputStream = bluetoothSocket?.inputStream
                outputStream = bluetoothSocket?.outputStream

                Log.d("OBDService", "OBD-II 連接成功")
            } catch (e: Exception) {
                Log.e("OBDService", "無法連接 OBD-II 裝置: ${e.message}")
                stopSelf()
            }
        }
    }

    private fun getBluetoothDevice(): BluetoothDevice {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        return bluetoothAdapter.bondedDevices.firstOrNull { it.name == "OBDII" }
            ?: throw IllegalStateException("找不到已配對的 OBD-II 裝置")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent?.getStringExtra(EXTRA_COMMAND) ?: ""
        val response = sendCommand(command)
        sendResponse(response)
        return START_NOT_STICKY
    }

    private var lastCommandTime = 0L  // 上次發送指令的時間戳

    private suspend fun sendCommandWithDelay(command: String): String {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastCommand = currentTime - lastCommandTime

        // 若上次發送後間隔不足 100ms，則延遲到足夠的間隔
        if (timeSinceLastCommand < 100) {
            val delayTime = 100 - timeSinceLastCommand
            Log.d("OBDService", "等待 $delayTime ms 再發送指令")
            delay(delayTime)  // 使用 Coroutine 延遲
        }

        return sendCommand(command).also {
            lastCommandTime = System.currentTimeMillis()  // 更新指令發送時間
        }
    }

    private fun sendCommand(command: String): String {
        return try {
            Log.d("OBDService", "發送指令: $command")
            outputStream?.write("$command\r".toByteArray())

            val buffer = ByteArray(1024)
            val bytesRead = inputStream?.read(buffer) ?: 0
            val response = String(buffer, 0, bytesRead).trim()

            Log.d("OBDService", "接收到的原始封包: $response")

            if (!response.startsWith("41 0D")) {
                Log.w("OBDService", "回應無效或間隔太短，收到 STOPPED，將調整間隔")
            }

            return response
        } catch (e: IOException) {
            Log.e("OBDService", "傳送指令失敗: ${e.message}")
            ""
        }
    }





    private fun sendResponse(response: String) {
        val intent = Intent(ACTION_COMMAND_RESPONSE).apply {
            putExtra(EXTRA_RESPONSE, response)
        }
        Log.d("OBDService", "正在發送廣播，內容: $response")
        val broadcastSuccess = LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        if (broadcastSuccess) {
            Log.d("OBDService", "廣播成功發送: $response")
        } else {
            Log.e("OBDService", "廣播發送失敗")
        }
    }


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e("OBDService", "關閉藍牙連接時發生錯誤: ${e.message}")
        }
    }
}
