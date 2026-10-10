package com.tidyclean.storagecleaner.base.command


import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.net.toUri
import com.pdffox.adv.AdvertiseSdk

import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone


/**
 * startActivity
 *
 * @param clazz
 */
fun Activity.readyGo(clazz: Class<*>?) {
    val intent = Intent(this, clazz)
    startActivity(intent)
}

fun log(eventName: String, params: Map<String, Any> = emptyMap()) {
    try {
        AdvertiseSdk.logEvent(eventName, params)
    } catch (e: Throwable) {
        Log.w("AdvertiseSdk", "Failed to log event: $eventName", e)
    }
}



fun Activity.openAppSettings(
    context: Context,
    packageName: String? = null,
    requestCode: Int = 0
) {
    try {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            val targetPackageName = packageName ?: context.packageName
            data = Uri.fromParts("package", targetPackageName, null)
            if (requestCode != 0) {
                startActivityForResult(this, requestCode)
            } else {
                startActivity(this)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}


fun getFunctionBundle(details:String,referrer_name:String): Bundle{
    val bundle= Bundle()
    bundle.putString("details",details)
    bundle.putString("referrer_name",referrer_name)
    return bundle

}



@SuppressLint("SimpleDateFormat")
fun getDetailCurrentTime(time:Long): String {
    val date = Date(time)
    val strDateFormat = "yyyy-MM-dd HH:mm:ss"
    val format = SimpleDateFormat(strDateFormat)
    format.timeZone = TimeZone.getDefault()
    return format.format(date)
}

fun dp2px(context: Context, dp: Int): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(),
        context.resources.displayMetrics
    ).toInt()
}


fun Activity.navigateToAppSettings() {
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:${packageName}".toUri()
        startActivity(this)
    }
}

/**
 * startActivity with bundle
 *
 * @param clazz
 * @param bundle
 */
fun Activity.readyGo(clazz: Class<*>?, bundle: Bundle?) {
    val intent = Intent(this, clazz)
    if (null != bundle) {
        intent.putExtras(bundle)
    }
    startActivity(intent)
}

fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    // Android 5.0 (API 21) 及以上可用
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}



/**
 * startActivity then finish
 *
 * @param clazz
 */
fun Activity.readyGoThenKill(clazz: Class<*>?) {
    val intent = Intent(this, clazz)
    startActivity(intent)
    finish()
}

/**
 * startActivity with bundle then finish
 *
 * @param clazz
 * @param bundle
 */
fun Activity.readyGoThenKill(clazz: Class<*>?, bundle: Bundle?) {
    val intent = Intent(this, clazz)
    if (null != bundle) {
        intent.putExtras(bundle)
    }
    startActivity(intent)
    finish()
}




fun Activity.hasStoragePermission(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return Environment.isExternalStorageManager()
    } else {
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) === PackageManager.PERMISSION_GRANTED
    }

}
const val REQUEST_CODE_MANAGE_STORAGE = 1002

const val REQUEST_CODE_STORAGE_PERMISSION = 1001
@SuppressLint("CheckResult")
fun Activity.checkAndRequestPermissions(
    permission: String,
    toTransparent: () -> Unit
) {
    when (permission) {
        Manifest.permission.READ_EXTERNAL_STORAGE -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    // 方法1：使用标准文件管理权限 Intent
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
                } catch (e: ActivityNotFoundException) {
                    try {
                        // 方法2：备用 Intent（某些厂商设备）
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:$packageName")
                        startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
                    } catch (ex: ActivityNotFoundException) {
                        try {
                            // 方法3：跳转到应用详情页面，用户可手动开启权限
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.parse("package:$packageName")
                            startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
                        } catch (ex2: ActivityNotFoundException) {
                            // 方法4：最终回退方案，跳转到应用列表
                            val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
                            startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE)
                        }
                    }
                }
                toTransparent()
            } else {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    REQUEST_CODE_STORAGE_PERMISSION
                )
            }
        }
    }
}











 fun Activity.openAppInfoSettings(pkgName: String?) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = ("package:$pkgName").toUri()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (e: java.lang.Exception) {
        // 最终回退方案
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: java.lang.Exception) {
        }
    }
}





const val REQUEST_CODE_NOTIFICATION = 1003




fun getVersionName(context: Context): String {
    var manager = context.applicationContext.packageManager
    var code = ""
    try {
        var info = manager.getPackageInfo(context.applicationContext.packageName, 0)
        code = info.versionName!!
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
    }
    return code
}


// 扩展函数实现防重复点击
fun View.safeClick(interval: Long = 1000, action: () -> Unit) {
    var lastClickTime = 0L
    setOnClickListener {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > interval) {
            lastClickTime = currentTime
            action()
        }
    }
}


fun Activity.rateUS(){
    try {
        val uri = ("https://play.google.com/store/apps/details?id=" +
                "" + packageName).toUri()
        val intent = Intent(Intent.ACTION_VIEW
            , uri)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        // 处理异常，例如显示错误消息
    }
}

fun checkNotificationPermission(context: Context): Boolean {
    val notificationManager = NotificationManagerCompat.from(context)
    val areNotificationsEnabled = notificationManager.areNotificationsEnabled()
    // 对于Android 13+，还需要检查是否明确授予了权限
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val hasNotificationPermission =
            checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        return areNotificationsEnabled && hasNotificationPermission
    }
    return areNotificationsEnabled
}


































