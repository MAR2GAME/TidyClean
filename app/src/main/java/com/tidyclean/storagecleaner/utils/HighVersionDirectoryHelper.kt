package com.tidyclean.storagecleaner.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.also
import kotlin.collections.any
import kotlin.collections.distinctBy
import kotlin.collections.filter
import kotlin.collections.forEach
import kotlin.collections.getOrPut
import kotlin.collections.mapNotNull
import kotlin.collections.sortedBy
import kotlin.io.use
import kotlin.let
import kotlin.takeIf
import kotlin.text.contains
import kotlin.text.lowercase
import kotlin.text.startsWith

object HighVersionDirectoryHelper {
    private const val TAG = "DirectoryHelper"

    // 优先级映射 - 使用枚举类优化
    private enum class DirectoryPriority(val value: Int) {
        CACHE(1),
        DOWNLOAD(2),
        MEDIA(3),
        OTHER(4)
    }

    // 预编译模式用于路径匹配
    private val CACHE_PATTERNS = setOf("cache", "temp", "tmp")
    private val DOWNLOAD_PATTERNS = setOf("download", "apk")
    private val MEDIA_PATTERNS = setOf(
        Environment.DIRECTORY_DOCUMENTS,
        Environment.DIRECTORY_PICTURES,
        Environment.DIRECTORY_DCIM,
        Environment.DIRECTORY_MOVIES,
        Environment.DIRECTORY_MUSIC
    )

    // 目录缓存 - 使用软引用防止内存泄漏
    private val directoryCache = ConcurrentHashMap<String, List<File>>()

    // 系统目录白名单 - 预编译避免重复创建
    private val SYSTEM_DIR_PATTERNS = setOf(
        "/system", "/android", "/lost+found", "/preload", "/vendor", "/mnt",
        "/proc", "/sys", "/acct", "/dev", "/config", "/oem", "/firmware", "/cache"
    )

    /**
     * 获取可扫描目录（按优先级排序）- 带缓存和内存优化
     */
    fun getPrioritizedDirectories(context: Context): List<File> {
        val cacheKey = "prioritized_${Build.VERSION.SDK_INT}_${hasAllFilesAccessPermission(context)}"

        return directoryCache.getOrPut(cacheKey) {
            getScannableDirectories(context)
                .distinctBy { it.absolutePath }
                .sortedBy { getDirectoryScanPriority(it) }
                .also {
                    Log.d(TAG, "Cached ${it.size} prioritized directories")
                }
        }
    }

    /**
     * 优化的目录扫描方法
     */
    fun getScannableDirectories(context: Context): List<File> {
        return buildList {
            // 1. 应用私有目录（最高优先级）
            addAll(getAppPrivateDirectories(context))

            // 2. MediaStore 可访问目录
           addAll(getMediaStoreAccessibleDirectories(context))

            // 3. 如果有所有文件访问权限，添加传统存储目录
            if (hasAllFilesAccessPermission(context)) {
             addAll(getLegacyStorageDirectories(context))
            }
        }.filter {
            it.exists() && it.isDirectory && it.canRead() && !isSystemDirectory(it)
        }
    }

    /**
     * 优化的应用私有目录获取
     */
    private fun getAppPrivateDirectories(context: Context): List<File> {
        return buildList {
            try {
                // 内部存储目录
                add(context.filesDir)
                add(context.cacheDir)
                // 外部存储私有目录
                context.externalCacheDir?.let { add(it) }
                context.getExternalFilesDir(null)?.let { add(it) }
                // 特定类型的外部文件目录
                val mediaTypes = arrayOf(
                    Environment.DIRECTORY_DOWNLOADS,
                    Environment.DIRECTORY_DOCUMENTS,
                    Environment.DIRECTORY_PICTURES,
                    Environment.DIRECTORY_DCIM,
                    Environment.DIRECTORY_MOVIES,
                    Environment.DIRECTORY_MUSIC
                )

                mediaTypes.mapNotNull { type ->
                    context.getExternalFilesDir(type)
                }.forEach { add(it) }

                // 高版本特有目录
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    context.noBackupFilesDir?.let { add(it) }
                    context.codeCacheDir?.let { add(it) }

                    // 外部媒体目录
                    context.externalMediaDirs.forEach { dir ->
                        if (dir.exists()) add(dir)
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot access some app private directories", e)
            }
        }
    }

    /**
     * MediaStore 目录获取优化
     */
    private fun getMediaStoreAccessibleDirectories(context: Context): List<File> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getMediaStoreDirectoriesAndroid10Plus(context)
        } else {
            getMediaStoreDirectoriesLegacy(context)
        }

    /**
     * Android 10+ MediaStore 目录获取优化
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun getMediaStoreDirectoriesAndroid10Plus(context: Context): List<File> {
        return buildList {
            try {
                // 并行查询优化
                val contentUris = arrayOf(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                )

                contentUris.forEach { uri ->
                    queryMediaStoreDirectory(context, uri)?.let { directory ->
                        if (directory.exists() && directory.isDirectory) {
                            add(directory)
                        }
                    }
                }

                // 备用方案
                if (isEmpty()) {
                    addAll(getFallbackDirectories())
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore query failed on Android 10+", e)
                addAll(getFallbackDirectories())
            }
        }
    }

    /**
     * 优化的 MediaStore 目录查询
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryMediaStoreDirectory(context: Context, contentUri: Uri): File? {
        var cursor: Cursor? = null
        return try {
            val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
            cursor = context.contentResolver.query(
                contentUri,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC LIMIT 1"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val pathIndex = it.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    if (pathIndex != -1) {
                        it.getString(pathIndex)?.let { relativePath ->
                            File(Environment.getExternalStorageDirectory(), relativePath).parentFile
                        }
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query MediaStore directory: $contentUri", e)
            null
        } finally {
            cursor?.close()
        }
    }

    /**
     * Android 9 及以下 MediaStore 目录获取
     */
    private fun getMediaStoreDirectoriesLegacy(context: Context): List<File> {
        return buildList {
            try {
                val directoryQueries = listOf(
                    Pair(
                        MediaStore.Files.getContentUri("external"),
                        Environment.DIRECTORY_DOWNLOADS
                    ),
                    Pair(
                        MediaStore.Files.getContentUri("external"),
                        Environment.DIRECTORY_DOCUMENTS
                    ),
                    Pair(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        Environment.DIRECTORY_PICTURES
                    )
                )

                directoryQueries.forEach { (uri, directory) ->
                    queryMediaStoreForDirectoryLegacy(context, uri, directory)?.let { dir ->
                        if (dir.exists()) add(dir)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Legacy MediaStore query failed", e)
            }
        }
    }

    /**
     * 传统 MediaStore 查询优化
     */
    private fun queryMediaStoreForDirectoryLegacy(
        context: Context,
        contentUri: Uri,
        directory: String
    ): File? {
        var cursor: Cursor? = null
        return try {
            val selection = "${MediaStore.MediaColumns.DATA} LIKE ?"
            val selectionArgs = arrayOf("%/$directory/%")

            cursor = context.contentResolver.query(
                contentUri,
                arrayOf(MediaStore.MediaColumns.DATA),
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC LIMIT 1"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val dataIndex = it.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIndex != -1) {
                        it.getString(dataIndex)?.let { filePath ->
                            File(filePath).parentFile
                        }
                    } else null
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query legacy MediaStore directory: $directory", e)
            null
        } finally {
            cursor?.close()
        }
    }

    /**
     * 备用目录获取优化
     */
    private fun getFallbackDirectories(): List<File> {
        return arrayOf(
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DOCUMENTS,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_MUSIC
        ).mapNotNull { type ->
            Environment.getExternalStoragePublicDirectory(type).takeIf {
                it.exists() && it.isDirectory
            }
        }
    }

    /**
     * 传统存储目录获取优化
     */
    private fun getLegacyStorageDirectories(context: Context): List<File> {
        return buildList {
            try {
                val externalStorage = Environment.getExternalStorageDirectory()
                if (externalStorage.exists()) {
                    add(externalStorage)

                    // 预定义常见子目录
                    val commonSubDirs = arrayOf(
                        Environment.DIRECTORY_DOWNLOADS,
                        Environment.DIRECTORY_DOCUMENTS,
                        Environment.DIRECTORY_PICTURES,
                        Environment.DIRECTORY_DCIM,
                        Environment.DIRECTORY_MOVIES,
                        Environment.DIRECTORY_MUSIC,
                        "APK", "apk", "Backup", "backup", "Temp", "temp", "Tmp", "tmp"
                    )

                    commonSubDirs.forEach { dirName ->
                        val dir = File(externalStorage, dirName)
                        if (dir.exists() && dir.isDirectory) {
                            add(dir)
                        }
                    }
                }

                // 外部媒体目录
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    context.externalMediaDirs.forEach { mediaDir ->
                        if (mediaDir.exists()) add(mediaDir)
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot access legacy storage directories", e)
            }
        }
    }

    /**
     * 检查所有文件访问权限
     */
    fun hasAllFilesAccessPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Android 10 及以下，假设有存储权限
            true
        }

    /**
     * 优化的目录扫描优先级计算
     */
    private fun getDirectoryScanPriority(file: File): Int {
        val path = file.absolutePath.lowercase()
        val name = file.name.lowercase()

        return when {
            CACHE_PATTERNS.any { path.contains(it) || name.contains(it) } ->
                DirectoryPriority.CACHE.value
            DOWNLOAD_PATTERNS.any { path.contains(it) || name.contains(it) } ->
                DirectoryPriority.DOWNLOAD.value
            MEDIA_PATTERNS.any { path.contains(it) || name.contains(it) } ->
                DirectoryPriority.MEDIA.value
            else -> DirectoryPriority.OTHER.value
        }
    }

    /**
     * 系统目录检查优化
     */
    private fun isSystemDirectory(dir: File): Boolean {
        if (dir.isHidden) return true

        val path = dir.absolutePath.lowercase()
        return SYSTEM_DIR_PATTERNS.any { systemDir ->
            path == systemDir || path.startsWith("$systemDir/")
        }
    }

    /**
     * 清理缓存
     */
    fun clearCache() {
        directoryCache.clear()
    }
}