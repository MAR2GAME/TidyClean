package com.tidyclean.storagecleaner.bean

data class BatteryInfo(
    val level: Int,              // 当前电量百分比 (0-100)
    val scale: Int,              // 最大电量值 (通常是100)
    val health: String,          // 电池健康状态
    val status: String,          // 充电状态
    val plugged: String,         // 充电方式
    val voltage: Int,            // 电池电压 (mV)
    val temperature: Float,        // 电池温度 (0.1°C 单位)
    val technology: String,      // 电池技术类型
    val capacity: Int   ,// 电池容量 (mAh)
    val timestamp: Long = System.currentTimeMillis(), // 时间戳
    val availableTime: String
) {
}

