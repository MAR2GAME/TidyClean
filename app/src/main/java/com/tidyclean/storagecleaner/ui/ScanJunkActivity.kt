package com.tidyclean.storagecleaner.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.activity.OnBackPressedCallback

import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.lifecycleScope
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.adapter.ExpandableListAdapter
import com.tidyclean.storagecleaner.advertise.AdAreaKeys
import com.tidyclean.storagecleaner.advertise.AdvertisePageMediator
import com.tidyclean.storagecleaner.base.activity.BaseViewActivity
import com.tidyclean.storagecleaner.base.command.log
import com.tidyclean.storagecleaner.base.command.rateUS
import com.tidyclean.storagecleaner.base.command.readyGoThenKill
import com.tidyclean.storagecleaner.base.command.safeClick
import com.tidyclean.storagecleaner.base.config.AppConfig
import com.tidyclean.storagecleaner.bean.CleanFilesBean
import com.tidyclean.storagecleaner.bean.JunkMotherBean
import com.tidyclean.storagecleaner.databinding.ActivityScanJunkBinding
import com.tidyclean.storagecleaner.utils.JunkFileScanner
import com.tidyclean.storagecleaner.utils.SizeUtil
import com.tidyclean.storagecleaner.view.CleanHintDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import kotlin.jvm.java

class ScanJunkActivity : BaseViewActivity<ActivityScanJunkBinding>(){

    companion object {
        private const val MIN_SCAN_DISPLAY_TIME = 4000L
        private const val SCAN_UPDATE_INTERVAL = 200L
        private const val JUNK_CATEGORY_COUNT = 4
    }

    // UI State
    private var isScanning: Boolean = false
    private var isScanFinished: Boolean = false
    private var totalJunkSize: Long = 0
    private var scanStartTime: Long = 0

    // Adapter & Data
    private lateinit var expandableListAdapter: ExpandableListAdapter
    private val junkMotherBeans = mutableListOf<JunkMotherBean>()

    // Components
    private var cleanHintDialog: CleanHintDialog? = null
    private lateinit var scanner: JunkFileScanner

    // Coroutines
    private var scanJob: Job? = null
    private var timeJob: Job? = null

    private var rotateAnimator: ObjectAnimator? = null


    override fun initializeActivity() {
        super.initializeActivity()
        initJunkData()
        initAdapter()
        setupBackPressHandler()
        startScanning()
        initializeViews()
        val details=intent.extras?.getString("details")
        val referrer_name=intent.extras?.getString("referrer_name")
        if(!TextUtils.isEmpty( details)){
            log("enter_corefeatures_page",mapOf("details" to details!! ,"referrer_name" to referrer_name!!))
        }
    }
    private fun initializeViews() {
        binding.ivScanBack.safeClick { onBack() }
        binding.tvRate.setOnClickListener { rateUS() }
    }

    private fun startScanning() {
        isScanning = true
        isScanFinished = false
        scanStartTime = System.currentTimeMillis()
        startScanJob()
        startTimeMonitoringJob()
    }


    private fun startRotate() {
        if (rotateAnimator?.isRunning == true) return
        rotateAnimator = ObjectAnimator.ofFloat(
            binding.ivScan2,
            View.ROTATION,
            0f, 360f
        ).apply {
            duration = 2000L                     // 一圈 2 秒，按需调整
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()  // 匀速，避免一顿一顿
            start()
        }
    }
    private fun stopRotate() {
        rotateAnimator?.cancel()
        rotateAnimator = null
    }

    private fun startScanJob() {
        startRotate()
        scanJob = lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    scanner.scanJunkFiles()
                }
            } catch (e: Exception) {
                // Handle scanning error - could show user feedback
                e.printStackTrace()
            } finally {
                isScanFinished = true
            }
        }

        lifecycleScope.launch(Dispatchers.Main) {
            var dotCount = 0
            while (true) {
                binding.tvState.text =
                    getString(R.string.scanning) + ".".repeat(dotCount)
                dotCount = (dotCount + 1) % 4
                delay(400)
            }
        }
    }

    private fun startTimeMonitoringJob() {
        timeJob = lifecycleScope.launch {
            while (isActive) {
                if (shouldFinishScan()) {
                    onScanFinished()
                    return@launch
                }
                delay(SCAN_UPDATE_INTERVAL)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun onScanFinished() {
        stopRotate()
        isScanning = false
        updateJunkListVisibility()
        updateUIAfterScan()
    }


    @SuppressLint("UseCompatLoadingForDrawables")
    private fun updateUIAfterScan() {
        binding.tvState.visibility=View.GONE
        binding.ivScan1.visibility= View.GONE
        binding.ivScan2.visibility= View.GONE
        binding.tvSize.visibility=View.VISIBLE
        binding.tvHint.visibility=View.VISIBLE
        binding.llHint.visibility=View.VISIBLE
        binding.btClean.isClickable = true
        binding.btClean.isEnabled = true
        binding.btClean.safeClick {
            navigateToCleanFiles()
        }
        totalJunkSize = junkMotherBeans.sumOf { it.size }
        if (totalJunkSize > 0) {
            setupUIForJunkFound()
        } else {
            setupUIForNoJunk()
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun setupUIForJunkFound() {
        binding.llBg.setBackgroundResource(R.drawable.scan_bg)
        binding.tvSize.text = SizeUtil.formatSize(totalJunkSize)
        binding.tvCleanHint.text = getString(R.string.junk_delete_hint)
        binding.llHint.setBackgroundColor(Color.parseColor("#1AF5753A"))
        binding.tvCleanHint.setTextColor(getColor(R.color.junk_hint_color))
        binding.btClean.setTextColor(getColor(R.color.junk_hint_color))
        binding.btClean.text=getString(R.string.clean_now)
        binding.tvRate.visibility = View.GONE
    }


    @SuppressLint("UseCompatLoadingForDrawables")
    private fun setupUIForNoJunk() {
        binding.llBg.setBackgroundResource(R.drawable.scan_shape)
        binding.tvSize.text = "0KB"
        binding.tvCleanHint.text = getString(R.string.very_clean)
        binding.llHint.setBackgroundColor(Color.parseColor("#1A04C07D"))
        binding.tvCleanHint.setTextColor(Color.parseColor("#04C07D"))
        binding.tvRate.visibility = View.VISIBLE
        binding.btClean.setTextColor(getColor(R.color.theme_color))
        binding.btClean.text=getString(R.string.ok)
    }

    private fun updateJunkListVisibility() {
        repeat(JUNK_CATEGORY_COUNT) { index ->
            junkMotherBeans[index].isShowCheck = true
        }
        expandableListAdapter.notifyDataSetChanged()
    }

    private fun shouldFinishScan(): Boolean {
        return isScanFinished &&
                (System.currentTimeMillis() - scanStartTime) >MIN_SCAN_DISPLAY_TIME
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    private fun onBack() {
        if (isScanning) {
            showScanningDialog()
        } else {
            handleBackAfterScan()
        }
    }

    private fun handleBackAfterScan() {
        if (totalJunkSize > 0) {
            showCleanHintDialog()
        } else {
            AdvertisePageMediator.showFeatureEntryInterstitialByAreaKey( this@ScanJunkActivity,
                AdAreaKeys.Interstitial.RETURN_FROM_JUNK_CLEAN_ADV){
                readyGoThenKill(MainActivity::class.java)
            }
        }
    }

    private fun showCleanHintDialog() {
        val hint = createFormattedHintText()
        getOrCreateCleanHintDialog().toShow(hint, true)
    }
    private fun createFormattedHintText(): SpannableString {
        val formattedSize = SizeUtil.formatSize(totalJunkSize)
        val hintText = "$formattedSize ${getString(R.string.cleaning_in_progress)}"
        val hint = SpannableString(hintText)
        hint.setSpan(
            ForegroundColorSpan(getColor(R.color.theme_color)),
            0,
            formattedSize.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return hint
    }
    private fun showScanningDialog() {
        getOrCreateCleanHintDialog().toShow(getString(R.string.scanning_in_progress), false)
    }

    private fun getOrCreateCleanHintDialog(): CleanHintDialog {
        if (cleanHintDialog == null) {
            cleanHintDialog = CleanHintDialog(this).apply {
                setmOnClickListener(object : CleanHintDialog.onClickListener {
                    override fun onNext(isTodelete: Boolean) {
//                        if (isTodelete) {
//                            navigateToCleanFiles()
//                        }

                    }

                    override fun onStop() {
                        readyGoThenKill(MainActivity::class.java)
                    }
                })
            }
        }
        return cleanHintDialog!!
    }
    private fun navigateToCleanFiles() {
        val bundle = Bundle()
        bundle.putString(AppConfig.NEXT_TAG, getString(R.string.remove_junk))
        if(totalJunkSize>0){
            EventBus.getDefault().postSticky(CleanFilesBean(totalJunkSize, junkMotherBeans))
            bundle.putString(AppConfig.TASK_TITLE, "" + SizeUtil.formatSize(totalJunkSize)+getString(R.string.junk_files))
            bundle.putString(AppConfig.TASK_CONTENT, getString(R.string.have_been_cleared))
            readyGoThenKill(CleanJunkOrFilesActivity::class.java, bundle)
        }else{
            readyGoThenKill(CompleteActivity::class.java, bundle)
        }
    }



    private fun initJunkData() {
        val junkCategories = listOf(
            JunkMotherBean(getString(R.string.obsolete_files), 0, mutableListOf()),
            JunkMotherBean(getString(R.string.residuals), 0, mutableListOf()),
            JunkMotherBean(getString(R.string.ad_junk), 0, mutableListOf()),
            JunkMotherBean(getString(R.string.trash_items), 0, mutableListOf())
        )
        junkMotherBeans.addAll(junkCategories)
        scanner = JunkFileScanner(this, junkMotherBeans)
    }
    private fun initAdapter() {
        expandableListAdapter = ExpandableListAdapter(this, junkMotherBeans)
        expandableListAdapter.setmonCheckChangeLister(createCheckChangeListener())
        binding.exJunk.setAdapter(expandableListAdapter)
    }

    private fun createCheckChangeListener(): ExpandableListAdapter.OnCheckChangeLister {
        return object : ExpandableListAdapter.OnCheckChangeLister {
            @SuppressLint("UseCompatLoadingForDrawables")
            override fun onCheckChange() {
                totalJunkSize = junkMotherBeans
                    .filter { it.isChecked }
                    .sumOf { it.size }

                updateCleanButtonState()
            }
        }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun updateCleanButtonState() {
        if (totalJunkSize > 0) {
            binding.tvSize.text = SizeUtil.formatSize(totalJunkSize)
        } else {
            binding.tvSize.text = "0KB"
        }
    }


    override fun onDestroy() {
        cleanHintDialog?.release()
        cleanHintDialog=null
        timeJob?.cancel()
        scanJob?.cancel()
        timeJob = null
        scanJob = null
        scanner.destroy()
        stopRotate()
        super.onDestroy()
    }

}