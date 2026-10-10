package com.tidyclean.storagecleaner.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.text.TextUtils
import android.util.Log
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
import com.tidyclean.storagecleaner.base.activity.BaseViewActivity
import com.tidyclean.storagecleaner.base.command.log
import com.tidyclean.storagecleaner.base.command.readyGoThenKill
import com.tidyclean.storagecleaner.bean.StorageInfo
import com.tidyclean.storagecleaner.databinding.ActivityDeviceInfoBinding
import com.tidyclean.storagecleaner.utils.CpuInfoUtils
import com.tidyclean.storagecleaner.utils.ProcCpuUsageReader
import com.tidyclean.storagecleaner.utils.SizeUtil
import com.tidyclean.storagecleaner.utils.SystemUsageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DeviceInfoActivity : BaseViewActivity<ActivityDeviceInfoBinding>() {
    private val cpuUsageReader by lazy {
        ProcCpuUsageReader(ioDispatcher = Dispatchers.IO)
    }
    override fun initializeActivity() {
        super.initializeActivity()
        binding.ivBack.setOnClickListener {   onBack() }
        loadDeviceInfo()
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
        showInterstitial(this, AdAreaKeys.Interstitial.RETURN_FROM_DEVICE_INFO_ADV){
            readyGoThenKill(MainActivity::class.java)
        }
    }

    /**
     * 加载所有设备信息：静态信息直接设置，动态信息在后台线程获取后更新UI
     */
    private fun loadDeviceInfo() {
        setStaticInfo()
        setScreenInfo()
        setStorageInfo()
        setSensorsInfo()
        setCpuInfo()
        loadDynamicInfo()
    }



    fun setCpuInfo(){

        var cpuInfo= CpuInfoUtils.getCpuInfo()

        binding.tvCpuCores.text="${cpuInfo.cores}"
        binding.tvCpuHardware.text=cpuInfo.hardware
        binding.tvCpuModel.text=cpuInfo.model
        binding.tvCpuFrequency.text="${cpuInfo.maxFrequencyMHz} MHz"

    }


    fun setSensorsInfo(){
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        binding.tvAccelerometerSensor.text = getString(
            if ( sensorManager.getDefaultSensor( Sensor.TYPE_ACCELEROMETER)!=null) R.string.supported else R.string.not_supported
        )
        binding.tvMagneticFiledSensor.text = getString(
            if ( sensorManager.getDefaultSensor( Sensor.TYPE_MAGNETIC_FIELD)!=null) R.string.supported else R.string.not_supported
        )
        binding.tvOrientationSensor.text = getString(
            if ( sensorManager.getDefaultSensor( Sensor.TYPE_ORIENTATION)!=null) R.string.supported else R.string.not_supported
        )
        binding.tvGyroscopeSensor.text = getString(
            if ( sensorManager.getDefaultSensor( Sensor.TYPE_GYROSCOPE)!=null) R.string.supported else R.string.not_supported
        )
        binding.tvLightSensor.text = getString(
            if ( sensorManager.getDefaultSensor( Sensor.TYPE_LIGHT)!=null) R.string.supported else R.string.not_supported
        )
        binding.tvLightSensor.text = getString(
            if ( sensorManager.getDefaultSensor( Sensor.TYPE_LIGHT)!=null) R.string.supported else R.string.not_supported
        )
        binding.tvDistanceSensor.text = getString(
            if ( sensorManager.getDefaultSensor( Sensor.TYPE_PROXIMITY)!=null) R.string.supported else R.string.not_supported
        )
        binding.tvTemperatureSensor.text = getString(
            if ( sensorManager.getDefaultSensor( Sensor.TYPE_AMBIENT_TEMPERATURE )!=null) R.string.supported else R.string.not_supported
        )

    }

    /**
     * 设置设备型号和系统版本等静态信息
     */
    private fun setStaticInfo() {
        binding.tvDeviceModel.text = Build.MODEL
        binding.tvSystemVersion.text = "Android ${Build.VERSION.RELEASE}"
    }

    /**
     * 设置屏幕相关信息（分辨率、密度、多点触控支持）
     */
    private fun setScreenInfo() {
        val point = getScreenResolution()
        binding.tvScreenSize.text = "${point.x}x${point.y}"
        val metrics = resources.displayMetrics
        binding.tvScreenDensity.text = " ${metrics.densityDpi} DPI"
        val hasMultitouch = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH)
        binding.tvScreenMultipletouch.text = getString(
            if (hasMultitouch) R.string.supported else R.string.not_supported
        )
    }

    /**
     * 设置存储信息（总空间、已用空间、使用率进度条）
     */
    private fun setStorageInfo() {
        val storageInfo = getStorageInfo()
        val usedPercentage = storageInfo.usagePercentage.toInt()
        val usedText = SizeUtil.formatSize(storageInfo.usedSpace)
        val totalText = SizeUtil.formatSize(storageInfo.totalSpace)
        binding.tvStorage.text = "${getString(R.string.storage_hint)}: $usedText/$totalText"
        binding.storageProgressBar.progress = usedPercentage
    }

    /**
     * 异步加载 RAM 和 CPU 使用率，避免阻塞主线程
     */
    private fun loadDynamicInfo() {
        lifecycleScope.launch {
            // 获取 RAM 使用率（IO 操作）
            val ramUsage = withContext(Dispatchers.IO) {
                SystemUsageUtils.getRamUsage(applicationContext).toInt()
            }
            binding.tvRam.text = "${getString(R.string.RAM)}: $ramUsage%"
            binding.ramProgressBar.progress = ramUsage

            // 获取 CPU 使用率（需要连续读取两次才能计算差值）
            var lastCpu: Int? = null
            while (isActive) {
                val cpu = cpuUsageReader.readUsagePercent(lastCpu) // 内部已使用 IO 调度器
                if (cpu != null) {
                    binding.cpuProgressBar.progress = cpu
                    binding.tvCpu.text = "${getString(R.string.CPU)}: $cpu%"
                    break
                }
                delay(2000)
            }
        }
    }

    /**
     * 获取屏幕分辨率（兼容不同 Android 版本）
     */
    private fun getScreenResolution(): Point {
        val point = Point()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            point.x = metrics.bounds.width()
            point.y = metrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                windowManager.defaultDisplay.getRealSize(point)
            } else {
                windowManager.defaultDisplay.getSize(point)
            }
        }
        return point
    }

    /**
     * 获取外部存储信息（总空间、已用空间、使用率）
     * 若发生异常或路径无效，返回默认空对象
     */
    @SuppressLint("DefaultLocale")
    private fun getStorageInfo(): StorageInfo {
        return try {
            val externalDir = File(
                application.getExternalFilesDir(null)?.absolutePath?.split("/Android")?.get(0) ?: ""
            )

            if (externalDir.exists()) {
                val statFs = StatFs(externalDir.absolutePath)
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

                val totalSize = totalBlocks * blockSize
                val availableSize = availableBlocks * blockSize
                val usedSize = totalSize - availableSize
                val usedPercentage = if (totalSize > 0) {
                    (usedSize * 100 / totalSize).toFloat()
                } else {
                    0f
                }

                StorageInfo(totalSize, usedSize, usedPercentage)
            } else {
                StorageInfo(0, 0, 0f)
            }
        } catch (e: Exception) {
            Log.e("DeviceInfoActivity", "Error getting storage info", e)
            StorageInfo(0, 0, 0f)
        }
    }
}