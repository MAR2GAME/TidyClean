package com.tidyclean.storagecleaner.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.ImageView
import android.widget.TextView
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.bean.JunkChildBean
import com.tidyclean.storagecleaner.bean.JunkMotherBean
import com.tidyclean.storagecleaner.utils.SizeUtil
import com.tidyclean.storagecleaner.view.MyRotatingRingView


import kotlin.apply

import kotlin.collections.get


import kotlin.compareTo

class ExpandableListAdapter(
    var context: Context,
    var groups: MutableList<JunkMotherBean>
) : BaseExpandableListAdapter() {
    override fun getChild(groupPosition: Int, childPosition: Int): Any? {
        return groups[groupPosition].junkChildrenItems[childPosition]
    }

    override fun getChildId(groupPosition: Int, childPosition: Int): Long {
        return childPosition.toLong()
    }

    override fun getChildView(
        groupPosition: Int,
        childPosition: Int,
        isLastChild: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View? {
        val childViewHolder: ChildViewHolder
        val view: View
        val junkChildBean: JunkChildBean =
            groups[groupPosition].junkChildrenItems[childPosition]

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_list_child, null)
            childViewHolder = ChildViewHolder().apply {
                childName = view.findViewById<TextView>(R.id.tv_child_name)
                childSize = view.findViewById<TextView>(R.id.tv_child_size)
                childCheck = view.findViewById<ImageView>(R.id.iv_child_check)
            }
            view.tag = childViewHolder
        } else {
            view = convertView
            childViewHolder = view.tag as ChildViewHolder
        }
        childViewHolder.childName.text = junkChildBean.name

        childViewHolder.childSize.text = SizeUtil.formatSize(junkChildBean.size)
        if (junkChildBean.isCheck) {
            childViewHolder.childCheck.setImageResource(R.mipmap.ic_check)
        } else {
            childViewHolder.childCheck.setImageResource(R.mipmap.ic_check_nor)
        }
        return view
    }

    override fun getChildrenCount(groupPosition: Int): Int {
        return groups[groupPosition].junkChildrenItems.size
    }

    override fun getGroup(groupPosition: Int): Any? {
        return groups[groupPosition]
    }

    override fun getGroupCount(): Int {
        return groups.size
    }

    override fun getGroupId(groupPosition: Int): Long {
        return groupPosition.toLong()
    }

    @SuppressLint("SetTextI18n")
    override fun getGroupView(
        groupPosition: Int,
        isExpanded: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View? {
        val groupViewHolder: GroupViewHolder
        val view: View
        val group = getGroup(groupPosition) as JunkMotherBean
        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.item_list_group, null)
            groupViewHolder = GroupViewHolder().apply {
                groupName = view.findViewById<TextView>(R.id.tv_group_name)
                groupSize = view.findViewById<TextView>(R.id.tv_size)
                groupIndicator = view.findViewById<ImageView>(R.id.iv_indicator)
                groupRotatingRingView = view.findViewById<MyRotatingRingView>(R.id.rotatingRingView)
                groupCheck = view.findViewById<ImageView>(R.id.iv_check)
            }
            view.tag = groupViewHolder
        } else {
            view = convertView
            groupViewHolder = view.tag as GroupViewHolder
        }
        groupViewHolder.groupName.text = group.name
        // 设置展开指示器
        if (isExpanded) {
            groupViewHolder.groupIndicator.rotation = 180f
        } else {
            groupViewHolder.groupIndicator.rotation = 0f
        }
        if (group.isShowCheck) {
            groupViewHolder.groupRotatingRingView.visibility = View.GONE
            groupViewHolder.groupCheck.visibility = View.VISIBLE
            groupViewHolder.groupSize.visibility= View.VISIBLE
            if(group.size>0){
                groupViewHolder.groupSize.text = SizeUtil.formatSize(group.size)
            }else{
                groupViewHolder.groupSize.text ="0KB"
            }

        } else {
            groupViewHolder.groupRotatingRingView.visibility = View.VISIBLE
            groupViewHolder.groupCheck.visibility = View.GONE
            groupViewHolder.groupSize.visibility= View.GONE
        }
        if (group.isChecked) {
            groupViewHolder.groupCheck.setImageResource(R.mipmap.ic_check)
        } else {
            groupViewHolder.groupCheck.setImageResource(R.mipmap.ic_check_nor)
        }
        groupViewHolder.groupCheck.setOnClickListener {
            group.isChecked = !group.isChecked
            for (item in group.junkChildrenItems) {
                item.isCheck = group.isChecked
            }
            notifyDataSetChanged()
            if(monCheckChangeLister!=null){
                monCheckChangeLister!!.onCheckChange()
            }

        }
        return view
    }


    override fun hasStableIds(): Boolean {
        return true
    }

    override fun isChildSelectable(p0: Int, p1: Int): Boolean {
        return false
    }


    class GroupViewHolder {
        lateinit var groupName: TextView
        lateinit var groupSize: TextView
        lateinit var groupRes: ImageView
        lateinit var groupIndicator: ImageView
        lateinit var groupRotatingRingView: MyRotatingRingView
        lateinit var groupCheck: ImageView
    }

    class ChildViewHolder {
        lateinit var childName: TextView
        lateinit var childSize: TextView
        lateinit var childCheck: ImageView
    }


    private var monCheckChangeLister: OnCheckChangeLister? = null

    interface OnCheckChangeLister {
        fun onCheckChange() //void

    }
    fun setmonCheckChangeLister(monCheckChangeLister: OnCheckChangeLister) {
        this.monCheckChangeLister = monCheckChangeLister

    }



}