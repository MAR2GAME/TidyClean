package com.tidyclean.storagecleaner.utils

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.system.Os.remove
import androidx.core.content.edit

/**
 * 使用情况访问权限工具类（PACKAGE_USAGE_STATS）
 */
object UsageStatsHelper {

    private const val PREF_NAME = "usage_stats_pref"
    private const val KEY_HAS_NAVIGATED = "has_navigated_to_settings"

    /**
     * 检查是否已获得使用情况访问权限
     */
    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            AppOpsManager.MODE_ALLOWED
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 跳转到系统的使用情况访问设置页
     * @return 是否成功跳转
     */
    fun navigateToUsageAccessSettings(context: Context): Boolean {
        return try {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            true
        } catch (e: Exception) {
            // 降级：打开系统设置首页
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    /**
     * 标记已经跳转过设置页
     */
    fun markNavigated() {
        AppPrefsUtils.commitBoolean(KEY_HAS_NAVIGATED,true)
    }

    /**
     * 检查是否已经跳转过设置页
     */
    fun hasNavigatedBefore(): Boolean {
        return AppPrefsUtils.getBoolean(KEY_HAS_NAVIGATED,false)
    }
    /**
     * 重置跳转标记（权限授权成功后调用，可选）
     */
    fun resetNavigatedFlag() {
        AppPrefsUtils.removeByKey(KEY_HAS_NAVIGATED)
    }

}