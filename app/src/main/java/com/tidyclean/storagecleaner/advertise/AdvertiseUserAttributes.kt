package com.tidyclean.storagecleaner.advertise

import android.util.Log
import com.mar2sdk.core.log.ThinkingUtil

object AdvertiseUserAttributes {
    private const val TAG = "AdvertiseUserAttributes"

    fun setUser(params: Map<String, Any>, tag: String) {
        params.forEach { (key, value) ->
            runCatching {
                when (tag) {
                    "user_setOnce" -> ThinkingUtil.setUserOnceAttr(key, value.toString())
                    "user_set" -> ThinkingUtil.setUserAttr(key, value)
                    "user_add" -> ThinkingUtil.setUserAddAttr(key, value)
                    else -> Log.w(TAG, "Unknown user attribute tag: $tag")
                }
            }.onFailure { throwable ->
                Log.w(TAG, "setUser failed for $key with tag $tag", throwable)
            }
        }
    }
}
