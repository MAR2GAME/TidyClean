package com.tidyclean.storagecleaner.utils

import android.content.Context
import android.app.ActivityManager
import android.system.Os
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.io.InputStreamReader
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

object SystemUsageUtils {

    /**
     * 获取 RAM 使用率（0~100）
     */
    suspend fun getRamUsage(context: Context): Float = withContext(Dispatchers.IO) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalMemory = memoryInfo.totalMem
        val availableMemory = memoryInfo.availMem
        val usedMemory = totalMemory - availableMemory

        (usedMemory.toFloat() / totalMemory.toFloat()) * 100
    }
}
