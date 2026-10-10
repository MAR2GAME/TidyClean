package com.tidyclean.storagecleaner.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.base.activity.BaseMvvmActivity
import com.tidyclean.storagecleaner.base.command.readyGoThenKill
import com.tidyclean.storagecleaner.base.config.AppConfig
import com.tidyclean.storagecleaner.bean.LargeFileEvent
import com.tidyclean.storagecleaner.databinding.ActivityLoadingBinding
import com.tidyclean.storagecleaner.viewmodle.LoadingViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import kotlin.invoke
import kotlin.jvm.java

class LoadingActivity : BaseMvvmActivity<ActivityLoadingBinding, LoadingViewModel>() {
    companion object {
        private const val PROGRESS_TOTAL = 100
        private const val DOT_ANIMATION_MAX = 4
        private const val DOT_ANIMATION_DELAY_MS = 300L
    }
    private var currentTag: String? = null
    private var hintAnimationJob: Job? = null

    private var rotateAnimator: ObjectAnimator? = null

    private var startTime: Long = 0
    private fun createFileManagerConfig(hintRes: Int) = LoadingConfig(
        animationRes = R.mipmap.ic_load_file,
        hintTextRes = hintRes,
        progressTime=40L,
        setupAction = { viewModel.startScanFiles(contentResolver, currentTag) }
    )
    private data class LoadingConfig(
        val animationRes: Int,
        val hintTextRes: Int,
        val progressTime: Long,
        val targetActivity: Class<*>? = null,
        val setupAction: (() -> Unit)? = null
    )
    private val tagConfigMap by lazy {
        mapOf(R.string.large_file_cleaner to createFileManagerConfig(R.string.scanning_large_file),
            R.string.images to createFileManagerConfig(R.string.scanning_images),
            R.string.video to createFileManagerConfig(R.string.scanning_videos),
            R.string.audio to createFileManagerConfig(R.string.scanning_audio_files),
            R.string.documents to createFileManagerConfig(R.string.scanning_documents),
            R.string.battery_info to LoadingConfig(
                animationRes = R.mipmap.ic_load_bt,
                hintTextRes = R.string.scanning_for_battery_info,
                progressTime=30L,
                targetActivity = BatteryInfoActivity::class.java
            ),
            R.string.device_info to LoadingConfig(
                animationRes = R.mipmap.ic_load_dv,
                hintTextRes = R.string.scanning_for_device_info,
                progressTime=30L,
                targetActivity = DeviceInfoActivity::class.java
            )
        )
    }

    override fun init() {
        currentTag = intent.extras?.getString(AppConfig.NEXT_TAG)
        startTime = System.currentTimeMillis()
        initViewModel()
        setupBackPressHandler()
        handleTagNavigation()
    }

    private fun handleTagNavigation() {
        val config = tagConfigMap.entries.firstOrNull { getString(it.key) == currentTag }?.value
        if (config == null) {
            finish()
            return
        }
        setupUI(config)
        config.setupAction?.invoke()
    }

    private fun startRotate() {
        if (rotateAnimator?.isRunning == true) return
        rotateAnimator = ObjectAnimator.ofFloat(
            binding.ivScan,
            View.ROTATION,
            0f, -360f          // 负角度 = 逆时针
        ).apply {
            duration = 1200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()  // 匀速，避免一顿一顿
            start()
        }
    }

    private fun stopRotate() {
        rotateAnimator?.cancel()
        rotateAnimator = null
    }

    private fun setupUI(config:LoadingConfig) {
        binding.ivBg.setImageResource(config.animationRes)
        startRotate()
        startHintDotsAnimation(getString(config.hintTextRes))
        startCounterAnimation(config)
    }


    @SuppressLint("SetTextI18n")
    private fun startCounterAnimation(config: LoadingConfig) {
        lifecycleScope.launch {
            for (i in 0..PROGRESS_TOTAL) {
                delay(config.progressTime)
                binding.tvProgress.text = "$i%"
            }
            config.targetActivity?.let {
                navigateWithDelay(null, it)
            }
        }
    }


    private fun <T> navigateWithDelay(event: T, targetActivity: Class<*>) {
        lifecycleScope.launch {
            val elapsedTime = System.currentTimeMillis() - startTime
            val remainingDelay = maxOf(0, 3000 - elapsedTime)
            if (remainingDelay > 0) {
                delay(remainingDelay)
            }
            if (event != null) {
                EventBus.getDefault().postSticky(event)
            }
            readyGoThenKill(targetActivity, intent.extras?.let { Bundle(it) })
        }
    }



    @SuppressLint("SetTextI18n")
    private fun startHintDotsAnimation(hintText: String) {
        hintAnimationJob?.cancel()
        val baseHintText = hintText.trimEnd('.')
        if (baseHintText == hintText) {
            binding.tvHint.text = hintText
            return
        }
        hintAnimationJob = lifecycleScope.launch {
            var dotCount = 1
            while (true) {
                binding.tvHint.text = baseHintText + ".".repeat(dotCount)
                delay(DOT_ANIMATION_DELAY_MS)
                dotCount = if (dotCount == DOT_ANIMATION_MAX) 1 else dotCount + 1
            }
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    this@LoadingActivity,
                    getString(R.string.please_wait_with_status, binding.tvHint.text),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun initViewModel() {
        with(viewModel) {
            largeFilesData.observe(this@LoadingActivity) { result ->
                navigateWithDelay(LargeFileEvent(result.files, result.totalSize), LargeFileManagerActivity::class.java)
            }
        }
    }

    override fun onDestroy() {
        stopRotate()
        hintAnimationJob?.cancel()
        hintAnimationJob=null
        super.onDestroy()
    }


}