package com.tidyclean.storagecleaner.adapter


import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

import com.tidyclean.storagecleaner.ui.fragment.HomeFragment
import com.tidyclean.storagecleaner.ui.fragment.SettingFragment
import com.tidyclean.storagecleaner.ui.fragment.ToolFragment

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 3
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ToolFragment()
            1 -> HomeFragment()
            else -> SettingFragment()
        }
    }
}