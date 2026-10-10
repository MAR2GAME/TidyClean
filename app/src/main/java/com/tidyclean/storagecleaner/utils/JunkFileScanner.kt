package com.tidyclean.storagecleaner.utils

import android.content.Context
import android.util.Log
import com.tidyclean.storagecleaner.bean.JunkChildBean
import com.tidyclean.storagecleaner.bean.JunkMotherBean


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatten
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.apply
import kotlin.collections.addAll
import kotlin.collections.any
import kotlin.collections.chunked
import kotlin.collections.flatten
import kotlin.collections.forEach
import kotlin.collections.forEachIndexed
import kotlin.collections.getOrNull
import kotlin.collections.getOrPut
import kotlin.collections.mapIndexed
import kotlin.collections.partition
import kotlin.collections.set
import kotlin.collections.sumOf
import kotlin.collections.take
import kotlin.collections.toMutableSet
import kotlin.collections.toSet
import kotlin.io.extension
import kotlin.io.walk
import kotlin.sequences.forEach
import kotlin.text.clear
import kotlin.text.contains
import kotlin.text.endsWith
import kotlin.text.equals
import kotlin.text.indexOf
import kotlin.text.lowercase
import kotlin.text.matches
import kotlin.text.startsWith
import kotlin.text.substring
import kotlin.time.Duration.Companion.seconds
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class JunkFileScanner(
    private val context: Context,
    private val junkMotherBeans: MutableList<JunkMotherBean>
) {
    private val scanScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isScanning = AtomicBoolean(false)
    private val scannedFilesCount = AtomicInteger(0)

    // 缓存优化
    private val directorySizeCache = ConcurrentHashMap<String, Long>()
    private val scannedPathsCache = ConcurrentHashMap<String, Boolean>()
    private val adPatternCache = ConcurrentHashMap<String, Boolean>()

    companion object {
        private const val TAG = "JunkFileScanner"
        private const val MAX_SCAN_DEPTH = 3
        private const val MAX_FILES_PER_CATEGORY = 500
        private const val MIN_FILE_SIZE = 1024L // 降低最小文件大小限制
        private const val MIN_AD_FILE_SIZE = 1024L // 广告文件更低的大小限制
        private const val BATCH_SIZE = 100
        private const val PARALLEL_SCAN_LIMIT = 4

        // 临时文件扩展名
        private val TEMP_EXTENSIONS = setOf("tmp", "temp", "log", "cache", "bak", "backup")

        // 残留文件模式
        private val RESIDUAL_PATTERNS = setOf(
            "cache", "temp", "log", "backup", "trash", "trashes",
            ".temp", ".tmp", ".cache", "tmp_", "temp_"
        )

        // 扩展广告模式 - 包含更多常见的广告标识
        private val AD_PATTERNS = setOf(
            "ad", "ads", "advert", "advertisement", "adcache",
            "admob", "mopub", "facebookad", "unityad", "advertising",
            "adsense", "gad", "iad", "adcolony", "vungle", "chartboost",
            "ironsource", "applovin", "tapjoy", "adwhirl", "flurryad",
            "amazonad", "heyzap", "inmobi", "revmob", "startapp",
            "leadbolt", "millennial", "burstly", "supersonic", "ogury"
        )

        // 广告目录模式
        private val AD_DIR_PATTERNS = setOf(
            "adcache", "adcaches", "advertising", "ad_data",
            "ads_data", "ad_images", "ad_videos", "ad_logs",
            "ads_cache", "advertising_cache", "ad_sdk", "ads_sdk",
            "ad_resources", "ads_resources", "ad_assets", "ads_assets"
        )

        // 广告文件正则模式
        private val AD_FILE_REGEX_PATTERNS = listOf(
            Regex(".*[._\\-]ad[._\\-].*", RegexOption.IGNORE_CASE),
            Regex(".*ads?\\d*\\.(cache|data|tmp|log|db)$", RegexOption.IGNORE_CASE),
            Regex("^ad_.*", RegexOption.IGNORE_CASE),
            Regex(".*_ad_.*", RegexOption.IGNORE_CASE),
            Regex(".*\\.ad\\..*", RegexOption.IGNORE_CASE),
            Regex("ads?_.*\\.(jpg|png|gif|mp4|webp)$", RegexOption.IGNORE_CASE)
        )

        // 正则预编译
        private val HIDDEN_DIR_PATTERN = Regex("^\\..*")

        // APK文件相关的模式
        private val APK_RELATED_PATTERNS = setOf(
            ".apk", ".apk.", "apk_", "_apk", "apk.", ".apk"
        )

        // 系统目录模式
        private val SYSTEM_DIR_PATTERNS = setOf(
            "/system", "/android", "/lost+found", "/preload", "/vendor", "/mnt",
            "/proc", "/sys", "/acct", "/dev", "/config", "/oem", "/firmware", "/cache"
        )

        // 已知的应用数据目录
        private val APP_DATA_PATTERNS = setOf(
            "/data/data/", "/user/", "/app_data/", "/app_files/",
            "/files/", "/cache/", "/code_cache/"
        )
    }




    /**
     * 优化的扫描方法
     */
    suspend fun scanJunkFiles(): Result<MutableList<JunkMotherBean>> = withContext(Dispatchers.IO) {
        if (!isScanning.compareAndSet(false, true)) {
            return@withContext Result.failure(kotlin.IllegalStateException("Scan already in progress"))
        }
        try {
            resetScanState()
            Log.d(TAG, "Starting junk file scan with categories: ${junkMotherBeans.size}")
            // 并行扫描优化 - 使用结构化并发
            val results = coroutineScope {
                val scanJobs = junkMotherBeans.mapIndexed { index, bean ->
                    async {
                        val junkFiles = when (index) {
                            0 -> scanCategory(index) { scanExpiredApkJunk() }
                            1 -> scanCategory(index) { scanResidualJunk() }
                            2 -> scanCategory(index) { scanAdvertisementJunk() }
                            3 -> scanCategory(index) { scanTemporaryFiles() }
                            else -> emptySet()
                        }
                        Pair(index, junkFiles)
                    }
                }
                scanJobs.awaitAll()
            }
            // 批量更新结果
            results.forEach { (index, junkFiles) ->
                junkMotherBeans.getOrNull(index)?.apply {
                    junkChildrenItems.clear()
                    junkChildrenItems.addAll(junkFiles)
                    size = junkChildrenItems.sumOf { it.size }
                }
            }
            Log.d(TAG, "Scan completed, total files found: ${scannedFilesCount.get()}")
            Result.success(junkMotherBeans)
        } catch (e: Exception) {
            Log.e(TAG, "Scan failed", e)
            Result.failure(e)
        } finally {
            isScanning.set(false)
            cleanupResources()
        }
    }
    /**
     * 分类扫描包装器 - 超时和错误处理
     */
    private suspend fun scanCategory(index: Int, scanner: suspend () -> Set<JunkChildBean>): Set<JunkChildBean> {
        return try {
            withTimeout(20.seconds) { // 增加超时时间
                scanner()
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Category $index scan timeout")
            emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "Category $index scan failed", e)
            emptySet()
        }
    }

    /**
     * 临时文件扫描优化
     */
    private suspend fun scanTemporaryFiles(): Set<JunkChildBean> = scanFilesFlow(
        fileFilter = { file ->
            file.isFile && file.length() >= MIN_FILE_SIZE &&
                    (TEMP_EXTENSIONS.any { file.extension.equals(it, ignoreCase = true) } ||
                            file.name.contains(".tmp", ignoreCase = true) ||
                            file.name.contains(".temp", ignoreCase = true) ||
                            file.name.startsWith("temp_", ignoreCase = true) ||
                            file.name.startsWith("tmp_", ignoreCase = true))
        },
        categoryName = "临时文件",
        minSize = MIN_FILE_SIZE
    ).first()

    /**
     * 残留文件扫描优化 - 同时扫描文件和目录
     */
    private suspend fun scanResidualJunk(): Set<JunkChildBean> = coroutineScope {
        val results = listOf(
            async { scanResidualFiles() },
            async { scanResidualDirectories() }
        ).awaitAll()

        results.flatten().toSet()
    }

    private suspend fun scanResidualFiles(): Set<JunkChildBean> = scanFilesFlow(
        fileFilter = { file ->
            file.isFile && file.length() >= MIN_FILE_SIZE &&
                    (RESIDUAL_PATTERNS.any { file.name.contains(it, ignoreCase = true) } ||
                            file.name.matches(
                                Regex(
                                    ".*\\.(cache|temp|tmp|bak|backup)\\d*$",
                                    RegexOption.IGNORE_CASE
                                )
                            ))
        },
        categoryName = "残留文件",
        minSize = MIN_FILE_SIZE
    ).first()

    private suspend fun scanResidualDirectories(): Set<JunkChildBean> = scanFilesFlow(
        fileFilter = { file ->
            file.isDirectory &&
                    RESIDUAL_PATTERNS.any { file.name.contains(it, ignoreCase = true) } &&
                    calculateDirectorySizeOptimized(file) >= MIN_FILE_SIZE
        },
        categoryName = "残留目录",
        minSize = MIN_FILE_SIZE
    ).first()

    /**
     * 广告文件扫描优化 - 综合扫描
     */
    private suspend fun scanAdvertisementJunk(): Set<JunkChildBean> = coroutineScope {
        Log.d(TAG, "Starting advertisement junk scan")
        val results = listOf(
            async { scanAdFiles() },
            async { scanAdDirectories() },
            async { scanAdCacheFiles() }
        ).awaitAll()
        val allResults = results.flatten().toMutableSet()
        Log.d(TAG, "Advertisement scan completed, found ${allResults.size} items")

        // 调试输出前10个结果
        allResults.take(10).forEachIndexed { index, bean ->
            Log.d(TAG, "Ad item $index: ${bean.name}, path: ${bean.path}, size: ${bean.size}")
        }

        allResults
    }

    private suspend fun scanAdFiles(): Set<JunkChildBean> = scanFilesFlow(
        fileFilter = { file ->
            if (!file.isFile) return@scanFilesFlow false

            val sizeOk = file.length() >= MIN_AD_FILE_SIZE
            if (!sizeOk) return@scanFilesFlow false

            val isAdFile = isAdvertisementFile(file)
            if (isAdFile) {
                Log.v(TAG, "Found ad file: ${file.name}, path: ${file.path}, size: ${file.length()}")
            }

            isAdFile
        },
        categoryName = "广告文件",
        minSize = MIN_AD_FILE_SIZE
    ).first()

    private suspend fun scanAdDirectories(): Set<JunkChildBean> = scanFilesFlow(
        fileFilter = { file ->
            if (!file.isDirectory) return@scanFilesFlow false

            val isAdDir = isAdvertisementDirectory(file)
            if (!isAdDir) return@scanFilesFlow false

            val dirSize = calculateDirectorySizeOptimized(file)
            val sizeOk = dirSize >= MIN_AD_FILE_SIZE

            if (isAdDir && sizeOk) {
                Log.v(TAG, "Found ad directory: ${file.name}, path: ${file.path}, size: $dirSize")
            }

            isAdDir && sizeOk
        },
        categoryName = "广告目录",
        minSize = MIN_AD_FILE_SIZE
    ).first()

    private suspend fun scanAdCacheFiles(): Set<JunkChildBean> = scanFilesFlow(
        fileFilter = { file ->
            if (!file.isFile) return@scanFilesFlow false

            val sizeOk = file.length() >= MIN_AD_FILE_SIZE
            if (!sizeOk) return@scanFilesFlow false

            // 专门扫描广告缓存文件
            val name = file.name.lowercase()
            val path = file.path.lowercase()

            // 检查是否是广告SDK的缓存文件
            val isAdCache = (name.contains("ad") && (name.endsWith(".cache") ||
                    name.endsWith(".cache.db") || name.contains("adcache"))) ||
                    (path.contains("/adcache/") && file.extension.equals("cache", ignoreCase = true)) ||
                    (name.matches(Regex(".*_ad_.*\\.cache$", RegexOption.IGNORE_CASE)))

            isAdCache
        },
        categoryName = "广告缓存",
        minSize = MIN_AD_FILE_SIZE
    ).first()

    /**
     * 过期APK扫描优化
     */
    private suspend fun scanExpiredApkJunk(): Set<JunkChildBean> = coroutineScope {
        val results = listOf(
            async { scanExpiredApkFiles() },
            async { scanApkCacheDirectories() },
            async { scanDuplicateApkFiles() }
        ).awaitAll()
        results.flatten().toSet()
    }
    private suspend fun scanDuplicateApkFiles(): Set<JunkChildBean> = scanFilesFlow(
        fileFilter = { file ->
            if (!file.isFile) return@scanFilesFlow false

            val name = file.name.lowercase()
            if (!name.endsWith(".apk")) return@scanFilesFlow false

            val isDuplicate = (
                    name.contains("(1)") ||
                            name.contains("(2)") ||
                            name.contains("copy") ||
                            name.contains("副本") ||
                            name.contains("duplicate") ||
                            name.contains("backup") && name.contains(".apk") ||
                            name.matches(
                                Regex(
                                    ".*\\s\\(\\d+\\).*\\.apk$",
                                    RegexOption.IGNORE_CASE
                                )
                            ) ||
                            name.matches(Regex(".*_\\d+\\.apk$", RegexOption.IGNORE_CASE))
                    )

            if (isDuplicate) {
                val sizeOk = file.length() >= MIN_FILE_SIZE
                return@scanFilesFlow sizeOk
            }

            false
        },
        categoryName = "重复APK文件",
        minSize =MIN_FILE_SIZE
    ).first()



    private suspend fun scanExpiredApkFiles(): Set<JunkChildBean> = scanFilesFlow(
        fileFilter = { file ->
            if (!file.isFile) return@scanFilesFlow false

            val isApkFile = file.extension.equals("apk", ignoreCase = true)
            if (!isApkFile) return@scanFilesFlow false

            if (isSystemApk(file)) {
                Log.v(TAG, "跳过系统APK: ${file.name}")
                return@scanFilesFlow false
            }

//            val isExpired = isExpiredApk(file)
//            if (!isExpired) {
//                return@scanFilesFlow false
//            }
            val sizeOk = file.length() >= MIN_FILE_SIZE
            if (sizeOk) {
                Log.v(TAG, "发现过期APK: ${file.name}, 大小: ${file.length() / 1024 / 1024}MB")
            }
            sizeOk
        },
        categoryName = "过期APK文件",
        minSize = MIN_FILE_SIZE,
    ).first()


//    private suspend fun scanExpiredApkFiles(): Set<JunkChildBean> = scanFilesFlow(
//        fileFilter = { file ->
//            file.extension.equals("apk", ignoreCase = true) &&
//                    !isSystemApk(file) &&
//                    //isExpiredApk(file) &&
//                    file.length() >= MIN_FILE_SIZE
//        },
//        categoryName = "过期APK文件",
//        minSize = MIN_FILE_SIZE
//    ).first()

    private suspend fun scanApkCacheDirectories(): Set<JunkChildBean> = scanFilesFlow(
        fileFilter = { file ->
            if (!file.isDirectory) return@scanFilesFlow false

            // 扫描APK相关的缓存目录
            val name = file.name.lowercase()
            val isApkRelated = APK_RELATED_PATTERNS.any { name.contains(it) } ||
                    name.contains("apk") && (name.contains("cache") ||
                    name.contains("temp") || name.contains("download"))

            if (!isApkRelated) return@scanFilesFlow false

            val dirSize = calculateDirectorySizeOptimized(file)
            val sizeOk = dirSize >= MIN_FILE_SIZE

            if (isApkRelated && sizeOk) {
                Log.v(TAG, "Found APK cache directory: ${file.name}, size: $dirSize")
            }

            isApkRelated && sizeOk
        },
        categoryName = "APK缓存目录",
        minSize = MIN_FILE_SIZE
    ).first()

    /**
     * 广告文件检查方法
     */
    private fun isAdvertisementFile(file: File): Boolean {
        val cacheKey = "file_${file.absolutePath.hashCode()}"
        return adPatternCache.getOrPut(cacheKey) {
            val name = file.name.lowercase()
            val path = file.path.lowercase()

            // 1. 检查基本模式匹配
            if (AD_PATTERNS.any { name.contains(it) }) return@getOrPut true

            // 2. 检查正则模式
            if (AD_FILE_REGEX_PATTERNS.any { it.matches(name) }) return@getOrPut true

            // 3. 检查路径中的广告关键词
            if (path.contains("/adcache/") ||
                path.contains("/advertising/") ||
                path.contains("/ads/") ||
                path.contains("/ad_data/") ||
                path.contains("/ad_images/") ||
                path.contains("/ad_videos/")) return@getOrPut true

            // 4. 检查文件扩展名
            val ext = file.extension.lowercase()
            if (ext in setOf("ad", "ads", "adcache", "advert", "advertisement")) return@getOrPut true

            // 5. 检查常见的广告SDK文件命名模式
            if (name.startsWith("ad_") || name.endsWith("_ad") ||
                name.contains("_ad_") || name.contains(".ad.")) return@getOrPut true

            false
        }
    }

    /**
     * 广告目录检查方法
     */
    private fun isAdvertisementDirectory(dir: File): Boolean {
        val cacheKey = "dir_${dir.absolutePath.hashCode()}"
        return adPatternCache.getOrPut(cacheKey) {
            val name = dir.name.lowercase()
            val path = dir.path.lowercase()

            // 1. 检查目录名
            if (AD_PATTERNS.any { name.contains(it) }) return@getOrPut true
            if (AD_DIR_PATTERNS.any { name.contains(it) }) return@getOrPut true

            // 2. 检查隐藏的广告目录
            if (name.startsWith(".ad_") || name.startsWith("ad_") ||
                name.startsWith(".ads_") || name.startsWith("ads_")) return@getOrPut true

            // 3. 检查路径中的广告目录
            if (path.contains("/adcache/") ||
                path.contains("/advertising/") ||
                path.contains("/ads/") ||
                path.contains("/ad_data/")) return@getOrPut true

            // 4. 检查常见的广告SDK目录模式
            if (name.matches(Regex(".*ads?.*cache.*", RegexOption.IGNORE_CASE)) ||
                name.matches(Regex(".*ad[._].*", RegexOption.IGNORE_CASE))) return@getOrPut true

            false
        }
    }

    /**
     * 流式文件扫描优化
     */
    private fun scanFilesFlow(
        fileFilter: (File) -> Boolean,
        categoryName: String,
        minSize: Long = MIN_FILE_SIZE
    ): Flow<Set<JunkChildBean>> = flow {
        val junkFiles = kotlin.collections.LinkedHashSet<JunkChildBean>()
        var scannedInCategory = 0

        scanOptimizedDirectoriesFlow().collect { file ->
            if (!isScanning.get()) return@collect

            try {
                // 检查是否已扫描过
                if (isAlreadyScanned(file)) return@collect

                // 应用过滤器
                if (!fileFilter(file)) return@collect

                // 计算大小
                val size = if (file.isFile) file.length() else calculateDirectorySizeOptimized(file)
                if (size < minSize) return@collect

                // 创建JunkChildBean
                val bean = JunkChildBean(file.name, file.absolutePath, size)
                junkFiles.add(bean)
                scannedFilesCount.incrementAndGet()
                scannedInCategory++
                markAsScanned(file)

                // 限制文件数量
                if (scannedInCategory >= MAX_FILES_PER_CATEGORY) {
                    Log.d(
                        TAG,
                        "Reached max files limit ($MAX_FILES_PER_CATEGORY) for $categoryName"
                    )
                    return@collect
                }

                // 定期输出进度
                if (scannedInCategory % 50 == 0) {
                    Log.d(TAG, "$categoryName: scanned $scannedInCategory files so far")
                }

            } catch (e: Exception) {
                Log.w(TAG, "Error processing file: ${file.absolutePath}", e)
            }
        }

        Log.d(TAG, "$categoryName completed: found ${junkFiles.size} items")
        emit(junkFiles)
    }.flowOn(Dispatchers.IO)

    /**
     * 优化的目录扫描Flow
     */
    private fun scanOptimizedDirectoriesFlow(): Flow<File> = flow {
        val directories = HighVersionDirectoryHelper.getPrioritizedDirectories(context)
        Log.d(TAG, "Will scan ${directories.size} directories")

        directories.forEachIndexed { index, dir ->
            if (!isScanning.get())
                return@forEachIndexed

            try {
                if (dir.exists() && dir.isDirectory && dir.canRead() && !isSystemDirectory(dir)) {
                    Log.d(
                        TAG,
                        "Scanning directory ${index + 1}/${directories.size}: ${dir.absolutePath}"
                    )
                    scanDirectoryFlow(dir).collect { file ->
                        emit(file)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to scan directory: ${dir.absolutePath}", e)
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 流式目录扫描优化
     */
    private fun scanDirectoryFlow(
        dir: File,
        depth: Int = 0
    ): Flow<File> = flow {
        if (depth >= MAX_SCAN_DEPTH || !isScanning.get()) return@flow

        try {
            val files = dir.listFiles() ?: return@flow

            // 分批处理减少内存压力
            val (subDirs, normalFiles) = files.partition { it.isDirectory }

            // 发射文件
            normalFiles.forEach { file ->
                if (isScanning.get() && file.isFile) {
                    emit(file)
                }
            }

            // 并行处理子目录（限制并行度）
            subDirs.chunked(PARALLEL_SCAN_LIMIT).forEach { dirChunk ->
                if (!isScanning.get()) return@forEach

                dirChunk.forEach { subDir ->
                    try {
                        if (!isSystemDirectory(subDir) &&
                            !isAppDataDirectory(subDir) &&
                            subDir.canRead()
                        ) {
                            scanDirectoryFlow(subDir, depth + 1).collect { file ->
                                emit(file)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error scanning subdirectory: ${subDir.absolutePath}", e)
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to access directory: ${dir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning directory: ${dir.absolutePath}", e)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 优化的目录大小计算
     */
    private fun calculateDirectorySizeOptimized(dir: File): Long {
        return directorySizeCache.getOrPut(dir.absolutePath) {
            calculateDirectorySizeInternal(dir)
        }
    }

    private fun calculateDirectorySizeInternal(dir: File): Long {
        return try {
            var size = 0L
            var fileCount = 0
            val maxFilesToCheck = 500

            dir.walk()
                .onEnter { fileCount < maxFilesToCheck && isScanning.get() }
                .forEach { file ->
                    if (file.isFile) {
                        size += file.length()
                        fileCount++
                    }
                }
            size
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating directory size: ${dir.absolutePath}", e)
            0L
        }
    }

    /**
     * 系统APK检查
     */
    private fun isSystemApk(file: File): Boolean =
        file.path.contains("/system/") ||
                file.path.contains("/preload/") ||
                file.path.contains("/system_app/") ||
                file.path.contains("/priv-app/")

    private fun isExpiredApk(file: File): Boolean =
        isFileTooOld(file) || isOldDownloadedApk(file)

    private fun isFileTooOld(file: File): Boolean {
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        return file.lastModified() < thirtyDaysAgo
    }

    private fun isOldDownloadedApk(file: File): Boolean {
        val isInDownloadDir = file.path.contains("/download", ignoreCase = true) ||
                file.path.contains("/downloads", ignoreCase = true) ||
                file.path.contains("/sdcard/download", ignoreCase = true)

        if (isInDownloadDir) {
            val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
            return file.lastModified() < sevenDaysAgo
        }

        return false
    }

    /**
     * 系统目录检查优化
     */
    private fun isSystemDirectory(dir: File): Boolean {
        if (isHiddenDirectory(dir)) return true

        val path = dir.absolutePath
        if (path.length < 3) return false

        val lowerPath = path.lowercase()
        return isSystemPath(lowerPath) || isSpecialAndroidDirectory(lowerPath)
    }

    private fun isHiddenDirectory(dir: File): Boolean {
        return HIDDEN_DIR_PATTERN.matches(dir.name) ||
                dir.name.equals("lost+found", ignoreCase = true) ||
                dir.name.startsWith(".")
    }

    private fun isSystemPath(lowerPath: String): Boolean {
        return SYSTEM_DIR_PATTERNS.any { systemDir ->
            when {
                lowerPath == systemDir -> true
                lowerPath.startsWith("$systemDir/") -> true
                systemDir == "/android" && lowerPath.contains("/android/") -> true
                else -> false
            }
        }
    }

    private fun isSpecialAndroidDirectory(lowerPath: String): Boolean {
        val androidIndex = lowerPath.indexOf("/android/")
        if (androidIndex == -1) return false

        val afterAndroid = lowerPath.substring(androidIndex + 9)
        return !afterAndroid.startsWith("data/") && !afterAndroid.startsWith("data")
    }

    /**
     * 应用数据目录检查（避免扫描应用私有数据）
     */
    private fun isAppDataDirectory(dir: File): Boolean {
        val path = dir.absolutePath.lowercase()
        return APP_DATA_PATTERNS.any { path.contains(it) }
    }

    /**
     * 重复扫描检查优化
     */
    private fun isAlreadyScanned(file: File): Boolean =
        scannedPathsCache.containsKey(file.absolutePath)

    private fun markAsScanned(file: File) {
        scannedPathsCache[file.absolutePath] = true
    }


    private fun resetScanState() {
        scannedFilesCount.set(0)
        directorySizeCache.clear()
        scannedPathsCache.clear()
        adPatternCache.clear()
    }

    /**
     * 取消扫描优化
     */
    fun cancelScan() {
        if (isScanning.compareAndSet(true, false)) {
            scanScope.coroutineContext.cancelChildren()
            cleanupResources()
            Log.d(TAG, "Scan cancelled")
        }
    }

    /**
     * 检查是否正在扫描
     */
    fun isScanning(): Boolean = isScanning.get()

    /**
     * 获取已扫描的文件数量
     */
    fun getScannedFilesCount(): Int = scannedFilesCount.get()

    private fun cleanupResources() {
        directorySizeCache.clear()
        scannedPathsCache.clear()
        adPatternCache.clear()
    }

    /**
     * 资源清理
     */
    fun destroy() {
        cancelScan()
        scanScope.cancel()
        cleanupResources()
    }
}