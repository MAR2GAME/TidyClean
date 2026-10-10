package com.tidyclean.storagecleaner.viewmodle

import android.app.Application
import android.content.ContentResolver
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.base.viewmodel.BaseViewModel
import com.tidyclean.storagecleaner.bean.LargeFile
import com.tidyclean.storagecleaner.utils.FileManagerCategory
import com.tidyclean.storagecleaner.utils.FileScanner


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoadingViewModel(application: Application) : BaseViewModel(application) {

    var largeFilesData = MutableLiveData<FileScanner.ScanResult>()

    fun startScanFiles(contentResolver: ContentResolver, tag: String?) {
        viewModelScope.launch {
            try {
                val category = FileManagerCategory.fromTag(application, tag)
                val scanResult = FileScanner(
                    contentResolver,
                    application.getString(R.string.unknown)
                ).scanFiles(category)
                largeFilesData.value = scanResult
            } catch (_: Exception) {
                largeFilesData.value = FileScanner.ScanResult(emptyList<LargeFile>(),0)
            }
        }
    }
}
