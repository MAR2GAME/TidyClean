package com.tidyclean.storagecleaner.view

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.base.command.dp2px
import com.tidyclean.storagecleaner.base.command.log
import com.tidyclean.storagecleaner.base.command.safeClick


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@SuppressLint("InflateParams")
class RequestPermissionDialog(context: Context) : Dialog(context) {
    private var v: View? = null

    init {
        var inflater = LayoutInflater.from(context)
        v = inflater.inflate(R.layout.dialog_requestpermission, null)
        setContentView(v!!)
        setCancelable(true)  // 允许返回键关闭
        setCanceledOnTouchOutside(false)  // 禁用外部点击关闭
        val params = this.window!!.attributes
        params.width = dp2px(context, 330)
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        window!!.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        this.window!!.attributes = params
        this.window!!.setGravity(Gravity.CENTER)
        v!!.findViewById<Button>(R.id.bt_allow).safeClick {
            dismiss()
            if(mOnClickListener!=null){
                mOnClickListener!!.onAllow()
            }
        }
        v!!.findViewById<TextView>(R.id.tv_cancel).safeClick {
            dismiss()
        }
    }
    fun toShow() {
        log("filemanager_popup")
        if (v == null) {
            return
        }
        if (!isShowing) {
            show()
        }
    }

    fun release() {
        if (isShowing) {
            dismiss()
        }
        mOnClickListener=null
        v = null
    }

    private var mOnClickListener: onClickListener? = null

    interface onClickListener {
        fun onAllow() //void
    }
    fun setmOnClickListener(mOnClickListener: onClickListener) {
        this.mOnClickListener = mOnClickListener
    }





}