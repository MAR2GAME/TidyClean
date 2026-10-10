package com.tidyclean.storagecleaner.utils

import android.content.Context
import android.content.SharedPreferences



import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import androidx.core.content.edit
import com.tidyclean.storagecleaner.base.MyApp

import kotlin.text.toDouble


/**
 * SharedPreferences 工具类
 * 支持基本数据类型存储和属性委托
 */
object AppPrefsUtils {
   val sp: SharedPreferences by lazy {
       MyApp.instance!!.getSharedPreferences(
           "tool_sp",
           Context.MODE_PRIVATE
       )
   }


    fun removeByKey(key: String) {
        sp.edit { remove(key) }
    }
    /**
     * 获取数据（带默认值）
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean =
        sp.getBoolean(key, defaultValue)

    fun getString(key: String, defaultValue: String = ""): String =
        sp.getString(key, defaultValue) ?: defaultValue

    fun getLong(key: String, defaultValue: Long = 0L): Long =
        sp.getLong(key, defaultValue)

    fun getInt(key: String, defaultValue: Int = 0): Int =
        sp.getInt(key, defaultValue)

    fun getFloat(key: String, defaultValue: Float = 0f): Float =
        sp.getFloat(key, defaultValue)

    fun getDouble(key: String, defaultValue: Double = 0.0): Double {
        return try {
            sp.getString(key, null)?.toDouble() ?: defaultValue
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }


    class PreferenceDelegate<T>(
        private val key: String,
        private val defaultValue: T
    ) : ReadWriteProperty<Any?, T> {

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return when (defaultValue) {
                is Int ->getInt(key, defaultValue) as T
                is Long -> getLong(key, defaultValue) as T
                is Boolean -> getBoolean(key, defaultValue) as T
                is Float ->getFloat(key, defaultValue) as T
                is String -> getString(key, defaultValue) as T
                is Double -> getDouble(key, defaultValue) as T
                else -> throw kotlin.IllegalArgumentException("Unsupported type.")
            }
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            when (value) {
                is Int -> commitInt(key, value)
                is Long ->commitLong(key, value)
                is Boolean -> commitBoolean(key, value)
                is Float -> commitFloat(key, value)
                is String -> commitString(key, value)
                is Double ->commitDouble(key, value)
                else -> throw kotlin.IllegalArgumentException("Unsupported type.")
            }
        }
    }
    // 同步提交方法（仅在需要立即持久化时使用）
    fun commitInt(key: String, value: Int) = sp.edit(commit = true) { putInt(key, value) }
    fun commitLong(key: String, value: Long) = sp.edit(commit = true) { putLong(key, value) }
    fun commitBoolean(key: String, value: Boolean) = sp.edit(commit = true) {
        putBoolean(
            key,
            value
        )
    }
    fun commitFloat(key: String, value: Float) = sp.edit(commit = true) { putFloat(key, value) }
   fun commitString(key: String, value: String) = sp.edit(commit = true) { putString(key, value) }
    fun commitDouble(key: String, value: Double) = sp.edit(commit = true) {
        putString(
            key,
            value.toString()
        )
    }
}


