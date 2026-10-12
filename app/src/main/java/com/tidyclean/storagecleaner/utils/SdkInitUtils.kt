package com.tidyclean.storagecleaner.utils

import android.app.Application
import android.util.Log
import com.mar2sdk.core.AppMod
import com.mar2sdk.core.Core
import com.tidyclean.storagecleaner.BuildConfig
import com.tidyclean.storagecleaner.advertise.AdvertiseConfigFactory
import com.mar2sdk.impl.AppOpenAdManager

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

object SdkInitUtils {
    private const val TAG = "SdkInit"

    private val isInitializing = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    private var initializationJob: Job? = null

    fun initSDKAsync(context: Application): Job {
        if (isInitialized.get()) {
            return initializationJob ?: Job().apply { complete() }
        }
        if (isInitializing.getAndSet(true)) {
            return initializationJob ?: Job().apply { complete() }
        }
        // Core.init and the app-open lifecycle callback must be registered on the
        // application's main thread. MyApp invokes this method from onCreate.
        val initialization = SupervisorJob()
        initializationJob = initialization
        try {
            Core.init(
                context,
                if (BuildConfig.DEBUG) AppMod.DEBUG else AppMod.RELEASE,
            )
            AdvertiseConfigFactory.applyAdmobIds()
            AppOpenAdManager.init(context, areaKey = "foreGroundAdv")
            isInitialized.set(true)
            initialization.complete()
        } catch (exception: Exception) {
            Log.e(TAG, "Mar2 SDK initialization failed; continue app startup", exception)
            isInitialized.set(false)
            initialization.complete()
        } finally {
            isInitializing.set(false)
        }
        return initialization
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
        initializationJob = null
        isInitialized.set(false)
        isInitializing.set(false)
    }
}
