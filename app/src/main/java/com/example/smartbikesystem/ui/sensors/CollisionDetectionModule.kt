package com.example.smartbikesystem.ui.sensors

import android.util.Log
import kotlinx.coroutines.*
import java.util.*

class CollisionDetectionModule {

    private val collisionThreshold = 3.0  // 碰撞的 G 力門檻
    private val reverseDetectionThreshold = -20.0  // Z 軸的倒車門檻
    private val timeWindow = 10 * 1000L  // 車禍後10秒內的時間窗 (毫秒)

    private var lastCollisionTime: Long = 0  // 上次碰撞時間
    private var isReversing = false  // 是否正在倒車

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
                delay(500)  // 每0.5秒檢查一次
            }
        }
    }

    fun processSensorData(gForce: Float, gyroZ: Float) {
        Log.d("CollisionDetection", "G-Force: $gForce, GyroZ: $gyroZ")

        if (gForce > collisionThreshold) {
            onCollisionDetected()
        }

        if (gyroZ < reverseDetectionThreshold) {
            onReverseDetected()
        }
    }

    private fun onCollisionDetected() {
        lastCollisionTime = System.currentTimeMillis()
        Log.d("CollisionDetection", "碰撞偵測到！")
        // 這裡執行通知或警報邏輯
    }

    private fun onReverseDetected() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCollisionTime <= timeWindow) {
            if (!isReversing) {
                isReversing = true  // 避免重複觸發
                Log.d("CollisionDetection", "碰撞後倒車偵測，發生車禍！")
                // 執行警報或通知邏輯
            }
        } else {
            Log.d("CollisionDetection", "倒車偵測中，但未偵測到碰撞。")
        }
    }

    fun clear() {
        coroutineScope.cancel()
    }

    data class SensorData(val gForce: Float, val gyroZ: Float)
}
