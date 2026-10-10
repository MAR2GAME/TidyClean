package com.tidyclean.storagecleaner.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pdffox.adv.AdvertiseSdk
import com.pdffox.adv.log.LogUtil
import com.pdffox.adv.notification.CommonService.Companion.intent


import com.tidyclean.storagecleaner.ui.MainActivity
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.advertise.AdvertisePageMediator
import com.tidyclean.storagecleaner.advertise.AdvertiseUserAttributes
import com.tidyclean.storagecleaner.base.activity.BaseMvvmActivity
import com.tidyclean.storagecleaner.base.activity.BaseViewActivity
import com.tidyclean.storagecleaner.base.command.getDetailCurrentTime
import com.tidyclean.storagecleaner.base.command.getFunctionBundle
import com.tidyclean.storagecleaner.base.command.hasStoragePermission
import com.tidyclean.storagecleaner.base.command.isNetworkAvailable
import com.tidyclean.storagecleaner.base.command.readyGoThenKill
import com.tidyclean.storagecleaner.base.config.AppConfig
import com.tidyclean.storagecleaner.base.config.AppConfig.PARAM_SCENE
import com.tidyclean.storagecleaner.base.config.AppConfig.PRIVACY_URL
import com.tidyclean.storagecleaner.base.config.AppConfig.TERMS_URL
import com.tidyclean.storagecleaner.databinding.ActivitySplashBinding
import com.tidyclean.storagecleaner.utils.AppPrefsUtils
import com.tidyclean.storagecleaner.utils.LaunchOpenAdPreloadScheduler
import com.tidyclean.storagecleaner.utils.SdkInitUtils
import com.tidyclean.storagecleaner.viewmodle.SplashViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.jvm.java

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseViewActivity<ActivitySplashBinding>() {
    companion object {
        private const val PROGRESS_UPDATE_INTERVAL = 16L
        private const val PROGRESS_SCALE = 100
        private const val TOTAL_PROGRESS = 100 * PROGRESS_SCALE
        private const val PREPARATION_PROGRESS_MAX = 99 * PROGRESS_SCALE
        private const val VISUAL_PROGRESS_TARGET = 90 * PROGRESS_SCALE
        private const val FINAL_PROGRESS_DURATION_MS = 500L
        private const val PROGRESS_ANIMATION_FRAME_MS = 16L
        private const val CONSENT_PREPARATION_TIMEOUT_MS = 30_000L
        private const val SPLASH_CONSENT_FORM_TIMEOUT_MS = 30_000L
        private const val STARTUP_PROGRESS_DURATION_MS = 4_000L
        private const val WAITING_PROGRESS_DURATION_MS = 8_000L
        private const val OPEN_AD_LOAD_WAIT_TIMEOUT_MS = 10_000L
        private const val OPEN_AD_OFFLINE_LOAD_WAIT_TIMEOUT_MS = 2_500L
        private const val OPEN_AD_FOREGROUND_WAIT_TIMEOUT_MS = 6_000L
        private const val OPEN_AD_SHOW_TIMEOUT_MS = 30_000L
        private const val OPEN_AD_POLL_INTERVAL_MS = 100L
        private const val OPEN_AD_PRELOAD_RETRY_INTERVAL_MS = 500L
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
        private const val APP_SHORTCUTS_REFERRER = "app_shortcuts"
        private const val OPEN_REFERRER = "open"

        const val APP_OPEN_FROM_SHORTCUT = "shortcut"
    }

    private var progressJob: Job? = null
    private var startupJob: Job? = null
    private var preloadOpenAdJob: Job? = null
    private var currentProgress = 0
    private var progressStartTime = 0L
    private var hasStartedConsentInit = false
    private var hasContinuedToNextStep = false
    private var hasReturnedFromBackgroundDuringSplash = false
    private var foregroundOpenAdDisabledForSplash = false
    private var isShowingLaunchOpenAd = false
    private var isWaitingForNotificationPermission = false
    private var isShowingSplashConsent = false

    private var scene: String = ""
    private var startTime: Long = 0L



    private val notificationPermissionFinished = CompletableDeferred<Unit>()
    private val consentInitFinished = CompletableDeferred<Boolean>()
    private val launchOpenAdPreloadScheduler =
        LaunchOpenAdPreloadScheduler(OPEN_AD_PRELOAD_RETRY_INTERVAL_MS)

    private var startElapsedRealtime: Long = 0L
    override fun initializeActivity() {
        super.initializeActivity()
        startElapsedRealtime = SystemClock.elapsedRealtime()
        setupBackPressHandler()
        initFirstLaunchTracking()
        suppressForegroundOpenAdDuringSplash()
        setupClickableText()
        startStartupFlow()
        requestPostNotificationsPermissionIfNeeded()

    }


    private fun startStartupFlow() {
        if (startupJob?.isActive == true) return
        beginEarlyConsentInitialization()
        tryBeginLaunchOpenAdPreload()
        startupJob = lifecycleScope.launch {
            try {
                notificationPermissionFinished.await()
                startProgressLoop()
                handleLaunchOpenAdStage()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.log("splash_sequence_error", mapOf("error" to (e.message ?: "")))
                completeProgressQuickly()
                continueToNextStep()
            }
        }
    }

    private fun continueToNextStep() {
        if (hasContinuedToNextStep || isFinishing || isDestroyed) return
        hasContinuedToNextStep = true
        restoreForegroundOpenAdIfNeeded()
        determineNavigationPath()
    }

    private fun determineNavigationPath() {
        logNotificationAppClick(intent)
        val notificationRoute = intent.getStringExtra("Route").orEmpty()
        if (notificationRoute.isNotBlank() && navigateFromNotificationRoute(notificationRoute)) {
            AdvertiseSdk.setSuperProperties(mapOf("traffic_source" to "app_push"))
            scene = intent.extras?.getString("id").orEmpty()
            LogUtil.log("notification_app_clicked", mapOf("scene" to scene))
            return
        }
        val appOpenFrom = intent.getStringExtra("AppOpenFrom")
        if (appOpenFrom == "shortcut") {
            navigateFromShortcut()
        } else {
            navigateFromNormalLaunch()
        }

    }

    private fun navigateFromShortcut() {
        val nextAction = intent.extras?.getString(AppConfig.NEXT_TAG).orEmpty()
        scene = intent.extras?.getString("id").orEmpty()
        AdvertiseSdk.setSuperProperties(mapOf("traffic_source" to "app_shortcuts"))
        LogUtil.log("notification_app_clicked", mapOf("scene" to scene))
        when (nextAction) {
            getString(R.string.remove_junk) -> navigateToJunkFiles()
            getString(R.string.app_usage) -> readyGoThenKill(AppUsageActivity::class.java,getFunctionBundle("appUsage","notification"))
            getString(R.string.battery_info) -> navigateToLoadingActivity(notificationBundle(getString(R.string.battery_info),"batteryInfo"))
            getString(R.string.uninstall) ->   readyGoThenKill(UninstallHintActivity::class.java)
            else -> readyGoThenKill(MainActivity::class.java)
        }
    }

    private fun navigateFromNormalLaunch() {
        AdvertiseSdk.setSuperProperties(mapOf("traffic_source" to null))
        val destination = if (AppPrefsUtils.getBoolean(AppConfig.IS_SCAN_PHONE)) {
            MainActivity::class.java
        } else {
            ScanPhoneActivity::class.java
        }
        readyGoThenKill(destination)
    }

    private fun navigateFromNotificationRoute(route: String): Boolean {
        when (normalizeRoute(route)) {
            "home", "main" ->   readyGoThenKill(MainActivity::class.java)
            "junkClean", "junkclean", "scan" -> navigateToJunkFiles()
            "batteryInfo", "batteryinfo" -> navigateToLoadingActivity(notificationBundle(getString(R.string.battery_info),"batteryInfo"))
            "speaker_cleaner", "speakercleaner" ->  readyGoThenKill(SpeakerCleanerActivity::class.java,getFunctionBundle("speakerCleaner","notification"))
            "managelargefiles","filemanager"-> navigateToStorageLoading(notificationBundle(getString(R.string.large_file_cleaner),"fileManage"))
            "managephotos"-> navigateToStorageLoading(notificationBundle(getString(R.string.images),"fileManage"))
            "managevideos"-> navigateToStorageLoading(notificationBundle(getString(R.string.video),"fileManage"))
            "appusage" -> readyGoThenKill(AppUsageActivity::class.java,getFunctionBundle("appUsage","notification"))
            "deviceinfo"->navigateToLoadingActivity(notificationBundle(getString(R.string.device_info),"deviceInfo"))
            else -> return false
        }
        return true
    }



    private fun navigateToStorageLoading(bundle: Bundle? = intent.extras) {
        if (hasStoragePermission()) {
            readyGoThenKill(LoadingActivity::class.java, bundle)
        } else {
            readyGoThenKill(MainActivity::class.java, bundle)
        }
    }


    private fun navigateToLoadingActivity(bundle: Bundle? = intent.extras) {
        readyGoThenKill(LoadingActivity::class.java, bundle)
    }

    private fun notificationBundle(nextTag: String? = null,details:String): Bundle{
        var bundle= Bundle()
        bundle.putString(AppConfig.NEXT_TAG,nextTag)
        bundle.putString("details",details)
        bundle.putString("referrer_name","notification")
        return bundle
    }

    private fun navigateToJunkFiles() {
        if (hasStoragePermission()) {
            readyGoThenKill(ScanJunkActivity::class.java,getFunctionBundle("junkClean","notification"))
        } else {
            readyGoThenKill(MainActivity::class.java)
        }
    }
    private fun normalizeRoute(route: String): String {
        return route.trim()
            .removePrefix("/")
            .replace("-", "_")
            .lowercase()
    }
    fun logNotificationAppClick(intent: Intent?) {
        if (!isNotificationOpen(intent)) return
        val scene = intent?.getStringExtra("Scene")
            ?: intent?.getStringExtra("scene")
            ?: intent?.getStringExtra("Route")
            ?: intent?.getStringExtra("AppOpenFrom")
        val params = scene
            ?.takeIf { it.isNotBlank() }
            ?.let { mapOf(PARAM_SCENE to it) }
            ?: emptyMap()
        LogUtil.log("notification_app_clicked", params)
    }

    private fun isNotificationOpen(intent: Intent?): Boolean {
        return when (intent?.getStringExtra("AppOpenFrom")) {
            "persistent", "app_push", "Push", "fcm_push", "push" -> true
            else -> false
        }
    }
    private fun restoreForegroundOpenAdIfNeeded() {
        if (!foregroundOpenAdDisabledForSplash) return
        foregroundOpenAdDisabledForSplash = false
        AdvertisePageMediator.setForegroundOpenAdEnabled(true)
    }
    private suspend fun completeProgressQuickly() {
        progressJob?.cancel()
        val startProgress = currentProgress
        if (startProgress >= TOTAL_PROGRESS) return
        val animationStartTime = SystemClock.elapsedRealtime()
        while (true) {
            if (isFinishing || isDestroyed) return
            val elapsed = SystemClock.elapsedRealtime() - animationStartTime
            val fraction = (elapsed.toFloat() / FINAL_PROGRESS_DURATION_MS).coerceIn(0f, 1f)
            val nextProgress = startProgress + ((TOTAL_PROGRESS - startProgress) * fraction).toInt()
            if (nextProgress > currentProgress) updateProgress(nextProgress)
            if (fraction >= 1f) break
            delay(PROGRESS_ANIMATION_FRAME_MS)
        }
        if (!isFinishing && !isDestroyed) updateProgress(TOTAL_PROGRESS)
    }


    private suspend fun finishLaunchOpenAdStage(hasLaunchOpenAd: Boolean) {
        if (isFinishing || isDestroyed) return
        if (hasReturnedFromBackgroundDuringSplash) {
            completeProgressQuickly()
            continueToNextStep()
            return
        }
        completeProgressQuickly()
        if (!hasLaunchOpenAd || !waitUntilForegroundForOpenAd()) {
            continueToNextStep()
            return
        }
        if (hasReturnedFromBackgroundDuringSplash) {
            continueToNextStep()
            return
        }
        isShowingLaunchOpenAd = true
        val adClosed = CompletableDeferred<Unit>()
        val showingAd = AdvertisePageMediator.showCachedLaunchOpenAd(this) {
            if (!adClosed.isCompleted) adClosed.complete(Unit)
        }
        if (!showingAd) {
            isShowingLaunchOpenAd = false
            continueToNextStep()
            return
        }

        val closedBySdk = withTimeoutOrNull(OPEN_AD_SHOW_TIMEOUT_MS) {
            adClosed.await()
            true
        } == true
        isShowingLaunchOpenAd = false
        if (!closedBySdk) {
            LogUtil.log("splash_open_ad_close_timeout", emptyMap())
        }
        continueToNextStep()
    }

    private suspend fun waitUntilForegroundForOpenAd(): Boolean {
        val deadline = SystemClock.elapsedRealtime() + OPEN_AD_FOREGROUND_WAIT_TIMEOUT_MS
        while (!isFinishing && !isDestroyed && SystemClock.elapsedRealtime() < deadline) {
            if (isForegroundForOpenAd()) return true
            delay(OPEN_AD_POLL_INTERVAL_MS)
        }
        return isForegroundForOpenAd()
    }

    private fun isForegroundForOpenAd(): Boolean {
        return lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }


    private suspend fun handleLaunchOpenAdStage() {
        if (isFinishing || isDestroyed) return
        finishLaunchOpenAdStage(prepareLaunchOpenAd())
    }

    private suspend fun prepareLaunchOpenAd(): Boolean {
        val consentPreparationDeadline =
            SystemClock.elapsedRealtime() + CONSENT_PREPARATION_TIMEOUT_MS
        if (waitForConsentInitialization(consentPreparationDeadline)) {
            showSplashConsentForStartup(consentPreparationDeadline)
        }
        if (isFinishing || isDestroyed || hasReturnedFromBackgroundDuringSplash) return false
        if (AdvertisePageMediator.hasCachedLaunchOpenAd()) return true

        tryBeginLaunchOpenAdPreload()
        return waitForLaunchOpenAd()
    }

    private suspend fun waitForLaunchOpenAd(): Boolean {
        val loadWaitTimeout = if (isNetworkAvailable(this)) {
            OPEN_AD_LOAD_WAIT_TIMEOUT_MS
        } else {
            OPEN_AD_OFFLINE_LOAD_WAIT_TIMEOUT_MS
        }
        val deadline = SystemClock.elapsedRealtime() + loadWaitTimeout
        while (!isFinishing && !isDestroyed && SystemClock.elapsedRealtime() < deadline) {
            if (AdvertisePageMediator.hasCachedLaunchOpenAd()) return true
            tryBeginLaunchOpenAdPreload()
            delay(OPEN_AD_POLL_INTERVAL_MS)
        }
        val hasCachedAd = AdvertisePageMediator.hasCachedLaunchOpenAd()
        if (!hasCachedAd) {
            LogUtil.log(
                "splash_open_ad_load_timeout",
                mapOf(
                    "attempts" to launchOpenAdPreloadScheduler.attemptCount,
                    "can_request_ads" to AdvertisePageMediator.canRequestAds(this),
                    "is_loading" to AdvertisePageMediator.isLaunchOpenAdLoading(),
                ),
            )
        }
        return hasCachedAd
    }
    private suspend fun waitForConsentInitialization(consentPreparationDeadline: Long): Boolean {
        val remaining = remainingConsentPreparationTime(consentPreparationDeadline)
        val completed = if (remaining > 0L) {
            withTimeoutOrNull(remaining) {
                consentInitFinished.await() == true
            } == true
        } else {
            false
        }
        if (!completed) {
            LogUtil.log("splash_consent_preparation_timeout", mapOf("stage" to "consent_init"))
        }
        return completed
    }

    private suspend fun showSplashConsentForStartup(consentPreparationDeadline: Long) {
        if (isFinishing || isDestroyed) return
        val formShown = CompletableDeferred<Unit>()
        val consentCompleted = CompletableDeferred<Unit>()
        var consentFormDisplayCancelled = false

        AdvertisePageMediator.showSplashConsent(
            this@SplashActivity,
            onWillShowForm = {
                if (consentFormDisplayCancelled || isFinishing || isDestroyed) {
                    false
                } else {
                    isShowingSplashConsent = true
                    LogUtil.log("ump_consent_form_will_show", emptyMap())
                    if (!formShown.isCompleted) formShown.complete(Unit)
                    true
                }
            },
            onError = { throwable ->
                LogUtil.log("ump_consent_error", mapOf("error" to (throwable.message ?: "")))
            },
        ) {
            if (!consentCompleted.isCompleted) consentCompleted.complete(Unit)
        }

        try {
            val consentFormDeadline = minOf(
                consentPreparationDeadline,
                SystemClock.elapsedRealtime() + SPLASH_CONSENT_FORM_TIMEOUT_MS,
            )
            when (awaitConsentCallbackBeforeFormIsShown(
                formShown,
                consentCompleted,
                consentFormDeadline,
            )) {
                true -> awaitConsentCompletionAfterFormIsShown(consentCompleted)
                false -> Unit
                null -> LogUtil.log(
                    "splash_consent_preparation_timeout",
                    mapOf("stage" to "consent_form"),
                )
            }
        } finally {
            consentFormDisplayCancelled = true
            isShowingSplashConsent = false
        }
    }

    private suspend fun awaitConsentCallbackBeforeFormIsShown(
        formShown: CompletableDeferred<Unit>,
        consentCompleted: CompletableDeferred<Unit>,
        consentPreparationDeadline: Long,
    ): Boolean? {
        val remaining = remainingConsentPreparationTime(consentPreparationDeadline)
        if (remaining <= 0L) return null
        return withTimeoutOrNull(remaining) {
            select<Boolean> {
                formShown.onAwait { true }
                consentCompleted.onAwait { false }
            }
        }
    }

    private suspend fun awaitConsentCompletionAfterFormIsShown(
        consentCompleted: CompletableDeferred<Unit>,
    ) {
        consentCompleted.await()
    }

    private fun remainingConsentPreparationTime(consentPreparationDeadline: Long): Long {
        return (consentPreparationDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }


    override fun onStop() {
        super.onStop()
        if (isShowingLaunchOpenAd || isWaitingForNotificationPermission || isShowingSplashConsent) {
            return
        }
        if (!hasContinuedToNextStep && !isFinishing && !isDestroyed) {
            hasReturnedFromBackgroundDuringSplash = true
            suppressForegroundOpenAdAfterSplashBackground()
        }
    }








    private fun startProgressLoop() {
        if (progressJob?.isActive == true) return
        progressJob = lifecycleScope.launch {
            progressStartTime = SystemClock.elapsedRealtime()
            while (currentProgress < PREPARATION_PROGRESS_MAX && !isFinishing && !isDestroyed) {
                val elapsed = SystemClock.elapsedRealtime() - progressStartTime
                val nextProgress = if (elapsed < STARTUP_PROGRESS_DURATION_MS) {
                    ((elapsed.toFloat() / STARTUP_PROGRESS_DURATION_MS) * VISUAL_PROGRESS_TARGET)
                        .toInt()
                } else {
                    val waitingElapsed = elapsed - STARTUP_PROGRESS_DURATION_MS
                    VISUAL_PROGRESS_TARGET +
                            ((waitingElapsed.toFloat() / WAITING_PROGRESS_DURATION_MS) *
                                    (PREPARATION_PROGRESS_MAX - VISUAL_PROGRESS_TARGET)).toInt()
                }.coerceIn(0, PREPARATION_PROGRESS_MAX)
                if (nextProgress > currentProgress) updateProgress(nextProgress)
                delay(PROGRESS_UPDATE_INTERVAL)
            }
        }
    }

    private fun updateProgress(progress: Int) {
        currentProgress = progress.coerceIn(0, TOTAL_PROGRESS)
        binding.progressBar.progress = currentProgress
    }

    private fun beginEarlyConsentInitialization() {
        if (hasStartedConsentInit) return
        hasStartedConsentInit = true
        AdvertisePageMediator.initSplashConsent(this) { success ->
            completeConsentInitialization(success)
        }
    }

    private fun completeConsentInitialization(success: Boolean) {
        if (!consentInitFinished.isCompleted) {
            consentInitFinished.complete(success)
        }
    }

    private fun tryBeginLaunchOpenAdPreload() {
        val hasCachedAd = AdvertisePageMediator.hasCachedLaunchOpenAd()
        val isLoading = preloadOpenAdJob?.isActive == true ||
                AdvertisePageMediator.isLaunchOpenAdLoading()
        if (!launchOpenAdPreloadScheduler.tryAcquire(
                nowMs = SystemClock.elapsedRealtime(),
                hasCachedAd = hasCachedAd,
                isLoading = isLoading,
            )
        ) {
            return
        }
        beginEarlyLaunchOpenAdPreload()
    }

    private fun beginEarlyLaunchOpenAdPreload() {
        if (preloadOpenAdJob?.isActive == true) return
        preloadOpenAdJob = lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                SdkInitUtils.awaitInitialization()
                if (!SdkInitUtils.isInitialized()) {
                    LogUtil.log(
                        "splash_open_ad_preload_attempt",
                        mapOf(
                            "attempt" to launchOpenAdPreloadScheduler.attemptCount,
                            "result" to "sdk_unavailable",
                        ),
                    )
                    return@runCatching
                }
                val result = AdvertisePageMediator.preloadLaunchAds(this@SplashActivity)
                LogUtil.log(
                    "splash_open_ad_preload_attempt",
                    mapOf(
                        "attempt" to launchOpenAdPreloadScheduler.attemptCount,
                        "result" to result.name.lowercase(),
                    ),
                )
            }.onFailure { throwable ->
                LogUtil.log(
                    "splash_open_ad_preload_error",
                    mapOf("error" to (throwable.message ?: throwable.javaClass.simpleName)),
                )
            }
        }
    }


    private fun suppressForegroundOpenAdDuringSplash() {
        foregroundOpenAdDisabledForSplash = true
        AdvertisePageMediator.setForegroundOpenAdEnabled(false)
    }

    private fun suppressForegroundOpenAdAfterSplashBackground() {
        foregroundOpenAdDisabledForSplash = true
        AdvertisePageMediator.suppressNextForegroundOpenAd()
        AdvertisePageMediator.setForegroundOpenAdEnabled(false)
    }


    private fun initFirstLaunchTracking() {
        if (!AppPrefsUtils.getBoolean(AppConfig.IS_SP_FIRST_SET)) {
            AppPrefsUtils.commitBoolean(AppConfig.IS_SP_FIRST_SET, true)
            AdvertiseUserAttributes.setUser(
                mapOf("my_first_open_time" to getDetailCurrentTime(System.currentTimeMillis())),
                "user_setOnce",
            )
        }
    }


    private fun setupClickableText() {
        val fullText = getString(R.string.splash_hint)
        val spannableString = SpannableString(fullText)
        // 处理 "Terms of Service"
        applyClickableSpan(
            spannableString,
            fullText,
            getString(R.string.terms_of_service),
            TERMS_URL
        )
        // 处理 "Privacy Policy"
        applyClickableSpan(
            spannableString,
            fullText,
            getString(R.string.privacy_policy),
            PRIVACY_URL
        )
        binding.tvHint.text = spannableString
        binding.tvHint.movementMethod = LinkMovementMethod.getInstance()
        binding.tvHint.highlightColor = Color.TRANSPARENT // 去除点击高亮
    }
    /**
     * 为指定文字设置颜色与点击事件
     * @param spannableString 总文本的 SpannableString
     * @param fullText 总文本字符串
     * @param target 需要设置样式的子串
     * @param url 点击后跳转的 WebView 地址
     */
    private fun applyClickableSpan(
        spannableString: SpannableString,
        fullText: String,
        target: String,
        url: String
    ) {
        val start = fullText.indexOf(target)
        if (start < 0) return
        val end = start + target.length
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val bundle = Bundle().apply {
                    putString(WebViewActivity.EXTRA_URL, url)
                    putString("tag", "splash")
                }

                readyGoThenKill(WebViewActivity::class.java, bundle)
            }

            override fun updateDrawState(ds: TextPaint) {
                // 自定义颜色 #242424，去掉下划线
                ds.color = Color.parseColor("#242424")
                ds.isUnderlineText = false
                ds.isFakeBoldText = true
            }
        }
        spannableString.setSpan(
            clickableSpan,
            start,
            end,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    // ========= 权限处理 =========
    private fun requestPostNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            completeNotificationPermission()
            return
        }
        isWaitingForNotificationPermission = true
        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE_POST_NOTIFICATIONS
        )
    }

    private fun completeNotificationPermission() {
        if (!notificationPermissionFinished.isCompleted) {
            notificationPermissionFinished.complete(Unit)
        }
    }




    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* 屏蔽返回键 */ }
        })
    }


    override fun onDestroy() {
        restoreForegroundOpenAdIfNeeded()
        val duration = (SystemClock.elapsedRealtime() - startElapsedRealtime).coerceAtLeast(0L)
        LogUtil.log("splash_display", mapOf("duration_time" to duration))
        progressJob?.cancel()
        startupJob?.cancel()
        preloadOpenAdJob?.cancel()
        isShowingLaunchOpenAd = false
        super.onDestroy()
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            isWaitingForNotificationPermission = false
            val isGranted =
                grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            val eventName = if (isGranted) "agree_push_permission" else "disagree_push_permission"
            LogUtil.log(eventName, emptyMap())
            AppPrefsUtils.commitBoolean("has_notification_permission", isGranted)
            completeNotificationPermission()
        }
    }
}