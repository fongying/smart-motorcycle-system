package com.example.smartbikesystem

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.smartbikesystem.databinding.ActivityMainBinding
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        // 初始化 SharedPreferences 並應用儲存的語言
        sharedPreferences = getSharedPreferences("settings", Context.MODE_PRIVATE)
        applySavedLanguage()

        super.onCreate(savedInstanceState)

        // 強制亮色模式
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // 使用 ViewBinding 綁定佈局
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 設置 NavController 與 BottomNavigationView 的關聯
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as? NavHostFragment

        if (navHostFragment != null) {
            val navController = navHostFragment.navController
            binding.navView.setupWithNavController(navController)
        } else {
            // 如果 NavHostFragment 找不到，顯示錯誤或記錄日誌
            showError("NavHostFragment not found.")
        }
    }

    // 應用已儲存的語言設定
    private fun applySavedLanguage() {
        val savedLanguage = sharedPreferences.getString("language", Locale.getDefault().language)
        val locale = Locale(savedLanguage!!)
        val config = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            config.setLocales(android.os.LocaleList(locale))
        } else {
            config.setLocale(locale)
        }

        resources.updateConfiguration(config, resources.displayMetrics)
        baseContext.resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun showError(message: String) {
        // 在這裡你可以顯示錯誤對話框或記錄日誌
        println(message)  // 或者使用 Log.e("MainActivity", message)
    }
}
