package com.tidyclean.storagecleaner.advertise

import com.mar2sdk.core.ad.impl.admob.AdmobConfig
import com.mar2sdk.core.ad.impl.admob.NativeAdConfig
import com.tidyclean.storagecleaner.BuildConfig

/** Applies host supplied ad identifiers while strategy remains in Mar2SDK resources. */
object AdvertiseConfigFactory {
    fun applyAdmobIds() {
        AdmobConfig.openConfig = AdmobConfig.openConfig.copy(id = BuildConfig.ADV_ADMOB_OPEN_ID)
        AdmobConfig.interConfig = AdmobConfig.interConfig.copy(id = BuildConfig.ADV_ADMOB_INTERSTITIAL_ID)
        AdmobConfig.videoConfig = AdmobConfig.videoConfig.copy(
            id = BuildConfig.ADV_ADMOB_REWARDED_ID.ifBlank { BuildConfig.ADV_ADMOB_INTERSTITIAL_ID },
        )
        AdmobConfig.bannerConfig = AdmobConfig.bannerConfig.copy(id = BuildConfig.ADV_ADMOB_BANNER_ID)
        val nativeId = BuildConfig.ADV_ADMOB_NATIVE_ID
        if (nativeId.isNotBlank()) {
            AdmobConfig.nativeConfig = AdmobConfig.nativeConfig.copy(
                ads = listOf(NativeAdConfig(nativeId, nativeId, nativeId)),
            )
        }
        AdmobConfig.saveAdmobConfig()
    }
}
