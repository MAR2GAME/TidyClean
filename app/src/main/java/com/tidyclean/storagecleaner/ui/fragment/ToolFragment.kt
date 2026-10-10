package com.tidyclean.storagecleaner.ui.fragment

import android.os.Bundle
import android.util.Log
import android.widget.RelativeLayout
import androidx.recyclerview.widget.GridLayoutManager
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.advertise.AdAreaKeys
import com.tidyclean.storagecleaner.advertise.AdvertisePageMediator
import com.tidyclean.storagecleaner.base.command.log
import com.tidyclean.storagecleaner.base.command.readyGo
import com.tidyclean.storagecleaner.base.config.AppConfig
import com.tidyclean.storagecleaner.base.fragment.BaseMvvmFragment
import com.tidyclean.storagecleaner.bean.ToScanEvent
import com.tidyclean.storagecleaner.bean.ToolBeans
import com.tidyclean.storagecleaner.databinding.FragmentToolBinding
import com.tidyclean.storagecleaner.ui.LoadingActivity
import com.tidyclean.storagecleaner.viewmodle.ToolFragmentViewModel
import org.greenrobot.eventbus.EventBus

class ToolFragment : BaseMvvmFragment<FragmentToolBinding, ToolFragmentViewModel>(){

    private lateinit  var toolAdapter: BaseQuickAdapter<ToolBeans, BaseViewHolder>
    private var toolBeans=mutableListOf<ToolBeans>()
    override fun getViewModelClass(): Class<ToolFragmentViewModel> = ToolFragmentViewModel::class.java


    override fun init() {
        initAdapter()
        binding.rlBt.setOnClickListener {
            log("click_corefeatures_btn",mapOf("details" to "batteryInfo","referrer_name" to "homepage"))
            AdvertisePageMediator.showFeatureEntryInterstitialByAreaKey(requireActivity(), AdAreaKeys.Interstitial.ENTER_BATTERY_INFO){
                val bundle = Bundle().apply {
                    putString(AppConfig.NEXT_TAG, getString(R.string.battery_info))
                    putString("details", "batteryInfo")
                    putString("referrer_name", "homepage")
                }
                requireActivity().readyGo(LoadingActivity::class.java, bundle)
            }
        }
        binding.rlDv.setOnClickListener {
            log("click_corefeatures_btn",mapOf("details" to "deviceInfo","referrer_name" to "homepage"))
            AdvertisePageMediator.showFeatureEntryInterstitialByAreaKey( requireActivity(),AdAreaKeys.Interstitial.ENTER_DEVICE_INFO){
                val bundle = Bundle().apply {
                    putString(AppConfig.NEXT_TAG, getString(R.string.device_info))
                    putString("details", "deviceInfo")
                    putString("referrer_name", "homepage")
                }
                requireActivity().readyGo(LoadingActivity::class.java, bundle)
            }
        }
    }
    fun  initAdapter(){
        toolAdapter= object : BaseQuickAdapter<ToolBeans, BaseViewHolder>(R.layout.item_tool){
            override fun convert(
                holder: BaseViewHolder,
                item: ToolBeans
            ) {
                holder.setText(R.id.tv_name,  item.title)
                holder.setImageResource(R.id.iv_res,item.res)
                holder.getView<RelativeLayout>(R.id.rl_content).setOnClickListener{
                    var tag=item.title
                    if(tag==getString(R.string.large_files)){
                        tag=getString(R.string.large_file_cleaner)
                    }
                    EventBus.getDefault().post(ToScanEvent(tag))
                }
            }
        }
        binding.rcAction.adapter=toolAdapter
        binding.rcAction.layoutManager= GridLayoutManager(requireActivity(),2)
        toolBeans.add(ToolBeans(R.mipmap.ic_image,getString(R.string.images)))
        toolBeans.add(ToolBeans(R.mipmap.ic_video,getString(R.string.video)))
        toolBeans.add(ToolBeans(R.mipmap.ic_audio,getString(R.string.audio)))
        toolBeans.add(ToolBeans(R.mipmap.ic_documents,getString(R.string.documents)))
        toolBeans.add(ToolBeans(R.mipmap.ic_file,getString(R.string.large_files)))
        toolAdapter.setList(toolBeans)
    }
}