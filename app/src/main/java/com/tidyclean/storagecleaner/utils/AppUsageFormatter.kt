package com.tidyclean.storagecleaner.utils

import android.content.Context

import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.bean.AppUsageMetric
import com.tidyclean.storagecleaner.bean.AppUsageSnapshot


object AppUsageFormatter {
    private const val ONE_MINUTE_MS = 60_000L
    private const val ONE_HOUR_MS = 60 * ONE_MINUTE_MS

    internal data class DurationParts(val hours: Int, val minutes: Int, val belowOneMinute: Boolean)

    internal fun durationParts(durationMs: Long): DurationParts {
        if (durationMs < ONE_MINUTE_MS) return DurationParts(0, 0, belowOneMinute = true)
        val totalMinutes = durationMs / ONE_MINUTE_MS
        return DurationParts(
            hours = (totalMinutes / 60).toInt(),
            minutes = (totalMinutes % 60).toInt(),
            belowOneMinute = false
        )
    }

    fun formatDurationCompact(context: Context, durationMs: Long): String {
        val duration = durationParts(durationMs)
        return when {
            duration.belowOneMinute -> context.getString(R.string.app_usage_duration_less_than_minute)
            duration.hours > 0 && duration.minutes > 0 -> context.getString(
                R.string.app_usage_duration_hours_minutes,
                duration.hours,
                duration.minutes
            )
            duration.hours > 0 -> context.getString(R.string.app_usage_duration_hours, duration.hours)
            else -> context.getString(R.string.app_usage_duration_minutes, duration.minutes)
        }
    }

    fun formatDurationDetail(context: Context, durationMs: Long): String {
        val duration = durationParts(durationMs)
        return when {
            duration.belowOneMinute -> context.getString(R.string.app_usage_duration_less_than_minute)
            duration.hours > 0 && duration.minutes > 0 -> context.getString(
                R.string.app_usage_duration_hours_minutes,
                duration.hours,
                duration.minutes
            )
            duration.hours > 0 -> context.getString(
                R.string.app_usage_duration_hours_zero_minutes,
                duration.hours
            )
            else -> context.getString(R.string.app_usage_duration_minutes, duration.minutes)
        }
    }

    fun formatOpenCount(context: Context, openCount: Int): String =
        context.resources.getQuantityString(
            R.plurals.app_usage_opened_count,
            openCount,
            openCount
        )

    fun formatSummaryValue(
        context: Context,
        metric: AppUsageMetric,
        snapshot: AppUsageSnapshot
    ): String {
        return when (metric) {
            AppUsageMetric.HOURS_SPENT -> formatDurationDetail(context, snapshot.totalTimeMs)
            AppUsageMetric.TIMES_OPENED -> context.resources.getQuantityString(
                R.plurals.app_usage_times_count,
                snapshot.totalOpenCount,
                snapshot.totalOpenCount
            )
        }
    }

    fun canManageApp(
        currentMetric: AppUsageMetric,
        packageName: String,
        currentPackageName: String,
        totalTimeMs: Long,
        openCount: Int
    ): Boolean {
        if (packageName == currentPackageName) return false
        if( currentMetric== AppUsageMetric.HOURS_SPENT){
            if (totalTimeMs < ONE_MINUTE_MS) return false
        }else{
            if (openCount < 1) return false
        }
        return true
    }
}
