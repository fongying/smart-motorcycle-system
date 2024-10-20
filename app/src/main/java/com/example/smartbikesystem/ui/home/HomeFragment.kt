package com.example.smartbikesystem.ui.home

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.smartbikesystem.databinding.FragmentHomeBinding
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var binding: FragmentHomeBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化 SharedPreferences
        sharedPreferences = requireContext().getSharedPreferences("settings", Context.MODE_PRIVATE)

        // 設定按鈕的初始語言顯示
        updateLanguageToggleButton()

        // 設定按鈕的點擊事件
        binding.languageToggleButton.setOnClickListener {
            toggleLanguage()
        }
    }

    private fun restartApp() {
        val intent = requireActivity().intent
        requireActivity().finish()
        startActivity(intent)
    }

    private fun toggleLanguage() {
        val currentLanguage = sharedPreferences.getString("language", Locale.getDefault().language)
        val newLanguage = if (currentLanguage == "en") "zh" else "en"
        sharedPreferences.edit().putString("language", newLanguage).apply()

        // 更新語言並重新啟動 MainActivity
        setLocale(Locale(newLanguage))
        restartApp()
    }

    private fun setLocale(locale: Locale) {
        val config = Configuration(resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            config.setLocales(android.os.LocaleList(locale))
        } else {
            config.setLocale(locale)
        }
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun updateLanguageToggleButton() {
        val currentLanguage = sharedPreferences.getString("language", Locale.getDefault().language)
        binding.languageToggleButton.text = if (currentLanguage == "en") "中文" else "English"
    }
}
