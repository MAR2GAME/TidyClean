package com.tidyclean.storagecleaner.viewmodle

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.base.viewmodel.BaseViewModel
import com.tidyclean.storagecleaner.utils.AudioVibrationPlayer

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlayAudioViewModel(application: Application) : BaseViewModel(application) {

    private val _playbackState = MutableLiveData<PlaybackState>()
    val playbackState: LiveData<PlaybackState> get() = _playbackState

    private val _navigationEvent = MutableLiveData<NavigationEvent>()
    val navigationEvent: LiveData<NavigationEvent> get() = _navigationEvent

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> get() = _toastMessage

    private lateinit var audioVibrationPlayer: AudioVibrationPlayer

    // 播放状态
    private var isFinishing = false


    private val _countdownText = MutableLiveData<String>()
    val countdownText: LiveData<String> = _countdownText

    private val _isCountdownRunning = MutableLiveData<Boolean>()
    val isCountdownRunning: LiveData<Boolean> = _isCountdownRunning

    private val _isCountdownPaused = MutableLiveData<Boolean>()
    val isCountdownPaused: LiveData<Boolean> = _isCountdownPaused



    private val _remainingSeconds = MutableLiveData<Int>()
    val remainingSeconds: LiveData<Int> = _remainingSeconds

    private val _progress = MutableLiveData<Int>()
    val progress: LiveData<Int> = _progress

    // 协程相关




    private var countdownJob: Job? = null
    private var totalSeconds = 15

    fun startCountdown() {
        // 如果正在运行，先停止
        if (_isCountdownRunning.value == true) {
            stopCountdown()
        }

        // 重置状态
        _isCountdownRunning.value = true
        _isCountdownPaused.value = false
        countdownJob = viewModelScope.launch {
            for (currentSecond in (_remainingSeconds.value ?: totalSeconds) downTo 0) {
                // 检查协程是否仍然活跃且没有被暂停
                if (!isActive || _isCountdownPaused.value == true) break

                // 更新当前秒数
                _remainingSeconds.postValue(currentSecond)
                _countdownText.postValue("$currentSecond")
                // 如果不是最后一秒，等待1秒
                if (currentSecond > 0) {
                    delay(1000)
                }
            }
            // 循环结束后的处理
            if (_isCountdownPaused.value != true) {
                // 倒计时正常结束
                _isCountdownRunning.postValue(false)
                _countdownText.postValue(" ")
                handlePlaybackComplete()
            }
        }
    }

    /**
     * 暂停倒计时
     */
    fun pauseCountdown() {
        if (_isCountdownRunning.value == true) {
            _isCountdownPaused.value = true
            _isCountdownRunning.value = false
            countdownJob?.cancel()
            countdownJob = null
        }
    }

    /**
     * 恢复倒计时
     */
    fun resumeCountdown() {
        if (_isCountdownPaused.value == true) {
            _isCountdownPaused.value = false
            startCountdown()
        }
    }


    override fun onCleared() {
        super.onCleared()
        stopCountdown()
    }

    /**
     * 停止倒计时（完全停止）
     */
    fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        _isCountdownRunning.value = false
        _isCountdownPaused.value = false
    }

    fun initializePlayer(context: Context) {
        audioVibrationPlayer = AudioVibrationPlayer(context).apply {
            onPlaybackError = { exception ->
                handlePlaybackError(exception)
            }
            onPlaybackComplete = {
                // handlePlaybackComplete()
            }
        }
        audioVibrationPlayer.setMediaVolumeToMax()
        _playbackState.value = PlaybackState.INITIALIZED
    }

    fun startPlayback(audioResourceId: Int) {
        _playbackState.value = PlaybackState.PLAYING
        try {
            audioVibrationPlayer.playAudioWithContinuousVibration(audioResourceId)
        } catch (e: Exception) {
            handlePlaybackError(e)
        }
    }

    fun pausePlayback() {
        if (::audioVibrationPlayer.isInitialized) {
            audioVibrationPlayer.pause()
            _playbackState.value = PlaybackState.PAUSED
        }
    }



    fun resumePlayback() {
        if (::audioVibrationPlayer.isInitialized) {
            audioVibrationPlayer.resume()
            _playbackState.value = PlaybackState.PLAYING
        }
    }

    fun handleBackPressed() {
        if (::audioVibrationPlayer.isInitialized && audioVibrationPlayer.isPlaying()) {
            _toastMessage.value = application.getString(R.string.speaker_back_hint)
        }
    }

    fun proceedToNextScreen(nextTag: String) {
        if (isFinishing) return
        isFinishing = true
        _navigationEvent.value = NavigationEvent.NavigateToFinished(nextTag)
    }

    private fun handlePlaybackComplete() {
        if (!isFinishing) {
            _playbackState.value = PlaybackState.COMPLETED
            proceedToNextScreen(application.getString(R.string.speaker_cleaner))
        }
    }

    private fun handlePlaybackError(exception: Exception?) {
        _playbackState.value = PlaybackState.ERROR
        // 延迟跳转以确保用户体验
    }

    fun releaseResources() {
        isFinishing = true
        if (::audioVibrationPlayer.isInitialized) {
            audioVibrationPlayer.release()
        }
        _playbackState.value = PlaybackState.RELEASED
    }

    fun isPlayerInitialized(): Boolean = ::audioVibrationPlayer.isInitialized

    fun isPlaying(): Boolean {
        return if (::audioVibrationPlayer.isInitialized) {
            audioVibrationPlayer.isPlaying()
        } else {
            false
        }
    }

    sealed class PlaybackState {
        object INITIALIZED : PlaybackState()
        object PLAYING : PlaybackState()
        object PAUSED : PlaybackState()
        object COMPLETED : PlaybackState()
        object ERROR : PlaybackState()
        object RELEASED : PlaybackState()
    }

    sealed class NavigationEvent {
        data class NavigateToFinished(val nextTag: String) : NavigationEvent()
    }
}