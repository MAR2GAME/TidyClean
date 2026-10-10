package com.tidyclean.storagecleaner.advertise

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope

import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.pdffox.adv.AdvertiseSdk
import com.pdffox.adv.adv.AdConfig
import com.pdffox.adv.adv.AdPool
import com.pdffox.adv.adv.ShowOpenAd
import com.pdffox.adv.adv.policy.AdPolicyManager
import com.pdffox.adv.log.LogUtil
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.utils.SdkInitUtils

import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class LaunchAdsPreloadResult {
    REQUESTED,
    CONSENT_UNAVAILABLE,
    POLICY_DISABLED,
    FAILED,
}

object AdvertisePageMediator {
    private const val TAG = "AdvertisePageMediator"
    private const val SDK_INITIALIZATION_WAIT_TIMEOUT_MS = 8_000L
    fun initSplashConsent(activity: Activity, onComplete: (Boolean) -> Unit) {
        if (activity.isUnavailable()) {
            onComplete(false)
            return
        }
        runCatching {
            val hasCachedConsentState = AdvertiseSdk.initConsent(activity) { success ->
                onComplete(success)
            }
            if (hasCachedConsentState) onComplete(true)
        }.onFailure { throwable ->
            Log.w(TAG, "initSplashConsent failed", throwable)
            onComplete(false)
        }
    }

    fun showSplashConsent(
        activity: Activity,
        onWillShowForm: (() -> Boolean)? = null,
        onError: ((Throwable) -> Unit)? = null,
        onComplete: () -> Unit,
    ) {
        if (activity.isUnavailable()) {
            onComplete()
            return
        }

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
                { consentForm ->
                    if (activity.isUnavailable()) {
                        completeOnce()
                    } else {
                        val latestConsentInformation =
                            UserMessagingPlatform.getConsentInformation(activity)
                        if (latestConsentInformation.consentStatus !=
                            ConsentInformation.ConsentStatus.REQUIRED
                        ) {
                            completeOnce()
                        } else {
                            runCatching {
                                if (onWillShowForm?.invoke() != false) {
                                    consentForm.show(activity) { completeOnce() }
                                } else {
                                    completeOnce()
                                }
                            }.onFailure { throwable ->
                                Log.w(TAG, "show consent form failed", throwable)
                                logSplashConsentError(throwable, onError)
                                completeOnce()
                            }
                        }
                    }
                },
                { formError ->
                    logSplashConsentError(
                        IllegalStateException("${formError.errorCode}: ${formError.message}"),
                        onError,
                    )
                    completeOnce()
                },
            )
        }.onFailure { throwable ->
            Log.w(TAG, "showSplashConsent failed", throwable)
            logSplashConsentError(throwable, onError)
            completeOnce()
        }
    }

    fun showCompletionInterstitial(activity: Activity, nextTag: String, onClosed: () -> Unit) {
        val areaKey = completionInterstitialAreaKey(activity, nextTag)
        showInterstitial(activity, areaKey, onClosed)
    }

    private fun completionInterstitialAreaKey(context: Context, nextTag: String): String? {
        return when (nextTag) {
            context.getString(R.string.remove_junk) -> AdAreaKeys.Interstitial.JUNK_CLEAN_FINISH
            context.getString(R.string.speaker_cleaner) -> AdAreaKeys.Interstitial.SPEAKER_CLEAN_FINISH
            else ->  AdAreaKeys.Interstitial.FILE_MANAGE_FINISH
        }
    }

    fun canRequestAds(context: Context): Boolean {
        return runCatching {
            UserMessagingPlatform.getConsentInformation(context).canRequestAds()
        }.onFailure { throwable ->
            Log.w(TAG, "read UMP request state failed", throwable)
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun preloadLaunchAds(context: Context): LaunchAdsPreloadResult {
        if (!canRequestAds(context)) return LaunchAdsPreloadResult.CONSENT_UNAVAILABLE

        return runCatching {
            val canPreloadOpen =
                AdvertiseSdk.canPreloadOpen(AdvertiseSdk.LOAD_TIME_OPEN_APP)
            if (canPreloadOpen) {
                AdvertiseSdk.preloadOpen(context, AdvertiseSdk.LOAD_TIME_OPEN_APP)
            }
            if (AdvertiseSdk.canPreloadInterstitial(AdvertiseSdk.LOAD_TIME_OPEN_APP)) {
                AdvertiseSdk.preloadInterstitial(context, AdvertiseSdk.LOAD_TIME_OPEN_APP)
            }
            if (canPreloadOpen) {
                LaunchAdsPreloadResult.REQUESTED
            } else {
                LaunchAdsPreloadResult.POLICY_DISABLED
            }
        }.onFailure { throwable ->
            Log.w(TAG, "preloadLaunchAds failed", throwable)
        }.getOrDefault(LaunchAdsPreloadResult.FAILED)
    }

    fun hasCachedLaunchOpenAd(): Boolean {
        return runCatching {
            val cacheTimestamps = AdPool.admobOpenPool.values.toList()
            val cacheLifetimeMs = AdConfig.adload_cache_time_open
            val now = System.currentTimeMillis()
            cacheTimestamps.any { loadedAt -> now - loadedAt < cacheLifetimeMs }
        }.onFailure { throwable ->
            Log.w(TAG, "read App Open cache state failed", throwable)
        }.getOrDefault(false)
    }

    fun isLaunchOpenAdLoading(): Boolean {
        return runCatching {
            AdPool.admobOpenIsLoadingNum > 0
        }.onFailure { throwable ->
            Log.w(TAG, "read App Open loading state failed", throwable)
        }.getOrDefault(false)
    }

    fun suppressNextForegroundOpenAd() {
        runCatching {
            AdvertiseSdk.suppressNextAppOpenAd = true
        }.onFailure { throwable ->
            Log.w(TAG, "suppressNextForegroundOpenAd failed", throwable)
        }
    }

    fun setForegroundOpenAdEnabled(enabled: Boolean) {
        runCatching {
            AdvertiseSdk.isAppOpenAdEnabled = enabled
        }.onFailure { throwable ->
            Log.w(TAG, "setForegroundOpenAdEnabled failed", throwable)
        }
    }

    fun showCachedLaunchOpenAd(activity: Activity, onContinue: () -> Unit): Boolean {
        if (activity.isUnavailable()) return false
        val policyAllowsAd = runCatching {
            AdPolicyManager.checkAdUnit(AdAreaKeys.Open.OPEN_PAGE)
        }.onFailure { throwable ->
            Log.w(TAG, "check cached App Open policy failed", throwable)
        }.getOrDefault(false)
        if (!policyAllowsAd) return false

        var continued = false

        fun continueOnce() {
            if (continued) return
            continued = true
            if (!activity.isUnavailable()) onContinue()
        }

        val cachedOpenAd = runCatching {
            AdPool.getAdmobOpen(
                AdAreaKeys.Open.OPEN_PAGE,
                onClosed = { continueOnce() },
                onDisplayed = { },
            )
        }.onFailure { throwable ->
            Log.w(TAG, "consume cached App Open failed", throwable)
        }.getOrNull() ?: return false

        return runCatching {
            ShowOpenAd.showAdmobOpenAdFromCache(
                cachedOpenAd,
                activity,
                AdAreaKeys.Open.OPEN_PAGE,
                ShowOpenAd.OpenAdCloseListener {
                    continueOnce()
                },
                ShowOpenAd.OpenAdLoadedListener { },
                ShowOpenAd.OpenAdPaidListener { _ -> },
            )
            true
        }.onFailure { throwable ->
            Log.w(TAG, "showCachedLaunchOpenAd failed", throwable)
        }.getOrDefault(false)
    }





    fun showInterstitial(activity: Activity, areaKey: String?, onClosed: () -> Unit) {
        if (activity.isUnavailable()) {
            Log.w(TAG, "interstitial skipped: activity unavailable; areaKey=$areaKey")
            return
        }
        if (areaKey.isNullOrBlank()) {
            Log.w(TAG, "interstitial skipped: blank areaKey")
            onClosed()
            return
        }

        val continued = AtomicBoolean(false)
        fun continueOnce() {
            if (continued.compareAndSet(false, true) && !activity.isUnavailable()) onClosed()
        }

        Log.i(
            TAG,
            "interstitial requested: areaKey=$areaKey, initialized=${SdkInitUtils.isInitialized()}",
        )
        val lifecycleOwner = activity as? LifecycleOwner
        if (lifecycleOwner == null) {
            Log.w(TAG, "interstitial skipped: activity has no lifecycle; areaKey=$areaKey")
            continueOnce()
            return
        }
        lifecycleOwner.lifecycleScope.launch {
            runCatching {
                SdkInitUtils.awaitInitialization(timeoutMs = SDK_INITIALIZATION_WAIT_TIMEOUT_MS)
                val initialized = SdkInitUtils.isInitialized()
                Log.i(TAG, "interstitial initialization complete: areaKey=$areaKey, initialized=$initialized")
                if (!initialized) {
                    Log.w(TAG, "interstitial skipped: SDK initialization unavailable; areaKey=$areaKey")
                    continueOnce()
                    return@runCatching
                }
                if (activity.isUnavailable()) {
                    Log.w(TAG, "interstitial skipped: activity became unavailable; areaKey=$areaKey")
                    return@runCatching
                }
                Log.i(TAG, "interstitial dispatching to SDK: areaKey=$areaKey")
                AdvertiseSdk.showInterstitialAd(activity, areaKey) { continueOnce() }
            }.onFailure { throwable ->
                Log.w(TAG, "showInterstitial failed; areaKey=$areaKey; continuing flow", throwable)
                continueOnce()
            }
        }
    }


    fun showFileAccessCancelInterstitial(activity: Activity, onClosed: () -> Unit = {}) {
        showInterstitial(activity, AdAreaKeys.Interstitial.FILE_ACCESS_CANCEL, onClosed)
    }

    fun showReturnHomeInterstitial(activity: Activity, onClosed: () -> Unit) {
        showInterstitial(activity, AdAreaKeys.Interstitial.RETURN_HOME_PAGE, onClosed)
    }


    fun showFeatureEntryInterstitial(
        activity: Activity,
        nextTag: String,
        onClosed: () -> Unit,
    ) {
        showFeatureEntryInterstitialByTag(activity, nextTag, onClosed)
    }

    fun showFeatureEntryInterstitialByTag(
        activity: Activity,
        nextTag: String,
        onClosed: () -> Unit,
    ) {
        val areaKey = featureEntryInterstitialAreaKey(activity, nextTag)
        Log.i(TAG, "feature entry tag resolved: tag=$nextTag, areaKey=$areaKey")
        showInterstitial(activity, areaKey, onClosed)
    }

    fun showFeatureEntryInterstitialByAreaKey(
        activity: Activity,
        areaKey: String,
        onClosed: () -> Unit,
    ) {
        showInterstitial(activity, InterstitialAreaResolver.forwardAreaKey(areaKey), onClosed)
    }

    fun showUninstallFirstInterstitial(activity: Activity, onClosed: () -> Unit) {
        showInterstitial(activity, AdAreaKeys.Interstitial.UNINSTALL_ONE, onClosed)
    }

    fun showUninstallSecondInterstitial(activity: Activity, onClosed: () -> Unit) {
        showInterstitial(activity, AdAreaKeys.Interstitial.UNINSTALL_TWO, onClosed)
    }

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
                largeFiles = context.getString(R.string.large_files)
            ),
            nextTag = nextTag,
        )




    private fun logSplashConsentError(
        throwable: Throwable,
        onError: ((Throwable) -> Unit)? = null,
    ) {
        onError?.invoke(throwable)
            ?: LogUtil.log("ump_consent_error", mapOf("error" to (throwable.message ?: "")))
    }

    private fun Activity.isUnavailable(): Boolean = isFinishing || isDestroyed
}
