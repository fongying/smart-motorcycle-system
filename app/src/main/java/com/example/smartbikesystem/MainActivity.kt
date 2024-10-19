package com.example.smartbikesystem

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.smartbikesystem.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
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

    private fun showError(message: String) {
        // 在這裡你可以顯示錯誤對話框或記錄日誌
        // 這樣可以避免應用在找不到 Fragment 時崩潰
        println(message)  // 或者使用 Log.e("MainActivity", message)
    }
}
