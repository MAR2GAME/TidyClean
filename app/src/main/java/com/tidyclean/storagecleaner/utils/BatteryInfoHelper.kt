package com.tidyclean.storagecleaner.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.bean.BatteryInfo


import java.lang.reflect.Method
import kotlin.math.max

class BatteryInfoHelper(private val context: Context) {

    companion object {
        private const val UNKNOWN_VALUE = -1
        private const val MICRO_TO_MILLI_CONVERSION = 1000.0
        private const val TEMPERATURE_DIVISOR = 10f
        private const val ASSUMED_HOURLY_DRAIN = 18.5f
        private const val DEFAULT_HOURLY_DRAIN = 10f
        private const val MIN_VALID_LEVEL = 0
        private const val MAX_VALID_LEVEL = 100
        private const val MILLIS_IN_MINUTE = 60 * 1000L
    }

    private var consumptionRatePerMinute: Float? = null
    private var lastBatteryLevel: Int? = null
    private var lastTimeRecorded: Long? = null

    /**
     * 获取完整的电池信息（包含状态与充电方式描述）
     */
    fun getBatteryInfo(): BatteryInfo {
        return try {
            val batteryStatusIntent = getBatteryStatusIntent()
            val level = getBatteryLevel(batteryStatusIntent)

            BatteryInfo(
                level = level,
                scale = getBatteryScale(batteryStatusIntent),
                health = getBatteryHealth(batteryStatusIntent),
                status = getBatteryStatusTextFromIntent(batteryStatusIntent),
                plugged = getChargingPlugTypeTextFromIntent(batteryStatusIntent),
                voltage = getBatteryVoltage(batteryStatusIntent),
                temperature = getBatteryTemperature(batteryStatusIntent) / TEMPERATURE_DIVISOR,
                technology = getBatteryTechnology(batteryStatusIntent),
                capacity = getBatteryCapacity(),
                timestamp = System.currentTimeMillis(),
                availableTime = getRemainingTimeEstimate(level)
            )
        } catch (e: Exception) {
            createDefaultBatteryInfo()
        }
    }

    /**
     * 获取电池当前状态码（充电中、放电中、已满等）
     * @return BatteryManager.BATTERY_STATUS_XXX 常量
     */
    fun getBatteryStatus(): Int {
        val batteryStatus = getBatteryStatusIntent()
        return batteryStatus.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        )
    }

    /**
     * 获取充电电源类型码（USB、交流电、无线等）
     * @return BatteryManager.BATTERY_PLUGGED_XXX 常量
     */
    fun getChargingPlugType(): Int {
        val batteryStatus = getBatteryStatusIntent()
        return batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    }

    /**
     * 获取电池状态的本地化描述
     */
    fun getBatteryStatusText(): String {
        return getBatteryStatusTextFromIntent(getBatteryStatusIntent())
    }

    /**
     * 获取充电电源类型的本地化描述
     */
    fun getChargingPlugTypeText(): String {
        return getChargingPlugTypeTextFromIntent(getBatteryStatusIntent())
    }

    /**
     * 更新电量消耗速率
     */
    fun updateConsumptionRate(currentLevel: Int) {
        if (!isValidBatteryLevel(currentLevel)) {
            return
        }

        val currentTime = System.currentTimeMillis()
        lastBatteryLevel?.let { lastLevel ->
            lastTimeRecorded?.let { lastTime ->
                val levelDiff = max(lastLevel - currentLevel, 0)
                val timeDiffMinutes = (currentTime - lastTime) / MILLIS_IN_MINUTE.toFloat()

                if (levelDiff > 0 && timeDiffMinutes > 0) {
                    consumptionRatePerMinute = levelDiff / timeDiffMinutes
                }
            }
        }

        lastBatteryLevel = currentLevel
        lastTimeRecorded = currentTime
    }

    /**
     * 估算剩余时间（分钟）
     */
    fun estimateRemainingTimeMinutes(currentLevel: Int): Float? {
        return consumptionRatePerMinute?.takeIf { it > 0 }?.let { rate ->
            currentLevel / rate
        }
    }

    /**
     * 格式化剩余时间
     */
    fun formatRemainingTime(minutes: Float): String {
        val totalMinutes = minutes.toInt().coerceAtLeast(0)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60

        return when {
            hours > 0 -> "${hours}h${mins}m"
            else -> "${mins}m"
        }
    }

    // region Private Methods

    /**
     * 获取电池状态 Intent
     */
    private fun getBatteryStatusIntent(): Intent {
        return context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: Intent()
    }

    /**
     * 获取当前电池电量百分比
     */
    private fun getBatteryLevel(batteryStatus: Intent): Int {
        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, UNKNOWN_VALUE)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, UNKNOWN_VALUE)

        return if (isValidLevelAndScale(level, scale)) {
            (level * 100 / scale.toFloat()).toInt().coerceIn(MIN_VALID_LEVEL, MAX_VALID_LEVEL)
        } else {
            UNKNOWN_VALUE
        }
    }

    /**
     * 获取电池最大刻度值
     */
    private fun getBatteryScale(batteryStatus: Intent): Int {
        return batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, UNKNOWN_VALUE)
    }

    /**
     * 获取电池健康状态（本地化字符串）
     */
    private fun getBatteryHealth(batteryStatus: Intent): String {
        return when (batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, UNKNOWN_VALUE)) {
            BatteryManager.BATTERY_HEALTH_COLD -> context.getString(R.string.overcooling)
            BatteryManager.BATTERY_HEALTH_DEAD -> context.getString(R.string.damage)
            BatteryManager.BATTERY_HEALTH_GOOD -> context.getString(R.string.good)
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> context.getString(R.string.overheat)
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> context.getString(R.string.overvoltage)
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> context.getString(R.string.fault)
            else -> context.getString(R.string.unknown)
        }
    }

    /**
     * 获取电池电压（毫伏）
     */
    private fun getBatteryVoltage(batteryStatus: Intent): Int {
        return batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, UNKNOWN_VALUE)
    }

    /**
     * 获取电池温度（原始值，单位 0.1°C）
     */
    private fun getBatteryTemperature(batteryStatus: Intent): Int {
        return batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, UNKNOWN_VALUE)
    }

    /**
     * 获取电池技术类型
     */
    private fun getBatteryTechnology(batteryStatus: Intent): String {
        return batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            ?: context.getString(R.string.unknown)
    }

    /**
     * 获取电池容量（整合多种方法，单位 mAh）
     */
    private fun getBatteryCapacity(): Int {
        return getBatteryCapacityByReflection()
            ?: getBatteryCapacityByChargeCounter()
            ?: 0
    }

    /**
     * 通过反射获取电池容量
     */
    private fun getBatteryCapacityByReflection(): Int? {
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val constructor = powerProfileClass.getConstructor(Context::class.java)
            val powerProfileInstance = constructor.newInstance(context)

            val method = getBatteryCapacityMethod(powerProfileClass)
            val capacity = invokeBatteryCapacityMethod(method, powerProfileInstance)

            capacity.takeIf { it > 0 }?.toInt()
        } catch (e: Exception) {
            null
        }
    }

    private fun getBatteryCapacityMethod(powerProfileClass: Class<*>): Method {
        return try {
            powerProfileClass.getMethod("getBatteryCapacity")
        } catch (e: NoSuchMethodException) {
            powerProfileClass.getMethod("getAveragePower", String::class.java)
        }
    }

    private fun invokeBatteryCapacityMethod(method: Method, instance: Any): Double {
        return if (method.parameterTypes.isEmpty()) {
            method.invoke(instance) as Double
        } else {
            method.invoke(instance, "battery.capacity") as Double
        }
    }

    /**
     * 通过电荷计数器获取电池容量
     */
    private fun getBatteryCapacityByChargeCounter(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null
        }
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val chargeCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val capacityPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            if (isValidChargeCounterData(chargeCounter, capacityPercent)) {
                ((chargeCounter / capacityPercent) * 100 / MICRO_TO_MILLI_CONVERSION).toInt()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取电池剩余使用时间估算（字符串格式）
     */
    private fun getRemainingTimeEstimate(batteryPercent: Int): String {
        val estimatedMinutes = estimateRemainingTimeMinutes(batteryPercent)
            ?: estimateRemainingUsageTimeSimple(batteryPercent, ASSUMED_HOURLY_DRAIN) * 60

        return formatRemainingTime(estimatedMinutes)
    }

    /**
     * 简单估算剩余使用时间（小时）
     */
    private fun estimateRemainingUsageTimeSimple(
        batteryPercentage: Int,
        assumedDrainPerHour: Float = DEFAULT_HOURLY_DRAIN
    ): Float {
        return if (batteryPercentage <= 0 || assumedDrainPerHour <= 0) {
            0f
        } else {
            batteryPercentage / assumedDrainPerHour
        }
    }

    /**
     * 创建默认电池信息（用于异常情况）
     */
    private fun createDefaultBatteryInfo(): BatteryInfo {
        return BatteryInfo(
            level = UNKNOWN_VALUE,
            scale = UNKNOWN_VALUE,
            health = context.getString(R.string.unknown),
            status = context.getString(R.string.unknown),
            plugged = context.getString(R.string.unknown),
            voltage = UNKNOWN_VALUE,
            temperature = 0f,
            technology = context.getString(R.string.unknown),
            capacity = 0,
            timestamp = System.currentTimeMillis(),
            availableTime = context.getString(R.string.unknown)
        )
    }

    /**
     * 从 Intent 提取电池状态并返回本地化描述字符串
     */
    private fun getBatteryStatusTextFromIntent(batteryStatus: Intent): String {
        return when (batteryStatus.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        )) {
            BatteryManager.BATTERY_STATUS_CHARGING -> context.getString(R.string.battery_status_charging)
            BatteryManager.BATTERY_STATUS_DISCHARGING -> context.getString(R.string.battery_status_discharging)
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> context.getString(R.string.battery_status_not_charging)
            BatteryManager.BATTERY_STATUS_FULL -> context.getString(R.string.battery_status_full)
            else -> context.getString(R.string.unknown)
        }
    }

    /**
     * 从 Intent 提取充电电源类型并返回本地化描述字符串
     */
    private fun getChargingPlugTypeTextFromIntent(batteryStatus: Intent): String {
        return when (batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_USB -> context.getString(R.string.charging_plug_usb)
            BatteryManager.BATTERY_PLUGGED_AC -> context.getString(R.string.charging_plug_ac)
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> context.getString(R.string.charging_plug_wireless)
            BatteryManager.BATTERY_PLUGGED_DOCK -> context.getString(R.string.charging_plug_dock)
            else -> context.getString(R.string.not_charging)
        }
    }

    /**
     * 验证电池电量和刻度是否有效
     */
    private fun isValidLevelAndScale(level: Int, scale: Int): Boolean {
        return level >= MIN_VALID_LEVEL && scale > 0
    }

    /**
     * 验证电池电量是否有效
     */
    private fun isValidBatteryLevel(level: Int): Boolean {
        return level in MIN_VALID_LEVEL..MAX_VALID_LEVEL
    }

    /**
     * 验证电荷计数器数据是否有效
     */
    private fun isValidChargeCounterData(chargeCounter: Long, capacityPercent: Int): Boolean {
        return chargeCounter != Long.MIN_VALUE &&
                capacityPercent != Int.MIN_VALUE &&
                capacityPercent > 0
    }

    // endregion
}