package com.tidyclean.storagecleaner.utils


import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

internal fun interface CpuUsageReader {
    suspend fun readUsagePercent(previousPercent: Int?): Int?
}

internal class ProcCpuUsageReader(
    private val ioDispatcher: CoroutineDispatcher,
) : CpuUsageReader {
    override suspend fun readUsagePercent(previousPercent: Int?): Int? =
        withContext(ioDispatcher) {
            readFromProcStat()
                ?: readFromLoadAverage()
                ?: readFromFrequencies()
                ?: previousPercent?.coerceIn(0, 100)
        }

    private suspend fun readFromProcStat(): Int? {
        val first = readCpuStat(File(PROC_STAT_PATH)) ?: return null
        delay(CPU_USAGE_SAMPLE_DELAY_MILLIS)
        val second = readCpuStat(File(PROC_STAT_PATH)) ?: return null
        return calculateCpuUsagePercent(first, second)
    }

    private fun readFromLoadAverage(): Int? =
        runCatching {
            val loadAverage =
                File(CPU_LOAD_AVERAGE_PATH)
                    .useLines { lines -> lines.firstOrNull().orEmpty() }
                    .trim()
                    .split(Regex("\\s+"))
                    .firstOrNull()
                    ?.toFloatOrNull()
            val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            loadAverage
                ?.takeIf { it.isFinite() && it >= 0f }
                ?.let { ((it / coreCount) * 100f).toCpuUsagePercent() }
        }.getOrNull()

    private fun readFromFrequencies(): Int? =
        runCatching {
            val ratios =
                File(CPU_SYSTEM_PATH)
                    .listFiles { file -> file.isDirectory && CPU_CORE_NAME_REGEX.matches(file.name) }
                    .orEmpty()
                    .mapNotNull { cpuDir ->
                        val current = readFirstCpuFrequency(cpuDir, CPU_CURRENT_FREQUENCY_FILES)
                        val max = readFirstCpuFrequency(cpuDir, CPU_MAX_FREQUENCY_FILES)
                        if (current > 0L && max > 0L) current.toFloat() / max else null
                    }
            ratios
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.times(100.0)
                ?.toFloat()
                ?.toCpuUsagePercent()
        }.getOrNull()
}

internal data class CpuStat(
    val idle: Long,
    val total: Long,
)

internal fun parseCpuStat(line: String): CpuStat? {
    val parts = line.trim().split(Regex("\\s+"))
    if (parts.size < 5 || parts.first() != "cpu") return null
    val values = parts.drop(1).mapNotNull { it.toLongOrNull() }
    if (values.size < 4) return null
    return CpuStat(
        idle = values[3] + values.getOrElse(4) { 0L },
        total = values.sum(),
    )
}

internal fun calculateCpuUsagePercent(
    first: CpuStat,
    second: CpuStat,
): Int? {
    val totalDiff = second.total - first.total
    val idleDiff = second.idle - first.idle
    if (totalDiff <= 0L || idleDiff < 0L || idleDiff > totalDiff) return null
    return (((totalDiff - idleDiff) * 100f) / totalDiff).toCpuUsagePercent()
}

private fun readCpuStat(file: File): CpuStat? =
    runCatching {
        file.useLines { lines -> parseCpuStat(lines.firstOrNull().orEmpty()) }
    }.getOrNull()

private fun readFirstCpuFrequency(
    cpuDir: File,
    fileNames: List<String>,
): Long =
    fileNames
        .asSequence()
        .mapNotNull { fileName ->
            runCatching {
                File(cpuDir, "cpufreq/$fileName")
                    .readText()
                    .trim()
                    .toLongOrNull()
            }.getOrNull()
        }.firstOrNull()
        ?: 0L

private fun Float.toCpuUsagePercent(): Int =
    roundToInt().coerceIn(MIN_REPORTED_CPU_USAGE_PERCENT, 100)

private const val PROC_STAT_PATH = "/proc/stat"
private const val CPU_USAGE_SAMPLE_DELAY_MILLIS = 360L
private const val CPU_SYSTEM_PATH = "/sys/devices/system/cpu"
private const val CPU_LOAD_AVERAGE_PATH = "/proc/loadavg"
private const val MIN_REPORTED_CPU_USAGE_PERCENT = 1

private val CPU_CORE_NAME_REGEX = Regex("cpu\\d+")
private val CPU_CURRENT_FREQUENCY_FILES = listOf("scaling_cur_freq", "cpuinfo_cur_freq")
private val CPU_MAX_FREQUENCY_FILES = listOf("cpuinfo_max_freq", "scaling_max_freq")
