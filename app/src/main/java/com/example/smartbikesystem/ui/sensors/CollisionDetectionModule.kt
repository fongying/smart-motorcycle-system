package com.example.smartbikesystem.ui.sensors

import android.util.Log
import kotlinx.coroutines.*

class CollisionDetectionModule {

    private var collisionThreshold = 3.0f  // 預設碰撞的 G 力門檻
    private val reverseDetectionThreshold = -20.0f  // Z 軸的倒車門檻
    private val timeWindow = 10 * 1000L  // 車禍後10秒內的時間窗 (毫秒)

    private var lastCollisionTime: Long = 0
    private var isReversing = false

    private var collisionListener: ((Float) -> Unit)? = null  // 支援將 G 力數值傳遞給 listener

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 設置碰撞的 G 力門檻
    fun setCollisionThreshold(threshold: Float) {
        collisionThreshold = threshold
    }

    // 設置碰撞事件的監聽器
    fun setOnCollisionListener(listener: (Float) -> Unit) {
        collisionListener = listener
    }

    // 開始監控感測器資料
    fun startMonitoring(dataProvider: suspend () -> SensorData?) {
        coroutineScope.launch {
            while (isActive) {
                try {
                    val sensorData = withTimeoutOrNull(1000) { dataProvider() }
                    sensorData?.let {
                        processSensorData(it.gForce, it.gyroZ)
                    }
                } catch (e: Exception) {
                    Log.e("CollisionDetection", "數據接收錯誤: ${e.message}")
                }
                delay(500)
            }
        }
    }

    // 處理感測器資料
    fun processSensorData(gForce: Float, gyroZ: Float) {
        Log.d("CollisionDetection", "G-Force: $gForce, GyroZ: $gyroZ")

        if (gForce > collisionThreshold) {
            onCollisionDetected(gForce)
        }

        if (gyroZ < reverseDetectionThreshold) {
            onReverseDetected()
        }
    }

    // 碰撞事件處理
    private fun onCollisionDetected(gForce: Float) {
        lastCollisionTime = System.currentTimeMillis()
        Log.d("CollisionDetection", "偵測到碰撞！G 力：$gForce")
        collisionListener?.invoke(gForce)  // 呼叫監聽器
        resetReversingState()  // 重置倒車狀態
    }

    // 倒車事件處理
    private fun onReverseDetected() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCollisionTime <= timeWindow) {
            if (!isReversing) {
                isReversing = true
                Log.d("CollisionDetection", "碰撞後倒車偵測，發生車禍！")
            }
        } else {
            Log.d("CollisionDetection", "倒車偵測中，但未偵測到碰撞。")
        }
    }

    // 重置倒車狀態
    private fun resetReversingState() {
        isReversing = false
    }

    // 清除資源
    fun clear() {
        coroutineScope.cancel()
    }

    // 資料類別
    data class SensorData(val gForce: Float, val gyroZ: Float)
}
