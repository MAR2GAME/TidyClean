package com.tidyclean.storagecleaner.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Point
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.registerReceiver
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.tidyclean.storagecleaner.ui.MainActivity
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.advertise.AdAreaKeys
import com.tidyclean.storagecleaner.advertise.AdvertisePageMediator
import com.tidyclean.storagecleaner.advertise.AdvertisePageMediator.showInterstitial
import com.tidyclean.storagecleaner.databinding.ActivityScanPhoneBinding


import com.tidyclean.storagecleaner.base.activity.BaseViewActivity
import com.tidyclean.storagecleaner.base.command.REQUEST_CODE_MANAGE_STORAGE
import com.tidyclean.storagecleaner.base.command.REQUEST_CODE_STORAGE_PERMISSION
import com.tidyclean.storagecleaner.base.command.checkAndRequestPermissions
import com.tidyclean.storagecleaner.base.command.hasStoragePermission
import com.tidyclean.storagecleaner.base.command.log
import com.tidyclean.storagecleaner.base.command.readyGo
import com.tidyclean.storagecleaner.base.command.readyGoThenKill
import com.tidyclean.storagecleaner.base.config.AppConfig
import com.tidyclean.storagecleaner.bean.PhoneInfo
import com.tidyclean.storagecleaner.bean.StorageInfo
import com.tidyclean.storagecleaner.utils.AppPrefsUtils
import com.tidyclean.storagecleaner.utils.SizeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.jvm.java

class ScanPhoneActivity :  BaseViewActivity<ActivityScanPhoneBinding>(){
    private lateinit var appAdapter: BaseQuickAdapter<PhoneInfo,BaseViewHolder>
    private var infoList: MutableList<PhoneInfo> = mutableListOf()
    private var rotateAnimator: ObjectAnimator? = null

    override fun initializeActivity() {
        super.initializeActivity()
        log("enter_guide_page_scanning")
        startRotate()
        getScreenResolution()
        getStorageInfo()
        getBatteryHealth()
        startDotAnimation()
        initAdapter()
        binding.btClean.setOnClickListener {
            log("click_continue_guidepage")
            if (hasStoragePermission()) {
                onGetPermission()
            }else{
                AdvertisePageMediator.suppressNextForegroundOpenAd()
                checkAndRequestPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, {
                    lifecycleScope.launch {
                        delay(200)
                        readyGo(TransparentHintActivity::class.java)
                    }
                })
            }
        }
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
            }
        })

    }



    fun onGetPermission() {
        log("agree_filemanage_permission")
        AppPrefsUtils.commitBoolean(AppConfig.IS_SCAN_PHONE, true)
        readyGoThenKill(ScanJunkActivity::class.java)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onGetPermission()
        } else {
            onNotGetPermission()
        }
    }
    fun onNotGetPermission(){
        AdvertisePageMediator.showFileAccessCancelInterstitial(this){
            log("disagree_filemanage_permission")
            AppPrefsUtils.commitBoolean(AppConfig.IS_SCAN_PHONE, true)
            readyGoThenKill(MainActivity::class.java)
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                onGetPermission()
            }else{
                onNotGetPermission()
            }
        }
    }

    private fun startDotAnimation() {
        lifecycleScope.launch(Dispatchers.Main) {
            // 4 秒后自动结束循环
            withTimeoutOrNull(4000L) {
                var dotCount = 0
                while (true) {
                    binding.tvHint1.text =
                        getString(R.string.checking_device_information) + ".".repeat(dotCount)
                    dotCount = (dotCount + 1) % 4
                    delay(300)
                }
            }
            stopRotate()
            binding.tvHint1.visibility= View.GONE
            binding.tvHint2.visibility= View.GONE
            binding.rlScan.visibility= View.GONE
            binding.rlContent.visibility=View.VISIBLE
            binding.tvSkip.visibility= View.VISIBLE
            binding.tvSkip.setOnClickListener {
                log("click_skip_guidepage")
                showInterstitial(this@ScanPhoneActivity, AdAreaKeys.Interstitial.ENTER_HOME_FROM_SCAN_DEVICE_RESULT){
                    AppPrefsUtils.commitBoolean(AppConfig.IS_SCAN_PHONE, true)
                    readyGoThenKill(MainActivity::class.java)
                }
            }
        }
    }

    fun initAdapter(){
        appAdapter =object : BaseQuickAdapter<PhoneInfo,BaseViewHolder>(R.layout.item_phone_info){
            override fun convert(
                holder: BaseViewHolder,
                item: PhoneInfo
            ) {
                item.let {
                    holder.setText(R.id.tv_tag, it.tagName)
                    holder.setText(R.id.tv_info, it.info)
                }
                if(appAdapter.getItemPosition(item)==4&&!TextUtils.isEmpty(item.info)&&item.info!="--"){
                    val spannableString = SpannableString(item.info)
                    spannableString.setSpan(
                        ForegroundColorSpan(Color.parseColor("#FF5352")),
                        0, "${SizeUtil.formatSize(storageInfo!!.usedSpace)}".length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    holder.setText(R.id.tv_info, spannableString)
                }
            }
        }
        binding.rcInfo.adapter = appAdapter
        binding.rcInfo.layoutManager =
            LinearLayoutManager(this@ScanPhoneActivity, RecyclerView.VERTICAL, false)
        binding.rcInfo.setHasFixedSize(true)
        binding.tvStorage.text="${SizeUtil.formatSize(storageInfo!!.totalSpace)}"
        binding.tvPercentage.text="${storageInfo!!.usagePercentage.toInt()} %"
        infoList.add(PhoneInfo(getString(R.string.device_model),"${Build.MODEL}"))
        infoList.add(PhoneInfo(getString(R.string.system_version),"Android ${Build.VERSION.RELEASE}"))
        infoList.add(PhoneInfo(getString(R.string.screen_resolution),"${point!!.x}X${point!!.y}"))
        infoList.add(PhoneInfo(getString(R.string.battery_health_status),batteryHealth))
        infoList.add(PhoneInfo(getString(R.string.storage_usage),"${SizeUtil.formatSize(storageInfo!!.usedSpace)}/${SizeUtil.formatSize(storageInfo!!.totalSpace)}"))
        infoList.add(PhoneInfo(getString(R.string.generating_cleanup_plan),getString(R.string.to_done)))
        appAdapter.setList(infoList)
        log("enter_guide_page_scan_result")
    }

    var storageInfo: StorageInfo?=null
    @SuppressLint("DefaultLocale", "SetTextI18n")
    fun getStorageInfo() {
        try {
            val app =application
            val externalDir = File(
                app.getExternalFilesDir(null)?.absolutePath?.split("/Android")?.get(0) ?: ""
            )
            if (externalDir.exists()) {
                val statFs = StatFs(externalDir.absolutePath)

                // 获取存储块大小和数量
                val blockSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    statFs.blockSizeLong
                } else {
                    statFs.blockSize.toLong()
                }

                val totalBlocks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    statFs.blockCountLong
                } else {
                    statFs.blockCount.toLong()
                }

                val availableBlocks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    statFs.availableBlocksLong
                } else {
                    statFs.availableBlocks.toLong()
                }
                // 计算存储空间
                val totalSize = totalBlocks * blockSize
                val availableSize = availableBlocks * blockSize
                val usedSize = totalSize - availableSize
                // 计算使用百分比
                val usedPercentage = if (totalSize > 0) {
                    (usedSize * 100 / totalSize)
                } else {
                    0
                }
                storageInfo =StorageInfo(
                    totalSize,
                    usedSize,
                    usedPercentage.toFloat()
                )
            } else {
                storageInfo = StorageInfo(0, 0, 0f)
            }
        }catch (e: Exception){
            storageInfo = StorageInfo(0, 0, 0f)
        }
    }




    var point:Point?=null
    fun getScreenResolution() {
        point = Point()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            point!!.x = metrics.bounds.width()
            point!!.y = metrics.bounds.height()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(point)
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(point)
        }
    }
    var batteryHealth: String=""
    fun getBatteryHealth() {
        var batteryStatus= registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: Intent().also {
            }
        batteryHealth=when (batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_COLD -> getString(R.string.overcooling)
            BatteryManager.BATTERY_HEALTH_DEAD -> getString(R.string.damage)
            BatteryManager.BATTERY_HEALTH_GOOD -> getString(R.string.good)
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> getString(R.string.overheat)
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> getString(R.string.overvoltage)
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> getString(R.string.fault)
            else ->getString(R.string.unknown)
        }
    }


    private fun startRotate() {
        if (rotateAnimator?.isRunning == true) return
        rotateAnimator = ObjectAnimator.ofFloat(
            binding.ivScanPhone,
            View.ROTATION,
            0f, -360f          // 负角度 = 逆时针
        ).apply {
            duration = 1500L                     // 一圈 1.5 秒，按需调整
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()  // 匀速，避免一顿一顿
            start()
        }
    }

    private fun stopRotate() {
        rotateAnimator?.cancel()
        rotateAnimator = null
        // 复位，避免下次进入时角度错乱
        binding.ivScanPhone.rotation = 0f
    }

    override fun onDestroy() {
        stopRotate()
        super.onDestroy()

    }

}
