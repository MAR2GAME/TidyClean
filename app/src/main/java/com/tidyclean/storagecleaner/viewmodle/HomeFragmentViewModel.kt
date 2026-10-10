package com.tidyclean.storagecleaner.viewmodle

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.os.StatFs
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import com.tidyclean.storagecleaner.base.viewmodel.BaseViewModel
import com.tidyclean.storagecleaner.bean.StorageInfo

import java.io.File
import kotlin.text.split

class HomeFragmentViewModel(application: Application) : BaseViewModel(application) {


    var infoData = MutableLiveData<StorageInfo>()
    @SuppressLint("DefaultLocale", "SetTextI18n")
    fun getStorageInfo() {
        try {
            var storageInfo: StorageInfo?
            val app = application
            val externalDir = File(
                app.getExternalFilesDir(null)?.absolutePath?.split("/Android")?.get(0) ?: ""
            )
            if (externalDir.exists()) {
                val statFs = StatFs(externalDir.absolutePath)

                // 获取存储块大小和数量
                val blockSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    statFs.blockSizeLong
                } else {
                    statFs.blockSize.toLong()
                }

                val totalBlocks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    statFs.blockCountLong
                } else {
                    statFs.blockCount.toLong()
                }

                val availableBlocks = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    statFs.availableBlocksLong
                } else {
                    statFs.availableBlocks.toLong()
                }
                // 计算存储空间
                val totalSize = totalBlocks * blockSize
                val availableSize = availableBlocks * blockSize
                val usedSize = totalSize - availableSize
                // 计算使用百分比
                val usedPercentage = if (totalSize > 0) {
                    (usedSize * 100 / totalSize)
                } else {
                    0
                }
                storageInfo = StorageInfo(totalSize, usedSize, usedPercentage.toFloat())
            } else {
                storageInfo = StorageInfo(0, 0, 0f)
            }
            infoData.value = storageInfo
        }catch (e: Exception){

        }
    }

}