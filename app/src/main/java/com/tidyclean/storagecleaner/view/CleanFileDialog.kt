package com.tidyclean.storagecleaner.view

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
import com.tidyclean.storagecleaner.base.command.safeClick
import kotlin.apply


class CleanFileDialog(context: Context) : Dialog(context) {
    private var v: View? = null

    init {
        val inflater = LayoutInflater.from(context)
        v = inflater.inflate(R.layout.dialog_cleanfile, null)
        setContentView(v!!)
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setGravity(Gravity.CENTER)
            val params = attributes.apply {
                width = dp2px(context, 320)
                height = WindowManager.LayoutParams.WRAP_CONTENT
            }
            attributes = params
        }
        v!!.findViewById<Button>(R.id.bt_delete).safeClick {
            dismiss()
            mOnClickListener?.onNext()
        }
        v!!.findViewById<Button>(R.id.bt_cancel).safeClick {
            dismiss()
        }
    }

    fun showDialog() {
        if (!isShowing) {
            show()
        }
    }

    fun setHint(hint: String) {
        if (v == null) {
            return
        }
        v!!.findViewById<TextView>(R.id.tv_hint)?.text = hint
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
        fun onNext() //void
    }

    fun setmOnClickListener(mOnClickListener: onClickListener) {
        this.mOnClickListener = mOnClickListener
    }
}
