package com.tidyclean.storagecleaner.viewmodle

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tidyclean.storagecleaner.base.viewmodel.BaseViewModel
import com.tidyclean.storagecleaner.bean.BatteryInfo
import com.tidyclean.storagecleaner.utils.BatteryInfoHelper


import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BatteryInfoViewModel(application: Application) : BaseViewModel(application) {

    companion object {
        private const val DEFAULT_UPDATE_INTERVAL_MS = 10000L
    }

    private val _batteryInfo = MutableStateFlow<BatteryInfo?>(null)
    val batteryInfo: StateFlow<BatteryInfo?> = _batteryInfo.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var updateJob: Job? = null
    private var isPeriodicUpdateRunning = false
    /**
     * 开始定时获取电池信息
     * @param intervalMillis 更新间隔（毫秒），默认10秒
     */
    fun startPeriodicUpdate(intervalMillis: Long = DEFAULT_UPDATE_INTERVAL_MS, batteryInfoHelper: BatteryInfoHelper) {
        if (isPeriodicUpdateRunning) return

        stopPeriodicUpdate()
        isPeriodicUpdateRunning = true
        updateJob = viewModelScope.launch {
            while (isActive) {
                fetchBatteryInfo(batteryInfoHelper)
                delay(intervalMillis)
            }
        }
    }
    /**
     * 停止定时获取电池信息
     */
    fun stopPeriodicUpdate() {
        isPeriodicUpdateRunning = false
        updateJob?.cancel()
        updateJob = null
    }
    /**
     * 手动获取一次电池信息
     */
    fun refreshBatteryInfo(batteryInfoHelper: BatteryInfoHelper) {
        viewModelScope.launch {
            fetchBatteryInfo(batteryInfoHelper)
        }
    }
    private suspend fun fetchBatteryInfo(batteryInfoHelper: BatteryInfoHelper) {
        try {
            _isLoading.value = true
            _errorMessage.value = null

            val info = batteryInfoHelper.getBatteryInfo()
            _batteryInfo.value = info

        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Unknown error occurred"
        } finally {
            _isLoading.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPeriodicUpdate()
    }
}