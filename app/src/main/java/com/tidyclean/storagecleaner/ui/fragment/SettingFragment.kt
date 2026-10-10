package com.tidyclean.storagecleaner.ui.fragment

import android.content.Intent
import android.os.Bundle
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.base.command.rateUS
import com.tidyclean.storagecleaner.base.command.readyGo
import com.tidyclean.storagecleaner.base.command.safeClick
import com.tidyclean.storagecleaner.base.config.AppConfig
import com.tidyclean.storagecleaner.base.fragment.BaseMvvmFragment
import com.tidyclean.storagecleaner.databinding.FragmentSettingsBinding
import com.tidyclean.storagecleaner.ui.WebViewActivity
import com.tidyclean.storagecleaner.viewmodle.SettingFragmentViewModel

class SettingFragment : BaseMvvmFragment<FragmentSettingsBinding, SettingFragmentViewModel>(){
    override fun getViewModelClass(): Class<SettingFragmentViewModel> = SettingFragmentViewModel::class.java

    override fun init() {
        binding.tvShare.safeClick {
            shareApp()
        }
        binding.tvRateUs.safeClick {
            requireActivity().rateUS()
        }

        binding.tvTerms.safeClick {
            toWebView(AppConfig.TERMS_URL)
        }
        binding.tvPrivacyPolicy.safeClick {
            toWebView(AppConfig.PRIVACY_URL)
        }

    }

    fun toWebView(url:String){
        val bundle = Bundle().apply {
            putString(WebViewActivity.EXTRA_URL, url)
        }
        requireActivity().readyGo(WebViewActivity::class.java, bundle)
    }

    fun shareApp() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"

            // 应用名称
            val appName = getString(R.string.app_name)

            // 应用商店链接（替换为你的应用实际链接）
            val appLink = "https://play.google.com/store/apps/details?id=${requireActivity().packageName}"
            // 分享文本
            val shareText = "Check out $appName at: $appLink"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, appName)
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText)
            startActivity(Intent.createChooser(shareIntent, "Share $appName"))
        } catch (e: Exception) {
            e.printStackTrace()
            // 处理异常，例如显示错误消息
        }
    }
}