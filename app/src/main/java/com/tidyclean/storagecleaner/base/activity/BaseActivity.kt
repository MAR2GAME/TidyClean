package com.tidyclean.storagecleaner.base.activity


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tidyclean.storagecleaner.advertise.AdvertiseUserAttributes
import com.tidyclean.storagecleaner.base.command.checkNotificationPermission
import com.tidyclean.storagecleaner.base.command.hasStoragePermission
import com.tidyclean.storagecleaner.utils.ActivityManagerUtils
import com.tidyclean.storagecleaner.view.ProgressDialog


open class BaseActivity : AppCompatActivity() {


    var progressDialog: ProgressDialog? = null

    fun handleLoadingState(showLoading: Boolean) {
        if (progressDialog == null) {
            progressDialog = ProgressDialog(this)
        }
        if (showLoading) {
            progressDialog?.showProgress()
        } else {
            progressDialog?.hideProgress()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityManagerUtils.addActivity(this)
    }

    override fun onDestroy() {
        AdvertiseUserAttributes.setUser(
            mapOf("has_notification_permission" to checkNotificationPermission(this)),
            "user_set",
        )
        AdvertiseUserAttributes.setUser(
            mapOf("has_allfile_permission" to hasStoragePermission()),
            "user_set",
        )
        progressDialog?.hideProgress()
        progressDialog = null
        ActivityManagerUtils.removeActivity(this)
        super.onDestroy()
    }

}