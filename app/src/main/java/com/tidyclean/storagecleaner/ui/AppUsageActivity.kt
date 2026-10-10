package com.tidyclean.storagecleaner.ui

import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.google.android.material.tabs.TabLayout
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.adapter.AppUsageAdapter
import com.tidyclean.storagecleaner.advertise.AdAreaKeys
import com.tidyclean.storagecleaner.advertise.AdvertisePageMediator.showInterstitial
import com.tidyclean.storagecleaner.base.activity.BaseViewActivity
import com.tidyclean.storagecleaner.base.command.log
import com.tidyclean.storagecleaner.base.command.openAppInfoSettings
import com.tidyclean.storagecleaner.base.command.readyGoThenKill
import com.tidyclean.storagecleaner.base.command.safeClick
import com.tidyclean.storagecleaner.bean.AppUsageDonutSlice
import com.tidyclean.storagecleaner.bean.AppUsageEntry
import com.tidyclean.storagecleaner.bean.AppUsageListItem
import com.tidyclean.storagecleaner.bean.AppUsageMetric
import com.tidyclean.storagecleaner.bean.AppUsageRange
import com.tidyclean.storagecleaner.bean.AppUsageSnapshot
import com.tidyclean.storagecleaner.databinding.ActivityAppUsageBinding
import com.tidyclean.storagecleaner.utils.AppUsageAnalyzer
import com.tidyclean.storagecleaner.utils.AppUsageFormatter
import com.tidyclean.storagecleaner.utils.UsageStatsHelper
import com.tidyclean.storagecleaner.view.UsageDataPermissionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppUsageActivity : BaseViewActivity<ActivityAppUsageBinding>(){

    private var tabData = mutableListOf<TabItem>()
    private var usageDataPermissionDialog: UsageDataPermissionDialog? = null
    private lateinit var appUsageAdapter: AppUsageAdapter

    private lateinit var appNameAdapter: BaseQuickAdapter<AppUsageDonutSlice, BaseViewHolder>

    private var currentRange = AppUsageRange.TODAY
    private var currentMetric = AppUsageMetric.HOURS_SPENT
    private var currentSnapshot: AppUsageSnapshot? = null


    override fun initializeActivity() {
        super.initializeActivity()
        initTab()
        checkPermissionAndRedirect()
        setupRecyclerView()
        setupClickListeners()
        val details=intent.extras?.getString("details")
        val referrer_name=intent.extras?.getString("referrer_name")
        if(!TextUtils.isEmpty( details)){
            log("enter_corefeatures_page",mapOf("details" to details!! ,"referrer_name" to referrer_name!!))
        }
    }

    private fun setupRecyclerView() {
        appUsageAdapter = AppUsageAdapter { item ->
            openAppInfoSettings(item.packageName)
        }
        appUsageAdapter.updateTag(getString(R.string.hours_spent))
        binding.rvUsage.apply {
            adapter = appUsageAdapter
            layoutManager = LinearLayoutManager(this@AppUsageActivity)
            setHasFixedSize(true)
        }
        appNameAdapter=object :BaseQuickAdapter<AppUsageDonutSlice, BaseViewHolder>(R.layout.item_app_name){
            override fun convert(
                holder: BaseViewHolder,
                item: AppUsageDonutSlice
            ) {
                when(appNameAdapter.getItemPosition(item)){
                    0-> holder.getView<View>(R.id.iv_bg).setBackgroundResource(R.drawable.oval_shape_1)
                    1-> holder.getView<View>(R.id.iv_bg).setBackgroundResource(R.drawable.oval_shape_2)
                    2-> holder.getView<View>(R.id.iv_bg).setBackgroundResource(R.drawable.oval_shape_3)
                    else-> holder.getView<View>(R.id.iv_bg).setBackgroundResource(R.drawable.oval_shape_4)

                }
                holder.setText(R.id.tv_name,item.label)
            }

        }
        binding.rcApp.apply {
            adapter = appNameAdapter
            layoutManager = LinearLayoutManager(this@AppUsageActivity)
        }
    }

    private fun setupClickListeners() {
        binding.ivBack.safeClick { onBack() }
        binding.btnHoursSpent.safeClick {
            currentMetric = AppUsageMetric.HOURS_SPENT
            renderMetricToggle()
            appUsageAdapter.updateTag(getString(R.string.hours_spent))
            currentSnapshot?.let(::renderUsageSnapshot)
        }
        binding.btnTimesOpened.safeClick {
            currentMetric = AppUsageMetric.TIMES_OPENED
            renderMetricToggle()
            appUsageAdapter.updateTag(getString(R.string.times_opened))
            currentSnapshot?.let(::renderUsageSnapshot)
        }

        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    fun onBack(){
        showInterstitial(this, AdAreaKeys.Interstitial.RETURN_FROM_APP_USAGE_ADV){
            readyGoThenKill(MainActivity::class.java)
        }
    }

    private fun renderUsageSnapshot(snapshot: AppUsageSnapshot) {
        binding.tvSummaryRange.text = getString(snapshot.range.labelResId)
        binding.tvSummaryValue.text = AppUsageFormatter.formatSummaryValue(this, currentMetric, snapshot)
        val sortedEntries = when (currentMetric) {
            AppUsageMetric.HOURS_SPENT -> snapshot.entries.sortedByDescending { it.totalTimeMs }
            AppUsageMetric.TIMES_OPENED -> snapshot.entries.sortedByDescending { it.openCount }
        }

        val maxValue = when (currentMetric) {
            AppUsageMetric.HOURS_SPENT -> sortedEntries.maxOfOrNull { it.totalTimeMs }?.toFloat() ?: 0f
            AppUsageMetric.TIMES_OPENED -> sortedEntries.maxOfOrNull { it.openCount }?.toFloat() ?: 0f
        }

        val items = sortedEntries.map { entry ->
            AppUsageListItem(
                packageName = entry.packageName,
                appName = entry.appName,
                icon = entry.icon,
                valueText = when (currentMetric) {
                    AppUsageMetric.HOURS_SPENT -> AppUsageFormatter.formatDurationCompact(
                        this,
                        entry.totalTimeMs
                    )

                    AppUsageMetric.TIMES_OPENED -> AppUsageFormatter.formatOpenCount(
                        this,
                        entry.openCount
                    )
                },
                progress = calculateProgress(entry, maxValue),
                canManage = AppUsageFormatter.canManageApp(
                    currentMetric,
                    packageName = entry.packageName,
                    currentPackageName = packageName,
                    totalTimeMs = entry.totalTimeMs,
                    openCount = entry.openCount
                )
            )
        }
        appUsageAdapter.submitList(items)
        binding.tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        renderDonutAndLegend(sortedEntries)
    }


    private fun renderDonutAndLegend(entries: List<AppUsageEntry>) {
        val palette = listOf(
            ContextCompat.getColor(this, R.color.feature_donut_1),
            ContextCompat.getColor(this, R.color.feature_donut_2),
            ContextCompat.getColor(this, R.color.feature_donut_3),
            ContextCompat.getColor(this, R.color.feature_donut_4)
        )

        val sourceValues = when (currentMetric) {
            AppUsageMetric.HOURS_SPENT -> entries.filter { it.totalTimeMs > 0L }.sortedByDescending { it.totalTimeMs }
            AppUsageMetric.TIMES_OPENED -> entries.filter { it.openCount > 0 }.sortedByDescending { it.openCount }
        }

        val topEntries = sourceValues.take(3)
        val otherValue = when (currentMetric) {
            AppUsageMetric.HOURS_SPENT -> (sourceValues.drop(3).sumOf { it.totalTimeMs }).toFloat()
            AppUsageMetric.TIMES_OPENED -> sourceValues.drop(3).sumOf { it.openCount }.toFloat()
        }

        val slices = buildList {
            topEntries.forEachIndexed { index, entry ->
                add(
                    AppUsageDonutSlice(
                        label = entry.appName,
                        color = palette[index],
                        value = when (currentMetric) {
                            AppUsageMetric.HOURS_SPENT -> entry.totalTimeMs.toFloat()
                            AppUsageMetric.TIMES_OPENED -> entry.openCount.toFloat()
                        }
                    )
                )
            }
            if (otherValue > 0f) {
                add(AppUsageDonutSlice(getString(R.string.other), palette.last(), otherValue))
            }
        }
        binding.donutUsage.setSlices(slices)
        appNameAdapter.setList(slices)

    }




    private fun calculateProgress(entry: AppUsageEntry, maxValue: Float): Int {
        if (maxValue <= 0f) return 0
        val rawProgress = when (currentMetric) {
            AppUsageMetric.HOURS_SPENT -> (entry.totalTimeMs / maxValue) * 100f
            AppUsageMetric.TIMES_OPENED -> (entry.openCount / maxValue) * 100f
        }
        return rawProgress.toInt().coerceIn(if (rawProgress > 0f) 6 else 0, 100)
    }

    private fun renderMetricToggle() {
        val selectedColor = ContextCompat.getColor(this, R.color.title_color)
        val unselectedColor = ContextCompat.getColor(this, R.color.hint_color)
        if(currentMetric == AppUsageMetric.HOURS_SPENT){
            binding.btnHoursSpent.setBackgroundResource(R.drawable.dialog_shape)
            binding.btnTimesOpened.background=null
        }else{
            binding.btnTimesOpened.setBackgroundResource(R.drawable.dialog_shape)
            binding.btnHoursSpent.background=null
        }
        binding.btnHoursSpent.setTextColor(
            if (currentMetric == AppUsageMetric.HOURS_SPENT) selectedColor else unselectedColor
        )
        binding.btnTimesOpened.setTextColor(
            if (currentMetric == AppUsageMetric.TIMES_OPENED) selectedColor else unselectedColor
        )
    }





    private fun checkPermissionAndRedirect() {
        if (!UsageStatsHelper.hasPermission(this)) {
            if (usageDataPermissionDialog == null) {
                usageDataPermissionDialog = UsageDataPermissionDialog(this)
                usageDataPermissionDialog?.setOnClickListener(object :
                    UsageDataPermissionDialog.OnClickListener {
                    override fun onAllow() {
                        val success =
                            UsageStatsHelper.navigateToUsageAccessSettings(this@AppUsageActivity)
                        if (success) {
                            UsageStatsHelper.markNavigated()
                        } else {
                            Toast.makeText(
                                this@AppUsageActivity,
                                getString(R.string.unable_to_open_permission),
                                Toast.LENGTH_SHORT
                            ).show()
                            readyGoThenKill(MainActivity::class.java)
                        }
                    }
                })
            }
            usageDataPermissionDialog?.showDialog()
        }
    }

    @SuppressLint("MissingInflatedId")
    fun initTab(){
        tabData.add(TabItem(getString(R.string.today)))
        tabData.add(TabItem(getString(R.string.yesterday)))
        tabData.add(TabItem(getString(R.string.last_7_days)))
        tabData.add(TabItem(getString(R.string.last_30_days)))
        tabData.forEachIndexed { index, item ->
            val tab = binding.tabLayout.newTab()
            // 自定义视图
            val customView = layoutInflater.inflate(R.layout.tab_item, null)
            val titleTv =customView.findViewById<TextView>(R.id.tv_title)
            titleTv.text = item.title
            tab.customView = customView
            binding.tabLayout.addTab(tab, index) // 添加并指定位置
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    val position = it.position
                    when(position){
                        0->{
                            if(currentRange!= AppUsageRange.TODAY ){
                                currentRange = AppUsageRange.TODAY
                                loadUsageData()
                            }
                        }
                        1->{
                            if(currentRange!= AppUsageRange.YESTERDAY ){
                                currentRange = AppUsageRange.YESTERDAY
                                loadUsageData()
                            }
                        }
                        2->{ if(currentRange!= AppUsageRange.LAST_7_DAYS ){
                            currentRange = AppUsageRange.LAST_7_DAYS
                            loadUsageData()
                        }

                        }
                        3->{
                            if(currentRange!= AppUsageRange.LAST_30_DAYS ){
                                currentRange = AppUsageRange.LAST_30_DAYS
                                loadUsageData()
                            }
                        }
                    }
                    // 更新内容区域
                    // 可选：更新自定义视图的样式（比如改变文字颜色）
                    updateTabStyle(it, true)

                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.let {
                    // 取消选中样式
                    updateTabStyle(it, false)
                }
            }
            override fun onTabReselected(tab: TabLayout.Tab?) {
                // 可忽略，或做刷新操作
            }
        })
        binding.tabLayout.getTabAt(0)?.let {
            it.select()                     // 触发监听器
            // 保险起见，再手动更新一次
            updateTabStyle(it, true)
        }

    }

    private fun loadUsageData(isShowLoading: Boolean=false) {
        if(isShowLoading){
            handleLoadingState(true)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val snapshot = AppUsageAnalyzer.loadSnapshot(this@AppUsageActivity, currentRange)
            withContext(Dispatchers.Main) {
                if(isShowLoading){
                    handleLoadingState(false)
                }
                currentSnapshot = snapshot
                renderUsageSnapshot(snapshot)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        if (!UsageStatsHelper.hasPermission(this) ) {
            if (UsageStatsHelper.hasNavigatedBefore()){
                readyGoThenKill(MainActivity::class.java)
            }
        }else{
            loadUsageData(true)
        }
    }

    override fun onDestroy() {
        UsageStatsHelper.resetNavigatedFlag()
        usageDataPermissionDialog?.release()
        usageDataPermissionDialog = null
        super.onDestroy()
    }




    private fun updateTabStyle(tab: TabLayout.Tab, isSelected: Boolean) {
        val customView = tab.customView ?: return
        val titleTv = customView.findViewById<TextView>(R.id.tv_title)
        val color = if (isSelected) {
            getColor(R.color.white)
        } else {
            getColor(R.color.hint_color)
        }
        titleTv.setTextColor(color)
    }
    data class TabItem( val title: String)
}