package com.tidyclean.storagecleaner.advertise

import android.util.Log
import com.pdffox.adv.log.ThinkingAttr

object AdvertiseUserAttributes {
    private const val TAG = "AdvertiseUserAttributes"

    fun setUser(params: Map<String, Any>, tag: String) {
        params.forEach { (key, value) ->
            runCatching {
                when (tag) {
                    "user_setOnce" -> ThinkingAttr.setUserOnceAttr(key, value.toString())
                    "user_set" -> ThinkingAttr.setUserAttr(key, value)
                    "user_add" -> ThinkingAttr.setUserAddAttr(key, value)
                    else -> Log.w(TAG, "Unknown user attribute tag: $tag")
                }
            }.onFailure { throwable ->
                Log.w(TAG, "setUser failed for $key with tag $tag", throwable)
            }
        }
    }
}
