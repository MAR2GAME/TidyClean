package com.tidyclean.storagecleaner.utils

import java.math.BigDecimal
import java.math.RoundingMode

object SizeUtil {

    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")

    /**
     * 将字节数格式化为易读的存储单位字符串。
     *
     * @param bytes         文件大小（字节），不能为负；负数将原样返回数字字符串
     * @param decimalPlaces 小数位数，默认 2 位，建议范围 0~3
     * @return 例如 1536 -> "1.50 KB"；0 -> "0 B"；1TB -> "1.00 TB"
     */
    fun formatSize(bytes: Long, decimalPlaces: Int = 2): String {
        // 负数直接返回原始字符串（与原逻辑保持一致）
        if (bytes < 0) return bytes.toString()
        if (bytes == 0L) return "0 B"

        // 确保小数位合法
        val scale = if (decimalPlaces < 0) 2 else decimalPlaces

        var value = bytes.toDouble()
        var unitIndex = 0
        // 循环除以 1024，直到小于 1024 或到达最大单位
        while (value >= 1024 && unitIndex < UNITS.lastIndex) {
            value /= 1024.0
            unitIndex++
        }

        // 使用 BigDecimal 避免浮点数格式化误差，并按指定小数位四舍五入
        val formattedValue = BigDecimal.valueOf(value)
            .setScale(scale, RoundingMode.HALF_UP)
            .toPlainString()

        return "$formattedValue ${UNITS[unitIndex]}"
    }
}