package com.tidyclean.storagecleaner.utils

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RawRes
import kotlinx.coroutines.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.apply
import kotlin.let
import kotlin.ranges.coerceIn

class AudioVibrationPlayer(private val context: Context) {

    companion object {
        private const val DEFAULT_VIBRATION_DURATION_MS = 500L
        private const val DEFAULT_VIBRATION_INTENSITY = 255
    }

    private var mediaPlayer: MediaPlayer? = null
    private val vibrator: Vibrator? by lazy { initializeVibrator() }
    private val isPlaying = AtomicBoolean(false)
    private val shouldVibrate = AtomicBoolean(false)

    private val vibrationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var vibrationJob: Job? = null

    // 播放完成回调
    var onPlaybackComplete: (() -> Unit)? = null
    var onPlaybackError: ((Exception) -> Unit)? = null

    private fun initializeVibrator(): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 播放 raw 资源中的音频文件并持续震动
     * @param audioResourceId raw 资源ID
     * @param vibrationIntensity 震动强度 (0-255)，默认 255（最大强度）
     * @param loop 是否循环播放，默认 false
     */
    fun playAudioWithContinuousVibration(
        @RawRes audioResourceId: Int,
        vibrationIntensity: Int = DEFAULT_VIBRATION_INTENSITY,
        loop: Boolean = false
    ) {
        // 使用CAS操作确保线程安全
        if (!isPlaying.compareAndSet(false, true)) {
            return
        }

        try {
            releaseMediaPlayer() // 先释放之前的资源

            mediaPlayer = MediaPlayer.create(context, audioResourceId).apply {
                if (this == null) {
                    throw kotlin.IllegalStateException("MediaPlayer creation failed for resource: $audioResourceId")
                }

                isLooping = loop
                setOnCompletionListener {
                    handlePlaybackComplete()
                }
                setOnErrorListener { _, what, extra ->
                    handlePlaybackError(kotlin.Exception("MediaPlayer error: what=$what, extra=$extra"))
                    true // 表示错误已处理
                }

                start()
                startContinuousVibration(vibrationIntensity.coerceIn(0, 255))
            }
        } catch (e: Exception) {
            isPlaying.set(false)
            handlePlaybackError(e)
        }
    }

    /**
     * 开始持续震动
     * @param intensity 震动强度 (0-255)
     */
    private fun startContinuousVibration(intensity: Int) {
        if (vibrator?.hasVibrator() != true) return

        shouldVibrate.set(true)

        // 取消之前的震动任务
        vibrationJob?.cancel()

        vibrationJob = vibrationScope.launch {
            while (shouldVibrate.get() && isPlaying.get() && isActive) {
                try {
                    vibrateWithIntensity(intensity)
                    delay(DEFAULT_VIBRATION_DURATION_MS) // 等待震动完成
                } catch (e: CancellationException) {
                    // 协程被取消，正常退出
                    break
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onPlaybackError?.invoke(e)
                    }
                    break
                }
            }
        }
    }

    private fun vibrateWithIntensity(intensity: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(DEFAULT_VIBRATION_DURATION_MS, intensity)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(DEFAULT_VIBRATION_DURATION_MS)
            }
        } catch (e: SecurityException) {
            // 处理权限异常
            throw SecurityException("Vibration permission denied", e)
        }
    }

    /**
     * 停止震动
     */
    private fun stopVibration() {
        shouldVibrate.set(false)
        vibrationJob?.cancel()
        vibrationJob = null

        try {
            vibrator?.cancel()
        } catch (e: SecurityException) {
            // 忽略取消震动时的权限异常
        }
    }

    private fun handlePlaybackComplete() {
        stopVibration()
        isPlaying.set(false)
        onPlaybackComplete?.invoke()
    }

    private fun handlePlaybackError(exception: Exception) {
        stopVibration()
        isPlaying.set(false)
        onPlaybackError?.invoke(exception)
    }

    /**
     * 暂停播放和震动
     */
    fun pause() {
        if (isPlaying.get()) {
            mediaPlayer?.pause()
            stopVibration()
        }
    }

    /**
     * 恢复播放和震动
     * @param vibrationIntensity 震动强度，如果为null则使用之前的强度
     */
    fun resume(vibrationIntensity: Int? = null) {
        if (isPlaying.get()) {
            mediaPlayer?.start()
            if (vibrationIntensity != null) {
                startContinuousVibration(vibrationIntensity.coerceIn(0, 255))
            }
        }
    }

    /**
     * 停止播放和震动
     */
    fun stop() {
        if (isPlaying.get()) {
            mediaPlayer?.stop()
            stopVibration()
            isPlaying.set(false)
        }
    }

    /**
     * 释放所有资源
     */
    fun release() {
        stop()
        releaseMediaPlayer()
        vibrationScope.cancel()
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.let { player ->
            try {
                player.reset()
                player.release()
            } catch (e: Exception) {
                // 释放资源时的异常可以忽略
            }
        }
        mediaPlayer = null
    }

    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean = isPlaying.get()

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /**
     * 将媒体音量设置为最大
     * @param showUi 是否显示音量调整UI
     */
    fun setMediaVolumeToMax(showUi: Boolean = false) {
        try {
            val maxVolume = getMaxMediaVolume()
            val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, flags)
        } catch (e: SecurityException) {
            onPlaybackError?.invoke(SecurityException("Volume adjustment permission denied", e))
        } catch (e: Exception) {
            onPlaybackError?.invoke(e)
        }
    }

    /**
     * 获取媒体音量最大值
     */
    fun getMaxMediaVolume(): Int {
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }

    /**
     * 获取当前媒体音量
     */
    fun getCurrentMediaVolume(): Int {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    /**
     * 安全地设置媒体音量
     * @param volume 音量值 (0 - maxVolume)
     * @param showUi 是否显示音量调整UI
     */
    fun setMediaVolume(volume: Int, showUi: Boolean = false) {
        try {
            val safeVolume = volume.coerceIn(0, getMaxMediaVolume())
            val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, safeVolume, flags)
        } catch (e: SecurityException) {
            onPlaybackError?.invoke(SecurityException("Volume adjustment permission denied", e))
        } catch (e: Exception) {
            onPlaybackError?.invoke(e)
        }
    }
}