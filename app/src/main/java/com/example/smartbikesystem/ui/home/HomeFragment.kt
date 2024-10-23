package com.example.smartbikesystem.ui.home

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.smartbikesystem.databinding.FragmentHomeBinding
import com.example.smartbikesystem.service.OBDService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val httpClient: OkHttpClient by lazy { OkHttpClient() }
    private var obdResponse: String? = null

    private val obdResponseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == OBDService.ACTION_COMMAND_RESPONSE) {
                obdResponse = intent.getStringExtra(OBDService.EXTRA_RESPONSE)?.trim()
                Log.d("HomeFragment", "接收到 OBD 回應: $obdResponse")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        startOBDService()

        binding.testAccidentButton.setOnClickListener {
            val name = binding.editTextUserName.text.toString().ifEmpty { "User" }
            val ipAddress = binding.editTextServerIp.text.toString().ifEmpty { "192.168.0.100" }
            val gForce = binding.editTextGForceThreshold.text.toString().ifEmpty { "2.0" }
            showAccidentAlert(name, ipAddress, gForce.toFloat())
        }
    }

    private fun startOBDService() {
        val intent = Intent(requireContext(), OBDService::class.java)
        requireContext().startForegroundService(intent)
        Toast.makeText(requireContext(), "OBD-II Service 已啟動", Toast.LENGTH_SHORT).show()
    }

    private fun showAccidentAlert(name: String, ipAddress: String, gForce: Float) {
        AlertDialog.Builder(requireContext())
            .setTitle("事故警告")
            .setMessage("偵測到 G 力達到 $gForce G，是否需要通報伺服器？")
            .setPositiveButton("通報") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    reportAccident(name, ipAddress, gForce)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private suspend fun reportAccident(name: String, ipAddress: String, gForce: Float) {
        val location = getCurrentLocation() ?: return
        val latitude = location.latitude.toString()
        val longitude = location.longitude.toString()

        val speed = fetchSpeedWithRetry() ?: "0"  // 使用重試機制取得車速

        sendAccidentReport(name, latitude, longitude, speed, ipAddress)

        withContext(Dispatchers.Main) {
            Toast.makeText(requireContext(), "事故通報成功", Toast.LENGTH_SHORT).show()
        }
    }

    private val responseMutex = Mutex()
    private suspend fun fetchSpeedWithRetry(): String? {
        responseMutex.withLock {
            val maxRetries = 3
            var attempt = 0

            while (attempt < maxRetries) {
                obdResponse = null
                sendCommandWithDelay("010D")

                Log.d("HomeFragment", "嘗試取得車速 (第 ${attempt + 1} 次)")
                delay(300)  // 確保每次請求間隔至少 300ms

                if (obdResponse != null) {
                    val speed = extractSpeedFromResponse(obdResponse!!)
                    if (speed != null) return speed
                }
                attempt++
            }

            Log.e("HomeFragment", "無法取得車速，所有重試均失敗")
            return "0"
        }
    }

    private suspend fun sendCommandWithDelay(command: String) {
        delay(100)  // 避免 OBD 裝置的間隔限制問題
        val intent = Intent(requireContext(), OBDService::class.java).apply {
            action = OBDService.ACTION_SEND_COMMAND
            putExtra(OBDService.EXTRA_COMMAND, command)
        }
        requireContext().startService(intent)
    }

    private fun extractSpeedFromResponse(response: String): String? {
        try {
            val cleanedResponse = response.replace(">", "").trim()
            Log.d("HomeFragment", "清理後的封包回應: $cleanedResponse")

            val validResponse = cleanedResponse.substringAfter("41 0D", "").trim()
            if (validResponse.isBlank()) {
                Log.e("HomeFragment", "無法找到有效的車速資料")
                return null
            }

            val parts = validResponse.split(" ").filter { it.isNotBlank() }
            val speedHex = parts.getOrNull(0) ?: return null
            val speed = speedHex.toInt(16)

            Log.d("HomeFragment", "解析出的車速: $speed km/h")
            return speed.toString()
        } catch (e: Exception) {
            Log.e("HomeFragment", "解析車速失敗: ${e.message}")
            return null
        }
    }

    private suspend fun getCurrentLocation(): Location? {
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            Log.e("HomeFragment", "無法取得位置: ${e.message}")
            null
        }
    }

    private suspend fun sendAccidentReport(
        name: String, latitude: String, longitude: String, speed: String, ipAddress: String
    ) {
        val requestBody = FormBody.Builder()
            .add("name", name)
            .add("latitude", latitude)
            .add("longitude", longitude)
            .add("speed", speed)
            .build()

        val request = Request.Builder()
            .url("http://$ipAddress/accident")
            .post(requestBody)
            .build()

        Log.d("HomeFragment", "發送的事故報告: $name, $latitude, $longitude, 車速: $speed km/h")

        try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Log.d("HomeFragment", "事故通報成功")
            } else {
                Log.e("HomeFragment", "通報失敗: ${response.code} - ${response.message}")
            }
        } catch (e: IOException) {
            Log.e("HomeFragment", "無法通報事故: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(OBDService.ACTION_COMMAND_RESPONSE)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
            obdResponseReceiver, filter
        )
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(obdResponseReceiver)
    }
}
