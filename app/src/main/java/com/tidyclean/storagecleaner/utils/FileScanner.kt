package com.tidyclean.storagecleaner.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.tidyclean.storagecleaner.bean.LargeFile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.apply
import kotlin.collections.any
import kotlin.collections.forEach
import kotlin.collections.sortedByDescending
import kotlin.collections.sumOf
import kotlin.io.extension
import kotlin.io.use
import kotlin.text.contains
import kotlin.text.endsWith
import kotlin.text.equals
import kotlin.text.indexOf
import kotlin.text.isEmpty
import kotlin.text.isNullOrEmpty
import kotlin.text.lowercase
import kotlin.text.orEmpty
import kotlin.text.startsWith
import kotlin.text.substring
import kotlin.text.substringAfterLast
import kotlin.to


class FileScanner(
    private val contentResolver: ContentResolver,
    private val unknownFileName: String
) {

    /**
     * 扫描结果封装：文件列表 + 总大小
     */
    data class ScanResult(
        val files: List<LargeFile>,
        val totalSize: Long
    )

    companion object {
        private const val MIN_FILE_SIZE = 30 * 1024 * 1024L
        private const val MILLISECONDS_MULTIPLIER = 1000L
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

        private val MEDIA_TYPE_PREFIXES = setOf("video/", "image/", "audio/")
        private val DOCUMENT_EXTENSIONS = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "rtf", "csv", "xml", "json", "md"
        )
        private val DOCUMENT_MIME_TYPES = setOf(
            "application/pdf",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/rtf",
            "application/json",
            "application/xml",
            "text/plain",
            "text/csv",
            "text/markdown",
            "text/xml"
        )
        private val FILE_EXTENSION_MAPPING = mapOf(
            setOf("jpg", "jpeg", "png", "gif", "bmp", "webp") to "image",
            setOf("mp4", "avi", "mov", "wmv", "flv", "mkv", "3gp") to "video",
            setOf("mp3", "wav", "ogg", "flac", "m4a", "aac") to "audio"
        )
        private val SYSTEM_DIR_PATTERNS = setOf(
            "/system", "/android", "/lost+found", "/preload", "/vendor", "/mnt",
            "/proc", "/sys", "/acct", "/dev", "/config", "/oem", "/firmware", "/cache"
        )
        private val HIDDEN_DIR_PATTERN = Regex("^\\..*")
    }

    // 扫描大文件（返回文件列表和总大小）
    suspend fun scanLargeFiles(): ScanResult = scanFiles(FileManagerCategory.LARGE_FILES)

    // 按类别扫描文件（返回文件列表和总大小）
    suspend fun scanFiles(category: FileManagerCategory): ScanResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            scanWithMediaStore(category)
        } else {
            scanWithFileApi(category)
        }
    }

    // ---------- Android 10+ 使用 MediaStore ----------
    private suspend fun scanWithMediaStore(category: FileManagerCategory): ScanResult {
        val files = mutableListOf<LargeFile>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.RELATIVE_PATH
        )
        val selectionQuery = buildSelectionQuery(category)
        val sortOrder = when (category) {
            FileManagerCategory.LARGE_FILES -> "${MediaStore.Files.FileColumns.SIZE} DESC"
            else -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        }

        try {
            contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selectionQuery.selection,
                selectionQuery.selectionArgs,
                sortOrder
            )?.use { cursor ->
                processMediaStoreCursor(cursor, files, category)
            }
        } catch (_: Exception) {
            // 查询失败，返回空结果
            return ScanResult(emptyList(), 0L)
        }

        val totalSize = files.sumOf { it.size }
        return ScanResult(files, totalSize)
    }

    private suspend fun processMediaStoreCursor(
        cursor: Cursor,
        files: MutableList<LargeFile>,
        category: FileManagerCategory
    ) {
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
        val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
        val relativePathColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)

        while (cursor.moveToNext()) {
            val path = cursor.getString(dataColumn)
            val name = cursor.getString(nameColumn) ?: unknownFileName
            if (path.isNullOrEmpty() || isSystemDirectory(path) || isHiddenDirectory(name)) {
                continue
            }

            val size = cursor.getLong(sizeColumn)
            val type = cursor.getString(typeColumn) ?: "unknown"
            val relativePath = cursor.readNullableString(relativePathColumn)
            if (!matchesMediaCategory(name, path, relativePath, type, size, category)) {
                continue
            }

            val id = cursor.getLong(idColumn)
            val date = cursor.getLong(dateColumn) * MILLISECONDS_MULTIPLIER
            val file = LargeFile(id, name, path, size, type, date).apply {
                if (MEDIA_TYPE_PREFIXES.any { type.startsWith(it) }) {
                    thumbnailUri = getMediaThumbnail(id, type)
                }
            }
            files.add(file)
        }
    }

    private suspend fun getMediaThumbnail(mediaId: Long, mimeType: String): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                when {
                    mimeType.startsWith("image/") -> ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )

                    mimeType.startsWith("video/") -> ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )

                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    // ---------- Android 10 以下使用 File API ----------
    private fun scanWithFileApi(category: FileManagerCategory): ScanResult {
        val files = mutableListOf<LargeFile>()
        val storageDir = Environment.getExternalStorageDirectory()
        scanDirectory(File(storageDir.absolutePath), files, category)

        val sortedFiles = when (category) {
            FileManagerCategory.LARGE_FILES -> files.sortedByDescending { it.size }
            else -> files.sortedByDescending { it.date }
        }
        val totalSize = sortedFiles.sumOf { it.size }
        return ScanResult(sortedFiles, totalSize)
    }

    private fun scanDirectory(
        dir: File,
        files: MutableList<LargeFile>,
        category: FileManagerCategory
    ) {
        try {
            if (isSystemDirectory(dir.absolutePath) || isHiddenDirectory(dir.name)) {
                return
            }

            dir.listFiles()?.forEach { file ->
                when {
                    file.isDirectory -> scanDirectory(file, files, category)
                    file.isFile && matchesFileCategory(file, category) -> processFile(file, files)
                }
            }
        } catch (_: SecurityException) {
            // 忽略无权限目录
        } catch (_: Exception) {
            // 忽略其他异常
        }
    }

    private fun processFile(file: File, files: MutableList<LargeFile>) {
        if (isSystemDirectory(file.absolutePath)) return

        val type = getFileType(file)
        files.add(
            LargeFile(
                id = file.hashCode().toLong(),
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                type = type,
                date = file.lastModified()
            ).apply {
                if (type.startsWith("image/") || type.startsWith("video/")) {
                    thumbnailUri = Uri.fromFile(file)
                }
            }
        )
    }

    private fun getFileType(file: File): String {
        val extension = file.extension.lowercase()
        if (extension.isEmpty()) return "unknown"

        FILE_EXTENSION_MAPPING.forEach { (extensions, type) ->
            if (extension in extensions) {
                return "$type/$extension"
            }
        }

        return extension
    }

    // ---------- 构建查询条件 ----------
    private fun buildSelectionQuery(category: FileManagerCategory): SelectionQuery {
        return when (category) {
            FileManagerCategory.LARGE_FILES -> SelectionQuery(
                selection = "${MediaStore.Files.FileColumns.SIZE} >= ?",
                selectionArgs = arrayOf(MIN_FILE_SIZE.toString())
            )

            FileManagerCategory.IMAGES -> SelectionQuery(
                selection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?",
                selectionArgs = arrayOf("image/%")
            )

            FileManagerCategory.VIDEO -> SelectionQuery(
                selection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?",
                selectionArgs = arrayOf("video/%")
            )

            FileManagerCategory.AUDIO -> SelectionQuery(
                selection = "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?",
                selectionArgs = arrayOf("audio/%")
            )


            FileManagerCategory.DOCUMENTS -> {
                val extensionSelection = DOCUMENT_EXTENSIONS.joinToString(" OR ") {
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
                }
                val mimeSelection = DOCUMENT_MIME_TYPES.joinToString(" OR ") {
                    "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
                }
                SelectionQuery(
                    selection = "($extensionSelection OR $mimeSelection)",
                    selectionArgs = (DOCUMENT_EXTENSIONS.map { "%.$it" } + DOCUMENT_MIME_TYPES).toTypedArray()
                )
            }
        }
    }

    // ---------- 分类匹配 ----------
    private fun matchesMediaCategory(
        name: String,
        path: String,
        relativePath: String?,
        mimeType: String,
        size: Long,
        category: FileManagerCategory
    ): Boolean {
        return when (category) {
            FileManagerCategory.LARGE_FILES -> size >= MIN_FILE_SIZE
            FileManagerCategory.IMAGES -> mimeType.startsWith("image/")
            FileManagerCategory.VIDEO -> mimeType.startsWith("video/")
            FileManagerCategory.AUDIO -> mimeType.startsWith("audio/")
            FileManagerCategory.DOCUMENTS -> isDocumentType(name, mimeType)
        }
    }

    private fun matchesFileCategory(file: File, category: FileManagerCategory): Boolean {
        val type = getFileType(file)
        return when (category) {
            FileManagerCategory.LARGE_FILES -> file.length() >= MIN_FILE_SIZE
            FileManagerCategory.IMAGES -> type.startsWith("image/")
            FileManagerCategory.VIDEO -> type.startsWith("video/")
            FileManagerCategory.AUDIO -> type.startsWith("audio/")
            FileManagerCategory.DOCUMENTS -> isDocumentType(file.name, type)
        }
    }

    private fun isDocumentType(name: String, mimeType: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in DOCUMENT_EXTENSIONS || mimeType in DOCUMENT_MIME_TYPES
    }

    private fun isDownloadPath(path: String, relativePath: String?): Boolean {
        val normalizedPath = path.lowercase()
        val normalizedRelativePath = relativePath?.lowercase().orEmpty()
        return normalizedRelativePath.startsWith("download/") ||
                normalizedPath.contains("/download/") ||
                normalizedPath.contains("/downloads/")
    }

    private fun isHiddenDirectory(name: String): Boolean {
        return HIDDEN_DIR_PATTERN.matches(name) || name.equals("lost+found", ignoreCase = true)
    }

    private fun isSystemDirectory(path: String): Boolean {
        if (path.length < 3) return false
        val lowerPath = path.lowercase()
        return SYSTEM_DIR_PATTERNS.any { systemDir ->
            when {
                lowerPath == systemDir -> true
                lowerPath.startsWith("$systemDir/") -> true
                systemDir == "/android" && lowerPath.contains("/android/") -> true
                else -> false
            }
        } || isSpecialAndroidDirectory(lowerPath)
    }

    private fun isSpecialAndroidDirectory(lowerPath: String): Boolean {
        val androidIndex = lowerPath.indexOf("/android/")
        if (androidIndex == -1) return false
        val afterAndroid = lowerPath.substring(androidIndex + 9)
        return !afterAndroid.startsWith("data/") && !afterAndroid.startsWith("data")
    }

    private fun Cursor.readNullableString(index: Int): String? {
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private data class SelectionQuery(
        val selection: String?,
        val selectionArgs: Array<String>?
    )
}