package com.tidyclean.storagecleaner.ui

import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.base.activity.BaseMvvmActivity
import com.tidyclean.storagecleaner.base.command.readyGoThenKill
import com.tidyclean.storagecleaner.base.config.AppConfig
import com.tidyclean.storagecleaner.databinding.ActivityPlayAudioBinding
import com.tidyclean.storagecleaner.viewmodle.PlayAudioViewModel
import kotlinx.coroutines.launch

class PlayAudioActivity : BaseMvvmActivity<ActivityPlayAudioBinding, PlayAudioViewModel>() {

    override fun init() {
        setupBackPressHandler()
        observeViewModel()
        initializePlayerAndStart()
    }

    private fun startPlayback() {
        viewModel.startPlayback(R.raw.voice_play)
        viewModel.startCountdown()
    }

    private fun initializePlayerAndStart() {
        viewModel.initializePlayer(this)
        startPlayback()
    }

    override fun onResume() {
        super.onResume()
        // 恢复播放
        viewModel.resumePlayback()
        viewModel.resumeCountdown()
        // 恢复动画
        if (!binding.ivPlay.isAnimating) {
            binding.ivPlay.resumeAnimation()
        }
    }

    override fun onPause() {
        super.onPause()
        // 暂停播放
        viewModel.pausePlayback()
        viewModel.pauseCountdown()
        // 暂停动画以节省资源
        if (binding.ivPlay.isAnimating) {
            binding.ivPlay.pauseAnimation()
        }
    }

    override fun onDestroy() {
        // 释放资源
        viewModel.releaseResources()
        cleanupAnimationResources()
        super.onDestroy()
    }

    private fun cleanupAnimationResources() {
        binding.ivPlay.apply {
            cancelAnimation()
            clearAnimation()
        }
    }


    private fun observeViewModel() {
        // 观察播放状态
        lifecycleScope.launch {
            viewModel.playbackState.observe(this@PlayAudioActivity) { state ->
                handlePlaybackState(state)
            }
        }
        lifecycleScope.launch {
            viewModel.countdownText.observe(this@PlayAudioActivity) { text ->
                if(!TextUtils.isEmpty(text)){
                    binding.tvTime.text = getString(R.string.cleaning_countdown_format, text)
                }

            }
        }

        // 观察导航事件
        lifecycleScope.launch {
            viewModel.navigationEvent.observe(this@PlayAudioActivity) { event ->
                when (event) {
                    is PlayAudioViewModel.NavigationEvent.NavigateToFinished -> {
                        navigateToFinishedActivity(event.nextTag)
                    }
                }
            }
        }

        // 观察Toast消息
        lifecycleScope.launch {
            viewModel.toastMessage.observe(this@PlayAudioActivity) { message ->
                if (message.isNotEmpty()) {
                    Toast.makeText(this@PlayAudioActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this) {
            viewModel.handleBackPressed()
        }
    }

    private fun handlePlaybackState(state: PlayAudioViewModel.PlaybackState) {
        when (state) {
            is PlayAudioViewModel.PlaybackState.PLAYING -> {
                if (!binding.ivPlay.isAnimating) {
                    binding.ivPlay.playAnimation()
                }
            }
            is PlayAudioViewModel.PlaybackState.PAUSED -> {
                if (binding.ivPlay.isAnimating) {
                    binding.ivPlay.pauseAnimation()
                }
            }
            is PlayAudioViewModel.PlaybackState.COMPLETED -> {
                // 播放完成，动画可以继续或停止
            }
            is PlayAudioViewModel.PlaybackState.ERROR -> {
                // 错误处理已在ViewModel中处理
                navigateToFinishedActivity(getString(R.string.speaker_cleaner))
            }
            else -> {
                // 其他状态不需要特殊处理
            }
        }
    }
    private fun navigateToFinishedActivity(nextTag: String) {
        val bundle = Bundle().apply {
            putString(AppConfig.NEXT_TAG, getString(R.string.speaker_cleaner))
        }
        readyGoThenKill(CompleteActivity::class.java, bundle)
    }
}