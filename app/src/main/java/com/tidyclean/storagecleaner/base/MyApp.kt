package com.tidyclean.storagecleaner.base

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context

import com.tidyclean.storagecleaner.utils.MyShortcutManager
import com.tidyclean.storagecleaner.utils.SafeChecker
import com.tidyclean.storagecleaner.utils.SdkInitUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MyApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        instance = this
    }
    private var isInitialized = false
    fun isInitialized(): Boolean = isInitialized
    companion object {
        @SuppressLint("StaticFieldLeak")
        var instance: MyApp? = null
    }
    override fun onCreate() {
        super.onCreate()
        instance = this
        SdkInitUtils.initSDKAsync(this)
        initShortcutsSafe(this)
        launchSecurityChecks()
    }
    private fun launchSecurityChecks() {
        applicationScope.launch {
            delay(1000) // 延迟1秒，让应用先启动
            SafeChecker.performAuxiliaryChecks()
        }
    }

    private fun initShortcutsSafe(context: Application) {
        try {
            MyShortcutManager.initDynamicShortcuts(context)
        } catch (e: Exception) {
        }
    }
    override fun onTerminate() {
        // 清理资源
        cleanup()
        super.onTerminate()
    }
    private fun cleanup() {
        SdkInitUtils.cleanup()
        instance = null
    }

}
