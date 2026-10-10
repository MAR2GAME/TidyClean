package com.tidyclean.storagecleaner.ui

import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.lifecycleScope
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.advertise.AdAreaKeys
import com.tidyclean.storagecleaner.advertise.AdvertisePageMediator.showInterstitial
import com.tidyclean.storagecleaner.base.activity.BaseMvvmActivity
import com.tidyclean.storagecleaner.base.command.log
import com.tidyclean.storagecleaner.base.command.readyGoThenKill
import com.tidyclean.storagecleaner.base.command.safeClick
import com.tidyclean.storagecleaner.bean.BatteryInfo
import com.tidyclean.storagecleaner.databinding.ActivityBatteryInfoBinding
import com.tidyclean.storagecleaner.utils.BatteryInfoHelper
import com.tidyclean.storagecleaner.viewmodle.BatteryInfoViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.collections.get

class BatteryInfoActivity : BaseMvvmActivity<ActivityBatteryInfoBinding, BatteryInfoViewModel>() {

    companion object {
        private const val UPDATE_INTERVAL_MS = 120000L
    }

    private lateinit var batteryInfoHelper: BatteryInfoHelper


    override fun init() {
        batteryInfoHelper = BatteryInfoHelper(this@BatteryInfoActivity)
        setupViews()
        observeData()
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
        val details=intent.extras?.getString("details")
        val referrer_name=intent.extras?.getString("referrer_name")
        if(!TextUtils.isEmpty( details)){
            log("enter_corefeatures_page",mapOf("details" to details!! ,"referrer_name" to referrer_name!!))
        }



    }

    fun onBack(){
        showInterstitial(this, AdAreaKeys.Interstitial.RETURN_FROM_BATTERY_INFO_ADV){
            readyGoThenKill(MainActivity::class.java)
        }
    }




    override fun onPause() {
        super.onPause()
        viewModel.stopPeriodicUpdate()
    }

    override fun onResume() {
        super.onResume()
        viewModel.startPeriodicUpdate(UPDATE_INTERVAL_MS, batteryInfoHelper)
    }
    private fun setupViews() {
        binding.ivBack.safeClick { onBack() }
    }
    private fun observeData() {
        lifecycleScope.launch {
            viewModel.batteryInfo.collectLatest { batteryInfo ->
                batteryInfo?.let { updateBatteryInfoUI(it) }
            }
        }
        lifecycleScope.launch {
            viewModel.errorMessage.collectLatest { errorMessage ->
                errorMessage?.let {
                    Toast.makeText(
                        this@BatteryInfoActivity,
                        getString(R.string.failed_to_obtain_battery_information),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    private fun updateBatteryInfoUI(batteryInfo: BatteryInfo) {
        val temperatureFormat = NumberFormat.getNumberInstance(
            resources.configuration.locales[0]
        ).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        with(binding) {
            tvTemperature.text = "${temperatureFormat.format(batteryInfo.temperature)}\u2103"
            tvVoltage.text = "${batteryInfo.voltage}mV"
            tvHealth.text = batteryInfo.health
            tvBatteryTechnology.text = batteryInfo.technology
            tvLife.text=batteryInfo.availableTime
            tvCurrentCapacity.text="${(batteryInfo.capacity * (batteryInfo.level / 100.0)).toInt()} MAh"
            tvTotalCapacity.text="${batteryInfo.capacity} MAh"
            tvPercentageStatus.text="${batteryInfo.level}%"
            tvBatteryStatus.text=batteryInfo.status
            tvChargingStatus.text=batteryInfo.plugged
        }
    }

}