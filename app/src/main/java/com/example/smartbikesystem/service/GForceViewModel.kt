package com.example.smartbikesystem.service

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import java.io.InputStream

class GForceViewModel : ViewModel() {

    // LiveData for G-force
    private val _gForce = MutableLiveData<Float>()
    val gForce: LiveData<Float> get() = _gForce

    // LiveData for G-force threshold
    private val _gForceThreshold = MutableLiveData<Float>()
    val gForceThreshold: LiveData<Float> get() = _gForceThreshold

    // LiveData for user and location data
    private val _userName = MutableLiveData<String>()
    val userName: LiveData<String> get() = _userName

    private val _ipAddress = MutableLiveData<String>()
    val ipAddress: LiveData<String> get() = _ipAddress

    private val _latitude = MutableLiveData<String>()
    val latitude: LiveData<String> get() = _latitude

    private val _longitude = MutableLiveData<String>()
    val longitude: LiveData<String> get() = _longitude

    // LiveData for speed and OBD data
    private val _speed = MutableLiveData<String>()
    val speed: LiveData<String> get() = _speed

    private val _obdData = MutableLiveData<Map<String, Any?>>()
    val obdData: LiveData<Map<String, Any?>> get() = _obdData

    // ESP32 InputStream and Coroutine Scope
    private var esp32InputStream: InputStream? = null
    private val viewModelScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isMonitoring = false

    // 更新使用者和位置資料
    fun setUserData(name: String, ip: String, lat: String, lon: String) {
        _userName.postValue(name)
        _ipAddress.postValue(ip)
        _latitude.postValue(lat)
        _longitude.postValue(lon)
    }

    fun processGForce(value: Float) {
        updateGForce(value)
    }


    // 更新車速資料
    fun setSpeed(currentSpeed: String) {
        _speed.postValue(currentSpeed)
    }

    // 更新 OBD 資料
    fun updateOBDData(data: Map<String, Any?>) {
        _obdData.postValue(data)
    }

    // 設定 ESP32 的 InputStream
    fun setESP32InputStream(inputStream: InputStream) {
        esp32InputStream = inputStream
    }

    // 設定 G-force 閾值
    fun setThreshold(value: Float) {
        _gForceThreshold.postValue(value)
    }

    // 開始監控 G-force 資料
    fun startMonitoringGForce() {
        if (isMonitoring) return  // 避免重複啟動
        isMonitoring = true

        viewModelScope.launch {
            while (isMonitoring) {
                try {
                    val gForceValue = fetchGForceFromESP32()  // 從 ESP32 取得數據
                    updateGForce(gForceValue)  // 更新 G 力數據
                } catch (e: Exception) {
                    Log.e("GForceViewModel", "獲取 G 力數據失敗：${e.message}")
                }
                delay(300)  // 每 300ms 讀取一次數據
            }
        }
    }

    // 停止監控 G-force
    fun stopMonitoring() {
        isMonitoring = false
    }

    // 從 ESP32 讀取 G-force 數據
    private suspend fun fetchGForceFromESP32(): Float {
        return withContext(Dispatchers.IO) {
            try {
                val buffer = ByteArray(1024)
                val bytesRead = esp32InputStream?.read(buffer) ?: 0
                val receivedData = String(buffer, 0, bytesRead).trim()

                Log.d("GForceViewModel", "ESP32 Received: $receivedData")

                // 檢查是否為空資料，若為空則忽略
                if (receivedData.isEmpty()) {
                    Log.w("GForceViewModel", "收到空資料，忽略")
                    return@withContext _gForce.value ?: 0f  // 保留上一次的數據
                }

                val parts = receivedData.split(",")
                parts[0].toFloatOrNull() ?: 0f  // 使用第一部分作為 G 力數值
            } catch (e: Exception) {
                Log.e("GForceViewModel", "ESP32 數據讀取失敗：${e.message}")
                0f  // 若發生錯誤回傳 0
            }
        }
    }



    // 更新 G 力數據並檢查是否超過閾值
    private fun updateGForce(value: Float) {
        _gForce.postValue(value)
        checkThreshold(value)
    }

    // 檢查 G 力是否超過閾值
    private fun checkThreshold(gForceValue: Float) {
        val threshold = _gForceThreshold.value ?: 2.0f  // 預設為 2.0 G
        if (gForceValue >= threshold) {
            Log.d("GForceViewModel", "G 力超過閾值！觸發警報：$gForceValue G")
            triggerAccidentAlert(gForceValue)
        } else {
            Log.d("GForceViewModel", "G 力未超過閾值：$gForceValue G")
        }
    }

    // 觸發事故警報
    private fun triggerAccidentAlert(gForceValue: Float) {
        Log.d("GForceViewModel", "警報已觸發，G 力：$gForceValue G")
        // 在這裡實作警報邏輯，如發送通知或啟動事故通報流程
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()  // 清理協程
    }
}
