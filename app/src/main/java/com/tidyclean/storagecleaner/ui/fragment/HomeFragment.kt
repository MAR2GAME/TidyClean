package com.tidyclean.storagecleaner.ui.fragment

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.animation.AccelerateDecelerateInterpolator
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.advertise.AdAreaKeys
import com.tidyclean.storagecleaner.advertise.AdvertisePageMediator
import com.tidyclean.storagecleaner.base.command.getFunctionBundle
import com.tidyclean.storagecleaner.base.command.log
import com.tidyclean.storagecleaner.base.command.readyGo
import com.tidyclean.storagecleaner.base.fragment.BaseMvvmFragment
import com.tidyclean.storagecleaner.bean.StorageInfo
import com.tidyclean.storagecleaner.bean.ToScanEvent
import com.tidyclean.storagecleaner.databinding.FragmentHomeBinding
import com.tidyclean.storagecleaner.ui.AppUsageActivity
import com.tidyclean.storagecleaner.ui.SpeakerCleanerActivity
import com.tidyclean.storagecleaner.utils.SizeUtil
import com.tidyclean.storagecleaner.viewmodle.HomeFragmentViewModel
import org.greenrobot.eventbus.EventBus

class HomeFragment: BaseMvvmFragment<FragmentHomeBinding, HomeFragmentViewModel>()  {

    private var pulseAnimator: ObjectAnimator? = null

    override fun init() {
        setupObservers()
        loadData()
        binding.tvClean.setOnClickListener {
            log("click_corefeatures_btn",mapOf("details" to "junkClean","referrer_name" to "homepage"))
            EventBus.getDefault().post(ToScanEvent(getString(R.string.remove_junk)))
        }

        binding.rlAppUsage.setOnClickListener {
            log("click_corefeatures_btn",mapOf("details" to "appUsage","referrer_name" to "homepage"))
            AdvertisePageMediator.showFeatureEntryInterstitial(requireActivity(), getString(R.string.app_usage)){
                requireActivity().readyGo(AppUsageActivity::class.java,getFunctionBundle("appUsage","homepage") )
            }
        }

        binding.rlSpeaker.setOnClickListener {
            log("click_corefeatures_btn",mapOf("details" to "speakerCleaner","referrer_name" to "homepage"))
            AdvertisePageMediator.showFeatureEntryInterstitialByAreaKey(requireActivity(),
                AdAreaKeys.Interstitial.ENTER_SPEAKER_CLEAN){
                requireActivity().readyGo(SpeakerCleanerActivity::class.java,getFunctionBundle("speakerCleaner","homepage"))
            }
        }




    }

    private fun loadData() {
        viewModel.getStorageInfo()
    }


    private fun setupObservers() {
        viewModel.infoData.observe(viewLifecycleOwner) { info ->
            updateStorageInfo(info)
        }
    }

    private fun updateStorageInfo(storageInfo: StorageInfo) {
        if (storageInfo.usagePercentage > 0) {
            binding.tvUsagePercentage.text = "${storageInfo.usagePercentage.toInt()}%"
            binding.tvMemory.text =
                "${SizeUtil.formatSize(storageInfo.usedSpace)}/${SizeUtil.formatSize(storageInfo.totalSpace)}"
        }
    }



    override fun onResume() {
        super.onResume()
        resumePulseAnimation()
    }

    override fun onPause() {
        super.onPause()
        pausePulseAnimation()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            pausePulseAnimation()
        } else {
            resumePulseAnimation()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    override fun getViewModelClass(): Class<HomeFragmentViewModel> =
        HomeFragmentViewModel::class.java


    /**
     * 暂停脉冲动画
     */
    private fun pausePulseAnimation() {
        pulseAnimator?.cancel()
    }

    /**
     * 恢复脉冲动画（若已停止或未创建则重新开始）
     */
    private fun resumePulseAnimation() {
        // 检查动画是否正在运行，避免重复创建
        if (pulseAnimator?.isRunning != true) {
            startPulseAnimation()
        }
    }

    /**
     * 启动脉冲动画，内部会先取消旧动画防止叠加
     */
    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        val scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.1f, 1.0f)
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.tvClean, scaleX).apply {
            duration = 700
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }
}