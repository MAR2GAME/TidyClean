package com.tidyclean.storagecleaner.bean

import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.StringRes

import com.tidyclean.storagecleaner.R


enum class AppUsageRange(@StringRes val labelResId: Int) {
    TODAY(R.string.today),
    YESTERDAY(R.string.yesterday),
    LAST_7_DAYS(R.string.last_7_days),
    LAST_30_DAYS(R.string.last_30_days)
}

enum class AppUsageMetric {
    HOURS_SPENT,
    TIMES_OPENED
}

data class AppUsageEntry(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val totalTimeMs: Long,
    val openCount: Int
)

data class AppUsageSnapshot(
    val range: AppUsageRange,
    val entries: List<AppUsageEntry>,
    val totalTimeMs: Long,
    val totalOpenCount: Int
)

data class AppUsageListItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val valueText: String,
    val progress: Int,
    val canManage: Boolean
)

data class AppUsageDonutSlice(
    val label: String,
    @ColorInt val color: Int,
    val value: Float
)
