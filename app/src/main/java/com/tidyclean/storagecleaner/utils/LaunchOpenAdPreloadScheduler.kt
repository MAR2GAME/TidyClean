package com.tidyclean.storagecleaner.utils

internal class LaunchOpenAdPreloadScheduler(
    private val retryIntervalMs: Long,
) {
    private var lastAttemptAtMs: Long? = null

    var attemptCount: Int = 0
        private set

    fun tryAcquire(nowMs: Long, hasCachedAd: Boolean, isLoading: Boolean): Boolean {
        if (hasCachedAd || isLoading) return false
        val lastAttempt = lastAttemptAtMs
        if (lastAttempt != null && nowMs - lastAttempt < retryIntervalMs) return false
        lastAttemptAtMs = nowMs
        attemptCount++
        return true
    }
}
