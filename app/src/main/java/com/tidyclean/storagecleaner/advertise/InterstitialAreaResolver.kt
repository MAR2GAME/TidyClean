package com.tidyclean.storagecleaner.advertise

data class FeatureEntryLabels(
    val removeJunk: String,
    val appUsage: String,
    val deviceInfo:String,
    val batteryInfo: String,
    val speakerCleaner: String,
    val images: String,
    val videos: String,
    val audio: String,
    val documents: String,
    val largeFiles: String,

)

object InterstitialAreaResolver {
    fun featureEntry(labels: FeatureEntryLabels, nextTag: String): String = when (nextTag) {
        labels.removeJunk -> AdAreaKeys.Interstitial.ENTER_JUNK_CLEAN
        labels.appUsage -> AdAreaKeys.Interstitial.ENTER_APP_USAGE
        labels.deviceInfo -> AdAreaKeys.Interstitial.ENTER_DEVICE_INFO
        labels.batteryInfo -> AdAreaKeys.Interstitial.ENTER_BATTERY_INFO
        labels.speakerCleaner -> AdAreaKeys.Interstitial.ENTER_SPEAKER_CLEAN
        labels.images,
        labels.videos,
        labels.audio,
        labels.documents,
        labels.largeFiles,
        -> AdAreaKeys.Interstitial.ENTER_FILE_MANAGE
        else -> AdAreaKeys.Interstitial.ENTER_FILE_MANAGE
    }

    fun forwardAreaKey(areaKey: String): String = areaKey
}
