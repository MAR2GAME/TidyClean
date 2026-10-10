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
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder

import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.base.command.dp2px
import com.tidyclean.storagecleaner.bean.TagBean
import com.tidyclean.storagecleaner.utils.EdgeToEdgeInsets
import kotlin.apply
import kotlin.let


class LargeFileDialog (context: Context) : Dialog(context){
    private var v: View? = null
    private var pos=-1
    var isSize: Boolean=false
    private lateinit var popItemAdapter: BaseQuickAdapter<TagBean, BaseViewHolder>
    init {
        var inflater = LayoutInflater.from(context)
        v = inflater.inflate(R.layout.dialog_largefile, null)
        setContentView(v!!)
        EdgeToEdgeInsets.applyNavigationBarPadding(v!!)
        setCancelable(true)
        setCanceledOnTouchOutside(true)
        window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setGravity(Gravity.BOTTOM)
            val params = attributes.apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = dp2px(context,530)
            }
            attributes = params
        }
        var recyclerView = v!!.findViewById<RecyclerView>(R.id.rc_tag)
        popItemAdapter =
            object : BaseQuickAdapter<TagBean, BaseViewHolder>(R.layout.item_tag) {
                @SuppressLint("NotifyDataSetChanged")
                override fun convert(
                    holder: BaseViewHolder,
                    item: TagBean
                ) {
                    item.let {
                        holder.setText(R.id.tv_tag_name,it.tagName)
                        val checkIcon = if (it.isSelected) R.mipmap.ic_check else R.mipmap.ic_check_nor
                        holder.setImageResource(R.id.iv_check, checkIcon)
                    }
                    holder.getView<RelativeLayout>(R.id.rl_tag).setOnClickListener {
                        pos=popItemAdapter.getItemPosition(item)
                        for (tagBean in popItemAdapter.data){
                            tagBean.isSelected=false
                        }
                        item.isSelected=true
                        popItemAdapter.notifyDataSetChanged()
                    }
                }
            }
        recyclerView.adapter = popItemAdapter
        recyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        v!!.findViewById<Button>(R.id.bt_confirm).setOnClickListener {
            dismiss()
            if(pos!=-1&&mOnClickListener!=null){
                mOnClickListener!!.onClick(isSize,pos)
            }
        }
    }


    fun toUpdate(data: MutableList<TagBean>, isSize: Boolean) {
        this.isSize=isSize
        if (isSize) {
            v!!.findViewById<TextView>(R.id.tv_title).text=context.getString(R.string.size)
        } else {
            v!!.findViewById<TextView>(R.id.tv_title).text=context.getString(R.string.type)
        }
        pos=-1
        popItemAdapter.setList(data)
    }
    fun relese() {
        if (isShowing) {
            dismiss()
        }
        mOnClickListener=null
        v = null
    }
    private var mOnClickListener: onClickListener? = null

    interface onClickListener {
        fun onClick(isSize: Boolean,pos:Int) //void
    }
    fun setmOnClickListener(mOnClickListener: onClickListener) {
        this.mOnClickListener = mOnClickListener
    }



}
