package com.tidyclean.storagecleaner.view

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.base.command.dp2px
import com.tidyclean.storagecleaner.base.command.safeClick


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ExitDialog (context: Context) : Dialog(context) {
    private var v: View? = null

    init {
        var inflater = LayoutInflater.from(context)
        v = inflater.inflate(R.layout.dialog_exit, null)
        setContentView(v!!)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        val params = this.window!!.attributes
        params.width = dp2px(context, 320)
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        window!!.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        this.window!!.attributes = params
        this.window!!.setGravity(Gravity.CENTER)
        v!!.findViewById<TextView>(R.id.tv_exit).safeClick {
            if(mOnClickListener!=null){
                mOnClickListener!!.onNext()
            }
            dismiss()
        }
        v!!.findViewById<Button>(R.id.bt_cancel).safeClick {
            dismiss()
        }
    }

    fun toShow() {
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
        mOnClickListener = null
        v = null
    }


    private var mOnClickListener: onClickListener? = null
    interface onClickListener {
        fun onNext()
    }
    fun setmOnClickListener(mOnClickListener: onClickListener) {
        this.mOnClickListener = mOnClickListener
    }
}