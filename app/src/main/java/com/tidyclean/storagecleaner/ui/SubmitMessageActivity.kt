package com.tidyclean.storagecleaner.ui

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.advertise.AdvertisePageMediator
import com.tidyclean.storagecleaner.base.activity.BaseViewActivity
import com.tidyclean.storagecleaner.base.command.readyGoThenKill
import com.tidyclean.storagecleaner.base.command.safeClick
import com.tidyclean.storagecleaner.databinding.ActivitySubmitMessageBinding
import kotlin.jvm.java

class SubmitMessageActivity : BaseViewActivity<ActivitySubmitMessageBinding>(){

    private companion object {
        private const val NO_SELECTION = -1
        private const val SELECTION_1 = 0
        private const val SELECTION_2 = 1
        private const val SELECTION_3 = 2
    }

    private var selectedPosition = NO_SELECTION


    override fun initializeActivity() {
        super.initializeActivity()
        setupBackPressHandler()
        setupClickListeners()
    }

    private fun setupClickListeners() {
        // 选项选择监听器
        setupSelectionListeners()

        // 操作按钮监听器
        setupActionListeners()
    }

    private fun setupActionListeners() {
        binding.apply {
            // 返回操作
            ivBack.setOnClickListener { navigateBack() }
            tvCancle.setOnClickListener { navigateBack() }
            // 提交按钮
            btnSubmit.safeClick { handleSubmit() }
        }
    }

    private fun handleSubmit() {
        if (selectedPosition == NO_SELECTION) {
            // 可选：显示未选择提示
            return
        }
        // 显示插屏广告并处理后续逻辑
        // 广告展示完成后的操作
        AdvertisePageMediator.showUninstallSecondInterstitial(this) {
            readyGoThenKill(UninstallActivity::class.java)
        }
    }



    private fun setupSelectionListeners() {
        binding.apply {
            // 选项1
            tvCheck1.setOnClickListener { handleSelection(SELECTION_1) }

            // 选项2
            tvCheck2.setOnClickListener { handleSelection(SELECTION_2) }

            // 选项3
            tvCheck3.setOnClickListener { handleSelection(SELECTION_3) }
        }
    }

    private fun handleSelection(position: Int) {
        if (selectedPosition == position) return

        selectedPosition = position
        updateSelectionUI()
        enableSubmitButton()
    }

    private fun enableSubmitButton() {
        binding.apply {
            btnSubmit.apply {
                background = getDrawable(R.drawable.btn_selector)
                isClickable = true
                isEnabled = true
            }
        }
    }

    private fun disableSubmitButton() {
        binding.btnSubmit.apply {
            isClickable = false
            isEnabled = false
            // 可选：设置禁用状态的背景
            // background = getDrawable(R.drawable.btn_disabled)
        }
    }

    private fun updateSelectionUI() {
        binding.apply {
            val checkIcon = R.mipmap.ic_check
            val uncheckIcon = R.mipmap.ic_check_nor
            // 重置所有选择状态
            ivCheck1.setImageResource(uncheckIcon)
            ivCheck2.setImageResource(uncheckIcon)
            ivCheck3.setImageResource(uncheckIcon)
            // 设置选中项
            when (selectedPosition) {
                SELECTION_1 -> ivCheck1.setImageResource(checkIcon)
                SELECTION_2 -> ivCheck2.setImageResource(checkIcon)
                SELECTION_3 -> ivCheck3.setImageResource(checkIcon)
            }
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBack()
            }
        })
    }

    private fun navigateBack() {
        AdvertisePageMediator.showReturnHomeInterstitial(this) {
            readyGoThenKill(MainActivity::class.java)
        }
    }




}