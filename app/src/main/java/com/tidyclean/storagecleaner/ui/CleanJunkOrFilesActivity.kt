package com.tidyclean.storagecleaner.ui

import android.content.ContentUris
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.base.activity.BaseViewActivity
import com.tidyclean.storagecleaner.base.command.readyGoThenKill
import com.tidyclean.storagecleaner.base.config.AppConfig
import com.tidyclean.storagecleaner.bean.CleanFilesBean
import com.tidyclean.storagecleaner.bean.CleanLargeFileBean
import com.tidyclean.storagecleaner.bean.JunkMotherBean
import com.tidyclean.storagecleaner.bean.LargeFile
import com.tidyclean.storagecleaner.databinding.ActivityCleanJunkOrFilesBinding
import com.tidyclean.storagecleaner.utils.SizeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File

class CleanJunkOrFilesActivity : BaseViewActivity<ActivityCleanJunkOrFilesBinding>() {

    private var totalSize: Long = 0L
    private val junkMotherBeans = mutableListOf<JunkMotherBean>()
    private val largeFileBeans = mutableListOf<LargeFile>()
    private var tag: String? = null
    private var toFinished = false
    private var cleaningStarted = false

    override fun initializeActivity() {
        super.initializeActivity()
        EventBus.getDefault().register(this)
        binding.ivClean.setImageAssetsFolder("images/")
        tag = intent.extras?.getString(AppConfig.NEXT_TAG)
        binding.ivClean.setAnimation(R.raw.junk_finish)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
            }
        })
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onCleanFilesEvent(event: CleanFilesBean) {
        // 移除粘性事件，防止重复消费
        EventBus.getDefault().removeStickyEvent(CleanFilesBean::class.java)
        if (cleaningStarted) return
        totalSize = event.size
        junkMotherBeans.clear()
        junkMotherBeans.addAll(event.groups)
        binding.tvCleanSize.text = SizeUtil.formatSize(totalSize)
        startCleanup()
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onCleanLargeFileEvent(event: CleanLargeFileBean) {
        EventBus.getDefault().removeStickyEvent(CleanLargeFileBean::class.java)
        if (cleaningStarted) return
        totalSize = event.size
        largeFileBeans.clear()
        largeFileBeans.addAll(event.groups)
        binding.tvCleanSize.text = SizeUtil.formatSize(totalSize)
        startCleanup()
    }

    private fun startCleanup() {
        if (cleaningStarted) return
        cleaningStarted = true
        lifecycleScope.launch {
            try {
                // 稍作延迟，让 UI 过渡更自然
                delay(1500)
                withContext(Dispatchers.IO) {
                    deleteJunkFiles { remainingSize ->
                        // 每次进度回调都切回主线程更新UI
                        withContext(Dispatchers.Main) {
                            binding.tvCleanSize.text = if (remainingSize > 0) {
                                SizeUtil.formatSize(remainingSize)
                            } else {
                                "0KB"
                            }
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    binding.tvCleanSize.text = "0KB"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cleanup failed", e)
                withContext(Dispatchers.Main) {
                    binding.tvCleanSize.text = "0KB"
                }
            } finally {
                if (!toFinished) {
                    toFinished = true
                    val extras = intent.extras ?: Bundle()
                    readyGoThenKill(CompleteActivity::class.java, extras)
                }
            }
        }
    }

    private suspend fun deleteJunkFiles(onProgress: suspend (Long) -> Unit) {
        var remaining = totalSize
        val chunkSize = if (totalSize > 0) totalSize / 20 else 0L
        repeat(20) {
            remaining -= chunkSize
            onProgress(remaining.coerceAtLeast(0))
            delay(100)
        }
        try {
            when (tag) {
                getString(R.string.remove_junk) -> deleteJunkInternal()
                else -> deleteLargeFilesInternal()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete files failed", e)
        } finally {
            onProgress(0)
        }
    }

    private fun deleteJunkInternal() {
        for (group in junkMotherBeans) {
            if (!group.isChecked || group.size <= 0) continue
            for (child in group.junkChildrenItems) {
                val path = child.path
                if (path.isNullOrBlank()) continue
                val file = File(path)
                if (!file.exists()) continue
                try {
                    deleteRecursively(file)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete junk: $path", e)
                }
            }
        }
    }

    private fun deleteLargeFilesInternal() {
        for (largeFile in largeFileBeans) {
            try {
                val file = File(largeFile.path)
                if (file.exists()) {
                    if (!deleteRecursively(file)) {
                        Log.w(TAG, "File.delete failed for ${largeFile.path}")
                    }
                }
                // Android 10+ 同时删除 MediaStore 记录
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    deleteFromMediaStore(largeFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete large file: ${largeFile.path}", e)
            }
        }
    }

    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursively(child)
            }
        }
        return file.delete()
    }

    private fun deleteFromMediaStore(largeFile: LargeFile) {
        try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Files.getContentUri("external"),
                largeFile.id
            )
            contentResolver.delete(uri, null, null)
        } catch (e: SecurityException) {
            // Android 10+ 可能因为缺少 MANAGE_EXTERNAL_STORAGE 权限而失败
            Log.w(TAG, "SecurityException deleting media: ${largeFile.path}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete media record: ${largeFile.path}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.ivClean.cancelAnimation()
        binding.ivClean.clearAnimation()
        EventBus.getDefault().unregister(this)
        // 清理可能残留的粘性事件，避免下次进入时重复触发
    }

    companion object {
        private const val TAG = "CleanJunkOrFilesActivity"
    }
}