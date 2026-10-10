package com.tidyclean.storagecleaner.utils

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

import com.tidyclean.storagecleaner.R

import kotlin.collections.any
import kotlin.collections.firstOrNull


enum class FileManagerCategory(
    @StringRes val routeTagRes: Int,
    @StringRes val displayNameRes: Int,
    @StringRes val loadingHintRes: Int,
    @DrawableRes val placeholderIconRes: Int,
    val supportsTypeFilter: Boolean
) {
    LARGE_FILES(
        routeTagRes = R.string.large_file_cleaner,
        displayNameRes = R.string.large_files,
        loadingHintRes = R.string.scanning_large_file,
        placeholderIconRes = R.mipmap.ic_file ,
        supportsTypeFilter = true
    ),
    IMAGES(
        routeTagRes = R.string.images,
        displayNameRes = R.string.images,
        loadingHintRes = R.string.scanning_images,
        placeholderIconRes = R.mipmap.ic_image,
        supportsTypeFilter = false
    ),
    VIDEO(
        routeTagRes = R.string.video,
        displayNameRes = R.string.video,
        loadingHintRes = R.string.scanning_videos,
        placeholderIconRes = R.mipmap.ic_video,
        supportsTypeFilter = false
    ),
    AUDIO(
        routeTagRes = R.string.audio,
        displayNameRes = R.string.audio,
        loadingHintRes = R.string.scanning_audio_files,
        placeholderIconRes = R.mipmap.ic_audio,
        supportsTypeFilter = false
    ),
    DOCUMENTS(
        routeTagRes = R.string.documents,
        displayNameRes = R.string.documents,
        loadingHintRes = R.string.scanning_documents,
        placeholderIconRes = R.mipmap.ic_documents,
        supportsTypeFilter = false
    );


    companion object {
        fun fromTag(context: Context, tag: String?): FileManagerCategory {
            return values().firstOrNull { context.getString(it.routeTagRes) == tag } ?: LARGE_FILES
        }

        fun isManagedTag(context: Context, tag: String?): Boolean {
            return values().any { context.getString(it.routeTagRes) == tag }
        }
    }
}
