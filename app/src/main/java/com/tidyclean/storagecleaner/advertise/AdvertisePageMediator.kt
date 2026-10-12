package com.tidyclean.storagecleaner.advertise

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.mar2sdk.core.Core
import com.mar2sdk.core.ad.AdConfig
import com.mar2sdk.core.ad.AdLoader
import com.mar2sdk.core.ad.impl.admob.AdmobLoader
import com.mar2sdk.core.ad.policy.ScreenAdContext
import com.mar2sdk.core.ad.policy.ScreenAdTrigger
import com.mar2sdk.core.ad.status.AdFormat
import com.mar2sdk.core.ad.status.AdPlatform
import com.mar2sdk.impl.AdActivity
import com.mar2sdk.impl.AppOpenAdManager
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.utils.SdkInitUtils
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class LaunchAdsPreloadResult { REQUESTED, CONSENT_UNAVAILABLE, POLICY_DISABLED, FAILED }

object AdvertisePageMediator {
    private const val TAG = "AdvertisePageMediator"
    private const val SDK_INITIALIZATION_WAIT_TIMEOUT_MS = 8_000L

    fun initSplashConsent(activity: Activity, onComplete: (Boolean) -> Unit) {
        if (activity.isUnavailable()) return onComplete(false)
        runCatching {
            val cached = Core.initConsent(activity) { onComplete(it) }
            if (cached) onComplete(true)
        }.onFailure {
            Log.w(TAG, "initSplashConsent failed", it)
            onComplete(false)
        }
    }

    fun showSplashConsent(
        activity: Activity,
        onWillShowForm: (() -> Boolean)? = null,
        onError: ((Throwable) -> Unit)? = null,
        onComplete: () -> Unit,
    ) {
        if (activity.isUnavailable()) return onComplete()
        val completed = AtomicBoolean(false)
        fun completeOnce() {
            if (completed.compareAndSet(false, true)) onComplete()
        }
        runCatching {
            val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
            if (!consentInformation.isConsentFormAvailable) {
                completeOnce()
                return@runCatching
            }
            UserMessagingPlatform.loadConsentForm(
                activity,
                { form ->
                    if (activity.isUnavailable()) {
                        completeOnce()
                    } else if (UserMessagingPlatform.getConsentInformation(activity).consentStatus != ConsentInformation.ConsentStatus.REQUIRED) {
                        completeOnce()
                    } else if (onWillShowForm?.invoke() == false) {
                        completeOnce()
                    } else {
                        runCatching { form.show(activity) { completeOnce() } }
                            .onFailure {
                                onError?.invoke(it)
                                Log.w(TAG, "show consent form failed", it)
                                completeOnce()
                            }
                    }
                },
                { error ->
                    val exception = IllegalStateException("${error.errorCode}: ${error.message}")
                    onError?.invoke(exception)
                    Log.w(TAG, "load consent form failed", exception)
                    completeOnce()
                },
            )
        }.onFailure {
            onError?.invoke(it)
            Log.w(TAG, "showSplashConsent failed", it)
            completeOnce()
        }
    }

    fun showCompletionInterstitial(activity: Activity, nextTag: String, onClosed: () -> Unit) {
        showInterstitial(activity, completionInterstitialAreaKey(activity, nextTag), onClosed)
    }

    private fun completionInterstitialAreaKey(context: Context, nextTag: String): String = when (nextTag) {
        context.getString(R.string.remove_junk) -> AdAreaKeys.Interstitial.JUNK_CLEAN_FINISH
        context.getString(R.string.speaker_cleaner) -> AdAreaKeys.Interstitial.SPEAKER_CLEAN_FINISH
        else -> AdAreaKeys.Interstitial.FILE_MANAGE_FINISH
    }

    fun canRequestAds(context: Context): Boolean = runCatching {
        UserMessagingPlatform.getConsentInformation(context).canRequestAds()
    }.getOrDefault(false)

    fun preloadLaunchAds(context: Context): LaunchAdsPreloadResult {
        if (!canRequestAds(context)) return LaunchAdsPreloadResult.CONSENT_UNAVAILABLE
        if (!SdkInitUtils.isInitialized()) return LaunchAdsPreloadResult.FAILED
        if (!AdConfig.isOpen) return LaunchAdsPreloadResult.POLICY_DISABLED
        return runCatching {
            (context as? LifecycleOwner)?.lifecycleScope?.launch { AdLoader.fillAd() }
                ?: return LaunchAdsPreloadResult.FAILED
            LaunchAdsPreloadResult.REQUESTED
        }.onFailure { Log.w(TAG, "preloadLaunchAds failed", it) }
            .getOrDefault(LaunchAdsPreloadResult.FAILED)
    }

    fun hasCachedLaunchOpenAd(): Boolean = runCatching {
        AdmobLoader.checkPool(AdFormat.OPEN)
        AdmobLoader.openPool.isNotEmpty()
    }.getOrDefault(false)

    fun isLaunchOpenAdLoading(): Boolean = AdmobLoader.isLoadingOpen

    fun suppressNextForegroundOpenAd() = AppOpenAdManager.suppressNextOpen()

    fun setForegroundOpenAdEnabled(enabled: Boolean) { AppOpenAdManager.isEnabled = enabled }

    fun showCachedLaunchOpenAd(activity: Activity, onContinue: () -> Unit): Boolean {
        if (activity.isUnavailable()) return false
        val context = ScreenAdContext(
            areaKey = AdAreaKeys.Open.OPEN_PAGE,
            adFormat = AdFormat.OPEN,
            adPlatform = AdPlatform.ADMOB,
            trigger = ScreenAdTrigger.ENTER,
        )
        return showWithResumeCallback(activity, context, onContinue)
    }

    fun showInterstitial(activity: Activity, areaKey: String?, onClosed: () -> Unit) {
        if (activity.isUnavailable() || areaKey.isNullOrBlank()) return onClosed()
        val trigger = if (areaKey.startsWith("return", ignoreCase = true)) {
            ScreenAdTrigger.RETURN
        } else {
            ScreenAdTrigger.ENTER
        }
        val context = ScreenAdContext(
            areaKey = areaKey,
            adFormat = AdFormat.INTER,
            adPlatform = AdPlatform.ADMOB,
            trigger = trigger,
        )
        val lifecycleOwner = activity as? LifecycleOwner ?: return onClosed()
        lifecycleOwner.lifecycleScope.launch {
            runCatching {
                SdkInitUtils.awaitInitialization(SDK_INITIALIZATION_WAIT_TIMEOUT_MS)
                if (!SdkInitUtils.isInitialized()) return@runCatching onClosed()
                if (!showWithResumeCallback(activity, context, onClosed)) onClosed()
            }.onFailure {
                Log.w(TAG, "showInterstitial failed; areaKey=$areaKey", it)
                onClosed()
            }
        }
    }

    private fun showWithResumeCallback(
        activity: Activity,
        context: ScreenAdContext,
        onClosed: () -> Unit,
    ): Boolean {
        if (activity.isUnavailable()) return false
        val lifecycle = (activity as? LifecycleOwner)?.lifecycle ?: return false
        val completed = AtomicBoolean(false)
        fun completeOnce() {
            if (completed.compareAndSet(false, true) && !activity.isUnavailable()) onClosed()
        }
        val observer = object : DefaultLifecycleObserver {
            var paused = false
            override fun onPause(owner: LifecycleOwner) { paused = true }
            override fun onResume(owner: LifecycleOwner) {
                if (paused) {
                    lifecycle.removeObserver(this)
                    completeOnce()
                }
            }
            override fun onDestroy(owner: LifecycleOwner) {
                lifecycle.removeObserver(this)
                completeOnce()
            }
        }
        lifecycle.addObserver(observer)
        val launched = runCatching { AdActivity.showAd(activity, context) }
            .onFailure {
                lifecycle.removeObserver(observer)
                Log.w(TAG, "Unable to launch ad activity", it)
            }.getOrDefault(false)
        if (!launched) {
            lifecycle.removeObserver(observer)
            return false
        }
        return true
    }

    fun showFileAccessCancelInterstitial(activity: Activity, onClosed: () -> Unit = {}) =
        showInterstitial(activity, AdAreaKeys.Interstitial.FILE_ACCESS_CANCEL, onClosed)

    fun showReturnHomeInterstitial(activity: Activity, onClosed: () -> Unit) =
        showInterstitial(activity, AdAreaKeys.Interstitial.RETURN_HOME_PAGE, onClosed)

    fun showFeatureEntryInterstitial(activity: Activity, nextTag: String, onClosed: () -> Unit) =
        showInterstitial(activity, featureEntryInterstitialAreaKey(activity, nextTag), onClosed)

    fun showFeatureEntryInterstitialByTag(activity: Activity, nextTag: String, onClosed: () -> Unit) =
        showFeatureEntryInterstitial(activity, nextTag, onClosed)

    fun showFeatureEntryInterstitialByAreaKey(activity: Activity, areaKey: String, onClosed: () -> Unit) =
        showInterstitial(activity, InterstitialAreaResolver.forwardAreaKey(areaKey), onClosed)

    fun showUninstallFirstInterstitial(activity: Activity, onClosed: () -> Unit) =
        showInterstitial(activity, AdAreaKeys.Interstitial.UNINSTALL_ONE, onClosed)

    fun showUninstallSecondInterstitial(activity: Activity, onClosed: () -> Unit) =
        showInterstitial(activity, AdAreaKeys.Interstitial.UNINSTALL_TWO, onClosed)

    private fun featureEntryInterstitialAreaKey(context: Context, nextTag: String): String =
        InterstitialAreaResolver.featureEntry(
            labels = FeatureEntryLabels(
                removeJunk = context.getString(R.string.remove_junk),
                appUsage = context.getString(R.string.app_usage),
                deviceInfo = context.getString(R.string.device_info),
                batteryInfo = context.getString(R.string.battery_info),
                speakerCleaner = context.getString(R.string.speaker_cleaner),
                images = context.getString(R.string.images),
                videos = context.getString(R.string.video),
                audio = context.getString(R.string.audio),
                documents = context.getString(R.string.documents),
                largeFiles = context.getString(R.string.large_files),
            ),
            nextTag = nextTag,
        )

    private fun Activity.isUnavailable() = isFinishing || isDestroyed
}
