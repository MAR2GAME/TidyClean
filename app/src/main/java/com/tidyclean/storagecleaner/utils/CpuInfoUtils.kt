package com.tidyclean.storagecleaner.utils

import android.os.Build
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object CpuInfoUtils {
    data class CpuInfo(
        val hardware: String,      // 如 "mt6789"
        val model: String,         // 如 "Unknown" 或具体型号
        val cores: Int,            // 核心数
        val maxFrequencyMHz: Long  // 最大频率（MHz），-1 表示未知
    )

    fun getCpuInfo(): CpuInfo {
        val hardware = getCpuHardware()
        val model = getCpuModel()
        val cores = getCpuCores()
        val freq = getMaxCpuFrequency()
        return CpuInfo(hardware, model, cores, freq)
    }

    private fun getCpuCores(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    private fun getMaxCpuFrequency(): Long {
        val paths = listOf(
            "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq",
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq",
            "/sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state"
        )
        for (path in paths) {
            try {
                val file = File(path)
                if (file.exists()) {
                    val content = file.readText().trim()
                    if (path.endsWith("time_in_state")) {
                        val firstLine = content.lines().firstOrNull()?.split(" ")?.firstOrNull()
                        if (firstLine != null) return firstLine.toLong() / 1000
                    } else {
                        return content.toLong() / 1000
                    }
                }
            } catch (_: Exception) { }
        }
        return -1
    }

    private fun getCpuHardware(): String {
        // 优先使用 Build.HARDWARE
        val hardware = Build.HARDWARE ?: ""
        if (hardware.isNotEmpty()) return hardware

        // 尝试从 /proc/cpuinfo 读取
        try {
            val process = Runtime.getRuntime().exec("cat /proc/cpuinfo")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line?.startsWith("Hardware") == true) {
                    val parts = line?.split(":") ?: emptyList()
                    if (parts.size > 1) return parts[1].trim()
                }
            }
            reader.close()
        } catch (_: Exception) { }

        // 备用
        return Build.BOARD ?: "Unknown"
    }

    private fun getCpuModel(): String {
        // 尝试从 /proc/cpuinfo 获取 Processor 或 model name
        try {
            val process = Runtime.getRuntime().exec("cat /proc/cpuinfo")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line?.contains("Processor") == true || line?.contains("model name") == true) {
                    val parts = line?.split(":") ?: emptyList()
                    if (parts.size > 1) return parts[1].trim()
                }
            }
            reader.close()
        } catch (_: Exception) { }

        // 备用
        return System.getProperty("ro.product.board") ?: "Unknown"
    }
}