package com.example.smartbikesystem.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.io.InputStream

class GForceService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var inputStream: InputStream? = null  // 設為 nullable，以避免未初始化錯誤

    var gForceThreshold: Float = 2.0f  // 閾值，可根據需求調整
    private var onGForceUpdate: ((Float) -> Unit)? = null  // 提供回調以更新 UI

    override fun onCreate() {
        super.onCreate()
        Log.d("GForceService", "服務啟動")
    }

    fun setInputStream(stream: InputStream) {
        inputStream = stream
        startMonitoringGForce()  // 開始監控 G-Sensor
    }

    private fun startMonitoringGForce() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val gForce = readGForceData()
                    Log.d("GForceService", "G 力數值：$gForce")

                    onGForceUpdate?.invoke(gForce)  // 將 G 力數據傳回 UI

                    if (gForce >= gForceThreshold) {
                        triggerAccidentAlert(gForce)  // 若超過閾值則觸發警報
                    }
                } catch (e: Exception) {
                    Log.e("GForceService", "無法讀取 G 力數據：${e.message}")
                }
                delay(300)  // 每 300ms 更新一次
            }
        }
    }

    private suspend fun readGForceData(): Float {
        val buffer = ByteArray(1024)
        val bytes = inputStream?.read(buffer) ?: 0  // 若未初始化則返回 0
        val data = String(buffer, 0, bytes).trim()
        return data.toFloatOrNull() ?: 0f  // 解析數據
    }

    private fun triggerAccidentAlert(gForce: Float) {
        Log.d("GForceService", "G 力超過閾值，觸發事故警報：$gForce g")
        // 可在這裡發送通知或進一步的處理邏輯
    }

    fun setOnGForceUpdateListener(listener: (Float) -> Unit) {
        onGForceUpdate = listener  // 設置回調函數以更新 UI
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()  // 停止所有協程
        Log.d("GForceService", "服務已停止")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
