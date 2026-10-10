package com.tidyclean.storagecleaner.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import androidx.lifecycle.lifecycleScope
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.advertise.AdAreaKeys
import com.tidyclean.storagecleaner.advertise.AdvertisePageMediator
import com.tidyclean.storagecleaner.base.activity.BaseViewActivity
import com.tidyclean.storagecleaner.base.command.getFunctionBundle
import com.tidyclean.storagecleaner.base.command.hasStoragePermission
import com.tidyclean.storagecleaner.base.command.log
import com.tidyclean.storagecleaner.base.command.readyGo
import com.tidyclean.storagecleaner.base.command.readyGoThenKill
import com.tidyclean.storagecleaner.base.config.AppConfig
import com.tidyclean.storagecleaner.bean.TaskListBean
import com.tidyclean.storagecleaner.bean.ToScanEvent
import com.tidyclean.storagecleaner.databinding.ActivityCompleteBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import kotlin.collections.filter
import kotlin.jvm.java

class CompleteActivity : BaseViewActivity<ActivityCompleteBinding>(){

    var taskListBeans: MutableList<TaskListBean> = mutableListOf()
    var tag=""

    override fun initializeActivity() {
        super.initializeActivity()
       initTask()
        val bundle = intent.extras
        tag = bundle?.getString(AppConfig.NEXT_TAG).orEmpty()
        val filtered = taskListBeans.filter { it.tag != tag }
        val randomSelected = if (filtered.size <= 2) filtered else filtered.shuffled().take(2)
        var title=bundle?.getString(AppConfig.TASK_TITLE)
        if(!TextUtils.isEmpty(bundle?.getString(AppConfig.TASK_CONTENT))){
            binding.tvHint.text=bundle?.getString(AppConfig.TASK_CONTENT)
            binding.tvHint.visibility= View.VISIBLE
        }
        if(!TextUtils.isEmpty(title)){
            binding.tvTitle.text=title
        }else{
            binding.tvTitle.text=getString(R.string.task_complete)
        }

        binding.tvTask1.text=randomSelected[0].title
        binding.tvHint1.text=randomSelected[0].hint
        binding.ivTask1.setImageResource(randomSelected[0].res)
        binding.tvNext1.text=randomSelected[0].next
        binding.tvNext1.setOnClickListener {
            toNext(randomSelected[0].tag,randomSelected[0].clazz)
        }
        binding.tvTask2.text=randomSelected[1].title
        binding.tvHint2.text=randomSelected[1].hint
        binding.ivTask2.setImageResource(randomSelected[1].res)
        binding.tvNext2.text=randomSelected[1].next
        binding.tvNext2.setOnClickListener {
            toNext(randomSelected[1].tag,randomSelected[1].clazz)
        }
        binding.ivBack.setOnClickListener {
            handleBackPress()
        }

        lifecycleScope.launch {
            delay(500)
            AdvertisePageMediator.showCompletionInterstitial(this@CompleteActivity, tag) {

            }
        }
        onBackPressedDispatcher.addCallback(this,object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }


    fun toNext(tag: String,clazz: Class<*>){
        when(tag){
            getString(R.string.remove_junk)->{
                if(!hasStoragePermission()){
                    EventBus.getDefault().post(ToScanEvent(getString(R.string.remove_junk)))
                    finish()
                }else{
                    log("click_corefeatures_btn",mapOf("details" to "junkClean","referrer_name" to "taskcompleted"))
                    AdvertisePageMediator.showFeatureEntryInterstitial(
                        this,
                        getString(R.string.remove_junk)
                    ) {
                        readyGoThenKill(clazz,getFunctionBundle("junkClean","taskcompleted"))
                    }
                }
            }

            getString(R.string.device_info)->{
                log("click_corefeatures_btn",mapOf("details" to "deviceInfo","referrer_name" to "taskcompleted"))
                AdvertisePageMediator.showFeatureEntryInterstitialByAreaKey(this, AdAreaKeys.Interstitial.ENTER_DEVICE_INFO){
                    val bundle = Bundle().apply {
                        putString(AppConfig.NEXT_TAG, getString(R.string.device_info))
                        putString("details", "deviceInfo")
                        putString("referrer_name", "taskcompleted")
                    }
                    readyGoThenKill(LoadingActivity::class.java, bundle)
                }
            }
            getString(R.string.battery_info)->{
                log("click_corefeatures_btn",mapOf("details" to "batteryInfo","referrer_name" to "taskcompleted"))
                AdvertisePageMediator.showFeatureEntryInterstitialByAreaKey(this, AdAreaKeys.Interstitial.ENTER_BATTERY_INFO){
                    val bundle = Bundle().apply {
                        putString(AppConfig.NEXT_TAG, getString(R.string.battery_info))
                        putString("details", "batteryInfo")
                        putString("referrer_name", "taskcompleted")
                    }
                    readyGoThenKill(LoadingActivity::class.java, bundle)
                }
            }
            getString(R.string.app_usage)->{
                log("click_corefeatures_btn",mapOf("details" to "appUsage","referrer_name" to "taskcompleted"))
                AdvertisePageMediator.showFeatureEntryInterstitial(this, getString(R.string.app_usage)){
                    readyGoThenKill(AppUsageActivity::class.java,getFunctionBundle("appUsage","taskcompleted"))
                }
            }
            else->{
                log("click_corefeatures_btn",mapOf("details" to "speakerCleaner","referrer_name" to "taskcompleted"))
                AdvertisePageMediator.showFeatureEntryInterstitial(this, getString(R.string.speaker_cleaner)){
                    readyGoThenKill(SpeakerCleanerActivity::class.java,getFunctionBundle("speakerCleaner","taskcompleted"))
                }
            }
        }
    }

    private fun handleBackPress() {
        if(tag!=getString(R.string.remove_junk)){
            finish()
        }else{
            readyGoThenKill(MainActivity::class.java)
        }
    }


    fun initTask(){
        taskListBeans.add(
            TaskListBean(
                getString(R.string.remove_junk),
                R.mipmap.ic_cleaner,
                getString(R.string.remove_junk),
                getString(R.string.remove_junk_hint),
                ScanJunkActivity::class.java,
                getString(R.string.scan_now)
            )
        )
        taskListBeans.add(
            TaskListBean(
                getString(R.string.battery_info), R.mipmap.ic_tool_bt,
                getString(R.string.battery_info), getString(R.string.battery_info_hint),
                BatteryInfoActivity::class.java, getString(R.string.view_now)
            )
        )
        taskListBeans.add(
            TaskListBean(
                getString(R.string.app_usage),
                R.mipmap.ic_app_usage,
                getString(R.string.app_usage),
                getString(R.string.app_usage_hint),
                AppUsageActivity::class.java,
                getString(R.string.view_now)
            )
        )
        taskListBeans.add(
            TaskListBean(
                getString(R.string.speaker_cleaner), R.mipmap.ic_sp,
                getString(R.string.speaker_cleaner), getString(R.string.speaker_cleaner_hint),
                SpeakerCleanerActivity::class.java, getString(R.string.check_now)
            )
        )
        taskListBeans.add(
            TaskListBean(
                getString(R.string.device_info), R.mipmap.ic_dv,
                getString(R.string.device_info), getString(R.string.device_info_hint),
                DeviceInfoActivity::class.java, getString(R.string.view_now)
            )
        )

    }









}