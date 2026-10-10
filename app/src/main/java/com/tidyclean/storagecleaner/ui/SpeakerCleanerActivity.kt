package com.tidyclean.storagecleaner.ui

import android.os.Bundle
import android.text.TextUtils
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.advertise.AdAreaKeys
import com.tidyclean.storagecleaner.advertise.AdvertisePageMediator.showInterstitial
import com.tidyclean.storagecleaner.base.activity.BaseViewActivity
import com.tidyclean.storagecleaner.base.command.log
import com.tidyclean.storagecleaner.base.command.readyGoThenKill
import com.tidyclean.storagecleaner.base.command.safeClick
import com.tidyclean.storagecleaner.databinding.ActivitySpeakerCleanerBinding
import kotlin.jvm.java

class SpeakerCleanerActivity : BaseViewActivity<ActivitySpeakerCleanerBinding>() {

    override fun initializeActivity() {
        super.initializeActivity()
        setupClickListeners()
        val details=intent.extras?.getString("details")
        val referrer_name=intent.extras?.getString("referrer_name")
        if(!TextUtils.isEmpty( details)){
            log("enter_corefeatures_page",mapOf("details" to details!! ,"referrer_name" to referrer_name!!))
        }

    }
    private fun setupClickListeners() {
        binding.btCleanDust.safeClick {
            readyGoThenKill(PlayAudioActivity::class.java)
        }

        binding.ivBack.safeClick {
            onBack()
        }
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    fun onBack(){
        showInterstitial(this, AdAreaKeys.Interstitial.RETURN_FROM_SPEAKER_ADV){
            readyGoThenKill(MainActivity::class.java)
        }
    }


}