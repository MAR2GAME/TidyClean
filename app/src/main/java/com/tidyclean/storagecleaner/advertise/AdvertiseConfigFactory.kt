package com.tidyclean.storagecleaner.advertise

import android.content.Context


import com.pdffox.adv.AdvertiseSdkConfig
import com.pdffox.adv.AdvertiseSdkConfigBuilder
import com.pdffox.adv.AdvertiseSdkConfigs
import com.pdffox.adv.NotificationActionConfig
import com.pdffox.adv.NotificationFeatureConfig
import com.pdffox.adv.NotificationRouteMapping
import com.pdffox.adv.PushSceneKeyConfig
import com.tidyclean.storagecleaner.BuildConfig
import com.tidyclean.storagecleaner.R

import com.tidyclean.storagecleaner.base.config.AppConfig


object AdvertiseConfigFactory {
    private const val DEFAULT_PERSISTENT_CONTENT_TEXT = "PhoneCleanUp"

    fun create(context: Context): AdvertiseSdkConfig {
        return AdvertiseSdkConfigs.create(context, BuildConfig.DEBUG) {
            configureCoolCleaner(
                packageName = context.packageName,
                persistentContentText = context.getString(R.string.app_name),
                isDebug = BuildConfig.DEBUG,
                applicationContext = context,
            )
        }
    }

    fun create(packageName: String, isDebug: Boolean): AdvertiseSdkConfig {
        return AdvertiseSdkConfigs.create(packageName, isDebug) {
            configureCoolCleaner(
                packageName = packageName,
                persistentContentText = DEFAULT_PERSISTENT_CONTENT_TEXT,
                isDebug = BuildConfig.DEBUG,
                applicationContext = null,
            )
        }
    }

    private fun AdvertiseSdkConfigBuilder.configureCoolCleaner(
        packageName: String,
        persistentContentText: String,
        isDebug: Boolean,
        applicationContext: Context?,
    ) {
        legal(
            privacyUrl = AppConfig.PRIVACY_URL,
            termsUrl = AppConfig.TERMS_URL,
        )
        defaultTopic(BuildConfig.ADV_DEFAULT_TOPIC)
        resources(
            adPolicyRawResId = R.raw.ad_policy,
            adLoadConfigRawResId = R.raw.adload_config,
            cloudCidrsRawResId = com.pdffox.adv.R.raw.cloud,
            googleCidrsRawResId = com.pdffox.adv.R.raw.google
        )
        server(
            enabled = AdvertiseRuntimeCapabilities.SERVER_ENABLED,
            releaseHost = AppConfig.ADVHost,
            testHost = "",
            parseTokenKey = "TianWangGaiDiHu",
        )
        firebase(
            analyticsEnabled = AdvertiseRuntimeCapabilities.FIREBASE_ANALYTICS_ENABLED,
            messagingEnabled = AdvertiseRuntimeCapabilities.FIREBASE_MESSAGING_ENABLED,
            subscribeDefaultTopic = AdvertiseRuntimeCapabilities.FIREBASE_SUBSCRIBE_DEFAULT_TOPIC,
        )
        remoteConfig(
            enabled = true,
            encryptionKeyBase64 = BuildConfig.ADV_REMOTE_CONFIG_ENCRYPTION_KEY,
            encryptionKeyId = BuildConfig.ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID,
        )
        thinking(
            enabled = AdvertiseRuntimeCapabilities.THINKING_ENABLED,
            appKey = AppConfig.THINKING_APPID,
            serverUrl = AppConfig.THINKING_URL,
        )
        singular(
            enabled = AdvertiseRuntimeCapabilities.SINGULAR_ENABLED,
            apiKey = AppConfig.Singular_Api_Key,
            secret = AppConfig.Singular_Secret,
        )
        adMob(
            enabled = AdvertiseRuntimeCapabilities.ADMOB_ENABLED,
            appId = BuildConfig.ADV_ADMOB_APP_ID,
            bannerId = BuildConfig.ADV_ADMOB_BANNER_ID,
            interstitialId = BuildConfig.ADV_ADMOB_INTERSTITIAL_ID,
            nativeId = BuildConfig.ADV_ADMOB_NATIVE_ID,
            openId = BuildConfig.ADV_ADMOB_OPEN_ID,
            rewardedId = BuildConfig.ADV_ADMOB_REWARDED_ID,
        )
        facebook(
            enabled =false,
            appId = "",
            clientToken = "",
        )
        tiktok(
            enabled =false,
            accessToken = "",
            ttAppId = "",
            appId = packageName,
        )
        safe(
            enabled = false,
            expectedSignatures = "",
            expectedPackageName = packageName,
            rejectDebuggableBuilds =false,
            rejectDebuggerAttached =false,
            killProcessOnFailure =false,
        )
        push(
            enabled = AdvertiseRuntimeCapabilities.PUSH_ENABLED,
            persistentServiceEnabled = AdvertiseRuntimeCapabilities.PUSH_PERSISTENT_SERVICE_ENABLED,
            firebaseMessagingServiceEnabled = AdvertiseRuntimeCapabilities.PUSH_FIREBASE_MESSAGING_SERVICE_ENABLED,
            serviceStarterJobEnabled = AdvertiseRuntimeCapabilities.PUSH_SERVICE_STARTER_JOB_ENABLED,
            bootReceiverEnabled = AdvertiseRuntimeCapabilities.PUSH_BOOT_RECEIVER_ENABLED,
            notificationDeletedReceiverEnabled = AdvertiseRuntimeCapabilities.PUSH_NOTIFICATION_DELETED_RECEIVER_ENABLED,
            fileProviderEnabled = AdvertiseRuntimeCapabilities.PUSH_FILE_PROVIDER_ENABLED,
            deletionObserverEnabled = AdvertiseRuntimeCapabilities.PUSH_DELETION_OBSERVER_ENABLED,
            sceneKeys = PushSceneKeyConfig(
                imageDeleted = "delete_photos",
                videoDeleted = "delete_videos",
                fileDeleted = "delete_files",
            ),
        )
        notifications(notificationConfig(persistentContentText, applicationContext))
        playIntegrity(
            enabled = false,
            cloudProjectNumber = 873431583316L,
        )

    }

    private fun notificationConfig(
        persistentContentText: String,
        applicationContext: Context?,
    ): NotificationFeatureConfig {
        fun label(resourceId: Int, fallback: String): String =
            applicationContext?.getString(resourceId) ?: fallback

        return NotificationFeatureConfig(
            enabled = AdvertiseRuntimeCapabilities.NOTIFICATIONS_ENABLED,
            smallIconResId = R.mipmap.ic_logo,
            persistentContentText = persistentContentText,
            persistentActions = listOf(
                NotificationActionConfig("junkClean", "Clean", R.mipmap.ic_removejunk),
                NotificationActionConfig("manageLargeFiles", "File", R.mipmap.ic_file_push),
                NotificationActionConfig("appUsage", "App", R.mipmap.ic_app_push),
                NotificationActionConfig("batteryInfo", "Battery", R.mipmap.ic_battery_push),
            ),
            routeMappings = notificationRouteMappings(),
        )
    }

    internal fun notificationRouteMappings(): List<NotificationRouteMapping> {
        return listOf(
            route("/home", "home", R.mipmap.ic_logo),
            route("main", "main", R.mipmap.ic_logo),
            route("/main", "main", R.mipmap.ic_logo),
            route("/junkClean", "junkClean", R.mipmap.ic_removejunk),
            route("/batteryInfo", "batteryInfo", R.mipmap.ic_battery_push),
            route("/speakerCleaner", "speakerCleaner", R.mipmap.ic_sp),
            route("/deviceInfo", "deviceInfo", R.mipmap.ic_dv),
            route("/fileManager", "fileManager", R.mipmap.ic_file_push),
            route("/manageLargeFiles", "manageLargeFiles", R.mipmap.ic_file_push),
            route("/appUsage", "appUsage", R.mipmap.ic_app_push),
            route("appUsage", "appUsage", R.mipmap.ic_app_push)
        )
    }

    private fun route(rawRoute: String, route: String, iconResId: Int): NotificationRouteMapping {
        return NotificationRouteMapping(
            rawRoute = rawRoute,
            route = route,
            temporaryIconResId = iconResId,
            persistentIconResId = iconResId,
        )
    }
}
