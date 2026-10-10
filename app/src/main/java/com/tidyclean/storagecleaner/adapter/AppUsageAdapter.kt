package com.tidyclean.storagecleaner.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.bean.AppUsageListItem
import com.tidyclean.storagecleaner.databinding.ItemAppUsageBinding


import kotlin.apply



class AppUsageAdapter(
    private val onManageClick: (AppUsageListItem) -> Unit
) : RecyclerView.Adapter<AppUsageAdapter.AppUsageViewHolder>() {

    private val items = mutableListOf<AppUsageListItem>()


    var tag=""
    fun submitList(items: List<AppUsageListItem>) {
        this.items.clear()
        this.items.addAll(items)
        notifyDataSetChanged()
    }

    fun updateTag(tag:String){
        this.tag=tag
    }





    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppUsageViewHolder {
        val binding = ItemAppUsageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppUsageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppUsageViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class AppUsageViewHolder(
        private val binding: ItemAppUsageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppUsageListItem) {
            binding.ivAppIcon.setImageDrawable(item.icon)
            binding.tvAppName.text = item.appName
            binding.tvUsageValue.text = item.valueText
            binding.progressUsage.progress = item.progress
            binding.tvUsageValueHint.text=tag
            binding.btnManage.apply {
                text = context.getString(R.string.manage)
                isEnabled = item.canManage
                alpha = if (item.canManage) 1f else 0.6f
                setBackgroundResource(
                    if (item.canManage) R.drawable.btn_nor
                    else R.drawable.app_usage_bg_shape
                )

                setOnClickListener {
                    if (item.canManage) {
                        onManageClick(item)
                    }
                }
            }
        }
    }
}
