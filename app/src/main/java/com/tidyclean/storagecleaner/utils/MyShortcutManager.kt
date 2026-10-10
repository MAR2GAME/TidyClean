package com.tidyclean.storagecleaner.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.content.getSystemService

import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.base.config.AppConfig
import com.tidyclean.storagecleaner.ui.SplashActivity

import kotlin.apply
import kotlin.collections.map
import kotlin.jvm.java
import kotlin.run

object MyShortcutManager {

    // 使用密封类定义所有可用的快捷方式
    sealed class ShortcutData(
        val id: String,
        @StringRes val labelRes: Int,
        @DrawableRes val iconRes: Int,
        @StringRes val nextTagRes: Int
    ) {
        object Clean : ShortcutData(
            id = "shortcut_clean",
            labelRes = R.string.remove_junk,
            iconRes = R.mipmap.ic_shortcut_clean,
            nextTagRes = R.string.remove_junk
        )

        object Process : ShortcutData(
            id = "shortcut_app_usage",
            labelRes = R.string.app_usage,
            iconRes = R.mipmap.ic_app_usage_samll,
            nextTagRes = R.string.app_usage
        )

        object Screenshot : ShortcutData(
            id = "shortcut_battery_info",
            labelRes = R.string.battery_info,
            iconRes = R.mipmap.ic_battry_info_small,
            nextTagRes = R.string.battery_info
        )
        object Uninstall : ShortcutData(
            id = "shortcut_uninstall",
            labelRes = R.string.uninstall,
            iconRes = R.mipmap.ic_short_uninstall,
            nextTagRes = R.string.uninstall
        )

        companion object {
            val allShortcuts = listOf(Clean, Process, Screenshot, Uninstall)
        }
    }

    // 初始化动态快捷方式
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun initDynamicShortcuts(context: Context) {
        try {
            val shortcutManager = context.getSystemService<ShortcutManager>() ?: run {
                return
            }
            val shortcuts = buildShortcuts(context)
            shortcutManager.dynamicShortcuts = shortcuts
        } catch (e: Exception) {
        }
    }

    // 创建单个快捷方式
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun createShortcut(context: Context, shortcutData: ShortcutData): ShortcutInfo {
        return ShortcutInfo.Builder(context, shortcutData.id)
            .setShortLabel(context.getString(shortcutData.labelRes))
            .setLongLabel(context.getString(shortcutData.labelRes))
            .setIcon(Icon.createWithResource(context, shortcutData.iconRes))
            .setIntent(createShortcutIntent(context, shortcutData))
            .build()
    }

    // 批量创建所有快捷方式
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun buildShortcuts(context: Context): List<ShortcutInfo> {
        return ShortcutData.allShortcuts.map { createShortcut(context, it) }
    }

    // 更新特定快捷方式
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun updateShortcut(context: Context, shortcutData: ShortcutData) {
        try {
            val shortcutManager = context.getSystemService<ShortcutManager>() ?: return
            val shortcut = createShortcut(context, shortcutData)
            shortcutManager.updateShortcuts(listOf(shortcut))
        } catch (e: Exception) {

        }
    }

    // 删除快捷方式
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun removeShortcuts(context: Context, shortcutIds: List<String>) {
        try {
            val shortcutManager = context.getSystemService<ShortcutManager>() ?: return
            shortcutManager.removeDynamicShortcuts(shortcutIds)
        } catch (e: Exception) {

        }
    }

    // 删除所有动态快捷方式
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun removeAllShortcuts(context: Context) {
        try {
            val shortcutManager = context.getSystemService<ShortcutManager>() ?: return
            val shortcutIds = ShortcutData.allShortcuts.map { it.id }
            shortcutManager.removeDynamicShortcuts(shortcutIds)
        } catch (e: Exception) {

        }
    }

    // 检查是否支持快捷方式
    fun isShortcutSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1
    }

    // 私有方法
    private fun createShortcutIntent(
        context: Context,
        shortcutData: ShortcutData
    ): Intent {
        return Intent(context, SplashActivity::class.java).apply {
           action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
           putExtra(AppConfig.NEXT_TAG, context.getString(shortcutData.nextTagRes))
          putExtra("AppOpenFrom", "shortcut")
           putExtra("ShortcutId", shortcutData.id)
        }
    }

}
