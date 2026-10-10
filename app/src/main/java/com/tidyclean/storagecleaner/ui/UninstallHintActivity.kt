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
import com.tidyclean.storagecleaner.base.command.getFunctionBundle
import com.tidyclean.storagecleaner.base.command.hasStoragePermission
import com.tidyclean.storagecleaner.base.command.readyGoThenKill
import com.tidyclean.storagecleaner.base.command.safeClick
import com.tidyclean.storagecleaner.databinding.ActivityUninstallHintBinding
import kotlin.jvm.java

class UninstallHintActivity : BaseViewActivity<ActivityUninstallHintBinding>() {




    override fun initializeActivity() {
        super.initializeActivity()
        setupBackPressHandler()
        setupClickListeners()

    }

    private fun setupClickListeners() {
        with(binding) {
            ivBack.setOnClickListener { handleBackAction() }
            rlClean.safeClick { handleCleanClick() }
            rlDiscover.safeClick { handleBackAction() }
            btnCancle.safeClick { handleBackAction() }
            tvUninstall.safeClick { handleUninstallClick() }
        }
    }


    private fun handleUninstallClick() {
        AdvertisePageMediator.showUninstallFirstInterstitial(this) {
            readyGoThenKill(SubmitMessageActivity::class.java)
        }
    }



    private fun handleCleanClick() {
        if (hasStoragePermission()) {
            AdvertisePageMediator.showFeatureEntryInterstitial(this, getString(R.string.junk_files)) {
                readyGoThenKill(ScanJunkActivity::class.java,getFunctionBundle("junkClean","homepage"))
            }
        } else {
            handleBackAction()
        }
    }




    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackAction()
            }
        })
    }

    private fun handleBackAction() {
        AdvertisePageMediator.showReturnHomeInterstitial(this) {
            readyGoThenKill(MainActivity::class.java)
        }
    }



}