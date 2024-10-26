package com.example.smartbikesystem.ui.home

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
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
import androidx.fragment.app.activityViewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import com.example.smartbikesystem.R
import com.example.smartbikesystem.databinding.FragmentHomeBinding
import com.example.smartbikesystem.obdii.ObdIIManager
import com.example.smartbikesystem.service.GForceViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private val gForceViewModel: GForceViewModel by activityViewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var esp32Socket: BluetoothSocket? = null
    private var esp32InputStream: InputStream? = null

    private val obdConnectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "OBD_CONNECTION_STATUS") {
                val isObdConnected = intent.getBooleanExtra("isConnected", false)
                Log.d("HomeFragment", "OBD 連接狀態更新為: $isObdConnected")
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
        connectESP32()
        gForceViewModel.startMonitoringGForce()

        binding.saveSettingsButton.setOnClickListener {
            val name = binding.editTextName.text.toString()
            val ipAddress = binding.editTextIpAddress.text.toString()
            val gForceThreshold = binding.editTextGForceThreshold.text.toString().toFloatOrNull() ?: 2.0f

            CoroutineScope(Dispatchers.IO).launch {
                val location = getCurrentLocation()
                val latitude = location?.latitude?.toString() ?: "0.0"
                val longitude = location?.longitude?.toString() ?: "0.0"

                withContext(Dispatchers.Main) {
                    openSensorFragment(name, ipAddress, latitude, longitude, gForceThreshold)
                }
            }
        }
    }

    private fun connectESP32() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                val device = bluetoothAdapter.bondedDevices.firstOrNull { it.name == "ESP32" }
                    ?: throw IOException("找不到已配對的 ESP32 裝置")

                esp32Socket = device.createRfcommSocketToServiceRecord(
                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                )
                esp32Socket?.connect()
                esp32InputStream = esp32Socket?.inputStream

                gForceViewModel.setESP32InputStream(esp32InputStream!!)
                Log.d("HomeFragment", "ESP32 已成功連接")
            } catch (e: Exception) {
                Log.e("HomeFragment", "ESP32 連接失敗：${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(requireContext()).apply {
            registerReceiver(obdConnectionReceiver, IntentFilter("OBD_CONNECTION_STATUS"))
        }
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(obdConnectionReceiver)
    }

    private fun openSensorFragment(name: String, ipAddress: String, latitude: String, longitude: String, gForceThreshold: Float) {
        val bundle = Bundle().apply {
            putString("name", name)
            putString("ipAddress", ipAddress)
            putString("latitude", latitude)
            putString("longitude", longitude)
            putFloat("gForceThreshold", gForceThreshold)
        }

        findNavController().navigate(R.id.action_homeFragment_to_sensorFragment, bundle)
    }

    private suspend fun getCurrentLocation(): Location? {
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            Log.e("HomeFragment", "無法獲取位置: ${e.message}")
            null
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
