package com.tidyclean.storagecleaner.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.tidyclean.storagecleaner.bean.AppUsageEntry
import com.tidyclean.storagecleaner.bean.AppUsageRange
import com.tidyclean.storagecleaner.bean.AppUsageSnapshot


import java.util.Calendar
import kotlin.collections.forEach
import kotlin.collections.mapNotNull
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.collections.sumOf
import kotlin.collections.toSet
import kotlin.jvm.java
import kotlin.runCatching

object AppUsageAnalyzer {
    private const val EVENT_COOLDOWN_MS = 10_000L

    fun loadSnapshot(context: Context, range: AppUsageRange): AppUsageSnapshot {
        val timeWindow = createTimeWindow(range)
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            timeWindow.startTime,
            timeWindow.endTime
        )
        val totalTimeByPackage = mutableMapOf<String, Long>()
        usageStats.forEach { stats ->
            val totalTime = extractForegroundTime(stats)
            if (totalTime > 0L) {
                totalTimeByPackage[stats.packageName] =
                    (totalTimeByPackage[stats.packageName] ?: 0L) + totalTime
            }
        }
        val openCountByPackage = queryOpenCounts(
            usageStatsManager = usageStatsManager,
            startTime = timeWindow.startTime,
            endTime = timeWindow.endTime
        )

        val packageNames = (totalTimeByPackage.keys + openCountByPackage.keys).toSet()
        val packageManager = context.packageManager

        val entries = packageNames.mapNotNull { packageName ->
            val totalTimeMs = totalTimeByPackage[packageName] ?: 0L
            val openCount = openCountByPackage[packageName] ?: 0
            if (totalTimeMs <= 0L && openCount <= 0) {
                return@mapNotNull null
            }

            val applicationInfo = runCatching {
                packageManager.getApplicationInfo(packageName, 0)
            }.getOrNull() ?: return@mapNotNull null

            AppUsageEntry(
                packageName = packageName,
                appName = packageManager.getApplicationLabel(applicationInfo).toString(),
                icon = runCatching { packageManager.getApplicationIcon(applicationInfo) }.getOrNull(),
                totalTimeMs = totalTimeMs,
                openCount = openCount
            )
        }

        return AppUsageSnapshot(
            range = range,
            entries = entries,
            totalTimeMs = entries.sumOf { it.totalTimeMs },
            totalOpenCount = entries.sumOf { it.openCount }
        )
    }

    private fun queryOpenCounts(
        usageStatsManager: UsageStatsManager,
        startTime: Long,
        endTime: Long
    ): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        val lastCountedAt = mutableMapOf<String, Long>()
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (!isOpenEvent(event.eventType)) continue
            val packageName = event.packageName ?: continue
            val lastTime = lastCountedAt[packageName]
            if (lastTime != null && event.timeStamp - lastTime < EVENT_COOLDOWN_MS) {
                continue
            }
            counts[packageName] = (counts[packageName] ?: 0) + 1
            lastCountedAt[packageName] = event.timeStamp
        }

        return counts
    }

    private fun isOpenEvent(eventType: Int): Boolean {
        return eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                eventType == UsageEvents.Event.ACTIVITY_RESUMED
    }

    private fun extractForegroundTime(usageStats: UsageStats): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            maxOf(usageStats.totalTimeVisible, usageStats.totalTimeInForeground)
        } else {
            usageStats.totalTimeInForeground
        }
    }

    private fun createTimeWindow(range: AppUsageRange): TimeWindow {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        return when (range) {
            AppUsageRange.TODAY -> {
                calendar.timeInMillis = now
                resetToStartOfDay(calendar)
                TimeWindow(calendar.timeInMillis, now)
            }
            AppUsageRange.YESTERDAY -> {
                calendar.timeInMillis = now
                resetToStartOfDay(calendar)
                val end = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                TimeWindow(calendar.timeInMillis, end)
            }
            AppUsageRange.LAST_7_DAYS -> {
                calendar.timeInMillis = now
                calendar.add(Calendar.DAY_OF_YEAR, -6)
                resetToStartOfDay(calendar)
                TimeWindow(calendar.timeInMillis, now)
            }
            AppUsageRange.LAST_30_DAYS -> {
                calendar.timeInMillis = now
                calendar.add(Calendar.DAY_OF_YEAR, -29)
                resetToStartOfDay(calendar)
                TimeWindow(calendar.timeInMillis, now)
            }
        }
    }

    private fun resetToStartOfDay(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }

    private data class TimeWindow(
        val startTime: Long,
        val endTime: Long
    )
}
