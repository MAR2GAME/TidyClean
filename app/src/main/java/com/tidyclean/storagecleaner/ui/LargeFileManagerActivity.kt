package com.tidyclean.storagecleaner.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chad.library.adapter.base.BaseMultiItemQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.advertise.AdAreaKeys
import com.tidyclean.storagecleaner.advertise.AdvertisePageMediator.showInterstitial
import com.tidyclean.storagecleaner.base.activity.BaseViewActivity
import com.tidyclean.storagecleaner.base.command.log
import com.tidyclean.storagecleaner.base.command.readyGoThenKill
import com.tidyclean.storagecleaner.base.command.safeClick
import com.tidyclean.storagecleaner.base.config.AppConfig
import com.tidyclean.storagecleaner.bean.CleanLargeFileBean
import com.tidyclean.storagecleaner.bean.LargeFile
import com.tidyclean.storagecleaner.bean.LargeFileEvent
import com.tidyclean.storagecleaner.bean.TagBean
import com.tidyclean.storagecleaner.databinding.ActivityLargeFileManagerBinding
import com.tidyclean.storagecleaner.utils.FileManagerCategory
import com.tidyclean.storagecleaner.utils.SizeUtil
import com.tidyclean.storagecleaner.view.CleanFileDialog
import com.tidyclean.storagecleaner.view.LargeFileDialog
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.DateFormat
import kotlin.collections.filter
import kotlin.collections.isNotEmpty
import kotlin.jvm.java

class LargeFileManagerActivity : BaseViewActivity<ActivityLargeFileManagerBinding>() {


    private lateinit var category: FileManagerCategory
    private lateinit var appAdapter: LargeFileAdapter

    private var cleanFileDialog: CleanFileDialog? = null
    private var largeFileDialog: LargeFileDialog? = null

    private val allLargeFiles = mutableListOf<LargeFile>()
    private val selectLargeFiles = mutableListOf<LargeFile>()
    private val tagBeansType = mutableListOf<TagBean>()
    private val tagBeansSize = mutableListOf<TagBean>()

    private var deleteSize: Long = 0
    private var isSelectAll = false
    private val dateFormat by lazy(LazyThreadSafetyMode.NONE) {
        DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.MEDIUM,
            resources.configuration.locales[0]
        )
    }








    override fun initializeActivity() {
        super.initializeActivity()
        EventBus.getDefault().register(this)
        category = FileManagerCategory.fromTag(this, intent.extras?.getString(AppConfig.NEXT_TAG))
        when(getString(category.displayNameRes)){
            getString(R.string.images)->{
                binding.ivHint.setImageResource(R.mipmap.ic_image)
                binding.tvHint.text=getString(R.string.manager_images)
            }
            getString(R.string.video)->{
                binding.ivHint.setImageResource(R.mipmap.ic_video)
                binding.tvHint.text=getString(R.string.manager_videos)
            }
            getString(R.string.audio)->{
                binding.ivHint.setImageResource(R.mipmap.ic_audio)
                binding.tvHint.text=getString(R.string.manager_audios)
            }
            getString(R.string.large_files)->{
                binding.ivHint.setImageResource(R.mipmap.ic_file)
                binding.tvHint.text=getString(R.string.manager_files)
            }
            getString(R.string.documents)->{
                binding.ivHint.setImageResource(R.mipmap.ic_documents)
                binding.tvHint.text=getString(R.string.manager_documents)
            }
        }

        binding.tvTitle.text=getString(category.displayNameRes)
        binding.tvType.text = getString(category.displayNameRes)
        setupBackPressHandler()
        getTagBeans()
        setupViews()
        val details=intent.extras?.getString("details")
        val referrer_name=intent.extras?.getString("referrer_name")
        if(!TextUtils.isEmpty( details)){
            log("enter_corefeatures_page",mapOf("details" to details!! ,"referrer_name" to referrer_name!!))
        }

    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    fun onBack(){
        showInterstitial(this, AdAreaKeys.Interstitial.RETURN_FROM_FILE_MANAGE_ADV){
            readyGoThenKill(MainActivity::class.java)
        }
    }

    private fun setupViews() {
        with(binding) {
            ivBack.safeClick { onBack()}
            rlType.setOnClickListener {
                showTypeFilterPop()
            }
            rlSize.setOnClickListener { showSizeFilterPop() }
            btDelete.setOnClickListener { handleDeleteClick() }
        }
    }

    private fun handleDeleteClick() {
        when {
            binding.btDelete.text.toString() == getString(R.string.back_to_main_page) -> onBack()
            selectLargeFiles.isNotEmpty() -> showCleanFileDialog()
        }
    }

    private fun showCleanFileDialog() {
        if (cleanFileDialog == null) {
            cleanFileDialog = CleanFileDialog(this).apply {
                setHint(getString(R.string.delete_selected_files_hint))
                setmOnClickListener(object : CleanFileDialog.onClickListener {
                    override fun onNext() {
                        navigateToCleanFileActivity()
                    }
                })
            }
        }
        cleanFileDialog?.showDialog()
    }

    private fun navigateToCleanFileActivity() {
        EventBus.getDefault().postSticky(
            CleanLargeFileBean(
                deleteSize,
                selectLargeFiles.toMutableList()
            )
        )
        val bundle = Bundle().apply {
            putString(AppConfig.NEXT_TAG, getString(category.routeTagRes))
        }
        readyGoThenKill(CleanJunkOrFilesActivity::class.java, bundle)
    }



    private fun showTypeFilterPop() {
        if (category.supportsTypeFilter && allLargeFiles.isNotEmpty()) {
            showFilterDialog(false)
        }
    }

    private fun showSizeFilterPop() {
        if (allLargeFiles.isNotEmpty()) {
            showFilterDialog(true)
        }
    }

    private fun showFilterDialog(isSizeFilter: Boolean) {
        if (largeFileDialog == null) {
            largeFileDialog = LargeFileDialog(this)
            largeFileDialog?.setmOnClickListener(object : LargeFileDialog.onClickListener {
                override fun onClick(isSize: Boolean, pos: Int) {
                    val tagList = if (isSize) tagBeansSize else tagBeansType
                    val textView = if (isSize) binding.tvSize else binding.tvType
                    if (textView.text.toString() != tagList[pos].tagName) {
                        tagList.forEach { it.isSelected = false }
                        tagList[pos].isSelected = true
                        textView.text = tagList[pos].tagName
                        filterData()
                    }
                }
            })
        }
        val tagList = if (isSizeFilter) tagBeansSize else tagBeansType
        largeFileDialog?.toUpdate(tagList, isSizeFilter)
        largeFileDialog?.show()
    }

    private fun filterData() {
        val typeFiltered = filterByType()
        val sizeFiltered = filterBySize(typeFiltered)
        updateUIAfterFilter(sizeFiltered)
    }

    private fun updateUIAfterFilter(filteredData: List<LargeFile>) {
        if (filteredData.isNotEmpty()) {
            binding.rlNodata.visibility = View.GONE
            updateSelectionState(filteredData)
        } else {
            binding.rlNodata.visibility = View.VISIBLE
            isSelectAll = false
        }
        appAdapter.setList(filteredData)
        updateSelectAllCheckbox()
    }

    private fun updateSelectionState(filteredData: List<LargeFile>) {
        filteredData.forEach { file ->
            file.isSelected = selectLargeFiles.contains(file)
        }
        isSelectAll = filteredData.isNotEmpty() && selectLargeFiles.containsAll(filteredData)
    }

    private fun filterByType(): List<LargeFile> {
        if (!category.supportsTypeFilter) {
            return allLargeFiles
        }
        return when (binding.tvType.text.toString()) {
            getString(R.string.all_types) -> allLargeFiles
            getString(R.string.video) -> allLargeFiles.filter { it.type.startsWith("video/") }
            getString(R.string.audio) -> allLargeFiles.filter { it.type.startsWith("audio/") }
            getString(R.string.images) -> allLargeFiles.filter { it.type.startsWith("image/") }
            getString(R.string.apk) -> allLargeFiles.filter { it.name.endsWith(".apk", ignoreCase = true) }
            else -> allLargeFiles.filter {
                !it.type.startsWith("image/") &&
                        !it.type.startsWith("audio/") &&
                        !it.type.startsWith("video/") &&
                        !it.name.endsWith(".apk", ignoreCase = true)
            }
        }
    }

    private fun filterBySize(data: List<LargeFile>): List<LargeFile> {
        return when (binding.tvSize.text.toString()) {
            getString(R.string.all_size) -> data
            "10MB" -> data.filter { it.size < 30 * 1024 * 1024L }
            "30MB" -> data.filter { it.size in (30 * 1024 * 1024L) until (100 * 1024 * 1024L) }
            "100MB" -> data.filter { it.size in (100 * 1024 * 1024L) until (500 * 1024 * 1024L) }
            "500MB" -> data.filter { it.size in (500 * 1024 * 1024L) until (1024 * 1024 * 1024L) }
            else -> data.filter { it.size >= 1024 * 1024 * 1024L }
        }
    }

    private fun toggleSelectAll() {
        isSelectAll = !isSelectAll
        currentVisibleFiles().forEach { file ->
            file.isSelected = isSelectAll
            if (isSelectAll) {
                if (!selectLargeFiles.contains(file)) {
                    selectLargeFiles.add(file)
                }
            } else {
                selectLargeFiles.remove(file)
            }
        }
        syncSelectionSummary()
        appAdapter.notifyDataSetChanged()
        updateButtonState()
    }





    private fun getTagBeans() {
        tagBeansSize.clear()
        if (category.supportsTypeFilter) {
            binding.rlType.visibility= View.VISIBLE
            tagBeansType.clear()
            tagBeansType.add(TagBean(getString(R.string.all_types), true))
            tagBeansType.add(TagBean(getString(R.string.images), false))
            tagBeansType.add(TagBean(getString(R.string.video), false))
            tagBeansType.add(TagBean(getString(R.string.audio), false))
            tagBeansType.add(TagBean(getString(R.string.apk), false))
            tagBeansType.add(TagBean(getString(R.string.other), false))
        } else{
            binding.rlType.visibility= View.GONE
        }
        tagBeansSize.add(TagBean(getString(R.string.all_size), true))
        tagBeansSize.add(TagBean("10MB", false))
        tagBeansSize.add(TagBean("30MB", false))
        tagBeansSize.add(TagBean("100MB", false))
        tagBeansSize.add(TagBean("500MB", false))
        tagBeansSize.add(TagBean("1GB", false))
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: LargeFileEvent) {
        allLargeFiles.addAll(event.data)
        if (event.data.isEmpty()) {
            showNoDataState()
            binding.tvTotalSize.text="0MB"
        } else {
            showDataState()
            initAdapter()
            binding.rlSelectAll.setOnClickListener { toggleSelectAll() }
            appAdapter.setList(event.data)
            binding.tvTotalSize.text= SizeUtil.formatSize(event.total)
        }
        EventBus.getDefault().removeStickyEvent(event)
    }

    private fun initAdapter() {
        appAdapter = LargeFileAdapter()
        binding.rcFile.apply {
            adapter = appAdapter
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@LargeFileManagerActivity, RecyclerView.VERTICAL, false)
        }
    }

    private inner class LargeFileAdapter :
        BaseMultiItemQuickAdapter<LargeFile, BaseViewHolder>() {

        init {
            addItemType(0, R.layout.item_largefile)
        }

        override fun convert(holder: BaseViewHolder, item: LargeFile) {
            bindFileItem(holder, item)
        }

        private fun bindFileItem(holder: BaseViewHolder, item: LargeFile) {
            holder.setText(R.id.tv_name, item.name)
            holder.setText(
                R.id.tv_info,
                "${formatSize(item.size)}  ${dateFormat.format(item.date)}"
            )
            setupImage(holder.getView(R.id.iv_largefile), item)
            setupPlayIconVisibility(holder.getView(R.id.iv_play), item)
            setupCheckboxState(holder.getView(R.id.iv_check), item)
            holder.getView<RelativeLayout>(R.id.rl_function).setOnClickListener {
                item.isSelected = !item.isSelected
                setData(appAdapter.getItemPosition(item), item)
                if (item.isSelected) {
                    if (!selectLargeFiles.contains(item)) {
                        selectLargeFiles.add(item)
                    }
                } else {
                    selectLargeFiles.remove(item)
                }
                syncSelectionSummary()
                updateButtonState()
            }
        }
    }

    private fun updateButtonState() {
        with(binding.btDelete) {
            if (selectLargeFiles.isEmpty()) {
                isClickable = false
                isEnabled = false
                text = getString(R.string.delete)
                background = getDrawable(R.drawable.btn_unclick)
            } else {
                isClickable = true
                isEnabled = true
                text = getString(R.string.delete_size_format, formatSize(deleteSize))
                background = getDrawable(R.drawable.btn_nor)
            }
        }
        updateSelectAllCheckbox()
    }

    private fun updateSelectAllCheckbox() {
        binding.ivCheck.setImageResource(if (isSelectAll) R.mipmap.ic_check else R.mipmap.ic_check_nor)
    }

    private fun formatSize(size: Long): String = SizeUtil.formatSize(
        size)

    private fun setupImage(imageView: ImageView, item: LargeFile) {
        val placeholderRes = resolvePlaceholder(item)
        if (item.thumbnailUri == null) {
            imageView.setImageResource(placeholderRes)
        } else {
            Glide.with(this)
                .load(item.thumbnailUri)
                .placeholder(placeholderRes)
                .error(placeholderRes)
                .into(imageView)
        }
    }

    private fun resolvePlaceholder(item: LargeFile): Int {
        return when {
            item.type.startsWith("image/") -> R.mipmap.ic_image
            item.type.startsWith("video/") -> R.mipmap.ic_video
            item.type.startsWith("audio/") -> R.mipmap.ic_audio
            else -> category.placeholderIconRes
        }
    }

    private fun setupPlayIconVisibility(playIcon: ImageView, item: LargeFile) {
        playIcon.visibility = if (item.type.startsWith("video/")) View.VISIBLE else View.GONE
    }

    private fun setupCheckboxState(checkBox: ImageView, item: LargeFile) {
        checkBox.setImageResource(if (item.isSelected) R.mipmap.ic_check else R.mipmap.ic_check_nor)
    }

    private fun showNoDataState() {
        binding.rlNodata.visibility = View.VISIBLE
        binding.rlSelectAll.visibility = View.GONE
        binding.btDelete.text = getString(R.string.back_to_main_page)
    }



    private fun showDataState() {
        binding.btDelete.isClickable = false
        binding.btDelete.isEnabled = false
        binding.btDelete.background = getDrawable(R.drawable.btn_unclick)
    }

    private fun currentVisibleFiles(): List<LargeFile> {
        return appAdapter.data.filter { it.itemType == 0 }
    }

    private fun syncSelectionSummary() {
        deleteSize = selectLargeFiles.sumOf { it.size }
        val visibleFiles = currentVisibleFiles()
        isSelectAll = visibleFiles.isNotEmpty() && selectLargeFiles.containsAll(visibleFiles)
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        cleanupResources()
        super.onDestroy()
    }

    private fun cleanupResources() {
        largeFileDialog?.relese()
        largeFileDialog = null
        cleanFileDialog?.release()
        cleanFileDialog = null
    }






}