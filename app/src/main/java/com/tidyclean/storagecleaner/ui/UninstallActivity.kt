package com.tidyclean.storagecleaner.ui

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.advertise.AdvertisePageMediator
import com.tidyclean.storagecleaner.base.activity.BaseViewActivity
import com.tidyclean.storagecleaner.base.command.openAppSettings
import com.tidyclean.storagecleaner.base.command.readyGoThenKill
import com.tidyclean.storagecleaner.base.command.safeClick
import com.tidyclean.storagecleaner.databinding.ActivityUninstallBinding
import com.tidyclean.storagecleaner.databinding.ActivityUninstallHintBinding

class UninstallActivity : BaseViewActivity<ActivityUninstallBinding>() {

    private lateinit var backPressCallback: OnBackPressedCallback

    override fun onDestroy() {
        super.onDestroy()
        // 可选：移除回调防止内存泄漏
        backPressCallback.remove()
    }

    private fun setupBackPressHandler() {
        backPressCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBack()
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressCallback)
    }

    private fun navigateBack() {
        AdvertisePageMediator.showReturnHomeInterstitial(this) {
            readyGoThenKill(MainActivity::class.java)
        }
    }


    private fun setupClickListeners() {
        binding.apply {
            // 返回操作（两种方式）
            ivBack.setOnClickListener { navigateBack() }
            btnCancle.setOnClickListener { navigateBack() }
            // 卸载操作
            tvUninstall.safeClick { openAppSettings(this@UninstallActivity, packageName) }
        }
    }




    override fun initializeActivity() {
        super.initializeActivity()
        setupBackPressHandler()
        setupClickListeners()
    }

}