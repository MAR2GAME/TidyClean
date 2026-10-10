package com.tidyclean.storagecleaner.utils

import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

import com.pdffox.adv.notification.NotificationManager as AdvertiseNotificationManager
import com.pdffox.adv.AdvertiseSdk
import com.pdffox.adv.notification.NotificationManager
import com.tidyclean.storagecleaner.BuildConfig
import com.tidyclean.storagecleaner.advertise.AdvertiseConfigFactory

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.apply
import kotlin.io.bufferedReader
import kotlin.io.readText
import kotlin.io.use
import kotlin.onFailure
import kotlin.onSuccess
import kotlin.runCatching
import kotlin.text.isNotBlank

object SdkInitUtils {
    private const val TAG = "SdkInit"

    private val isInitializing = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    private var initializationJob: Job? = null
    private val initializationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initSDKAsync(context: Application): Job {
        if (isInitializing.getAndSet(true)) {
            return initializationJob ?: Job().apply { complete() }
        }
        initializationJob = initializationScope.launch {
            try {
                AdvertiseSdk.init(
                    context = context,
                    isTest = BuildConfig.DEBUG,
                    sdkConfig = AdvertiseConfigFactory.create(context),
                )
                isInitialized.set(true)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Advertise SDK initialization failed; continue app startup", exception)
                isInitialized.set(false)
            } finally {
                isInitializing.set(false)
            }
        }
        return initializationJob!!
    }

    suspend fun awaitInitialization(timeoutMs: Long? = null) {
        val job = initializationJob ?: return
        if (timeoutMs == null) {
            job.join()
        } else {
            withTimeoutOrNull(timeoutMs) { job.join() }
        }
    }



    fun isInitialized(): Boolean = isInitialized.get()

    fun cleanup() {
        initializationJob?.cancel()
        initializationScope.cancel()
        initializationJob = null
        isInitialized.set(false)
        isInitializing.set(false)
    }
}
