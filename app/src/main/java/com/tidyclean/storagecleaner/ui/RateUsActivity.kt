package com.tidyclean.storagecleaner.ui

import android.R.attr.rating
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.databinding.DataBindingUtil.setContentView
import com.tidyclean.storagecleaner.R
import com.tidyclean.storagecleaner.base.activity.BaseViewActivity
import com.tidyclean.storagecleaner.base.command.rateUS
import com.tidyclean.storagecleaner.databinding.ActivityRateUsBinding

class RateUsActivity : BaseViewActivity<ActivityRateUsBinding>(){
    private lateinit var starViews: List<ImageView>
    private var currentRating = 0                      // 0..5
    private var starBoundaries = mutableListOf<Pair<Int, Int>>() // 缓存左右边界(px)
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null



    override fun initializeActivity() {
        super.initializeActivity()
        initializeViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.starLayout?.let { layout ->
            globalLayoutListener?.let {
                layout.viewTreeObserver.removeOnGlobalLayoutListener(it)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initializeViews() {
        // 获取星星 ImageView
        starViews = listOf(
            binding.star1,
            binding.star2,
            binding.star3,
            binding.star4,
            binding.star5,
        )
        updateStars(currentRating)
        // 等待布局完成，缓存每颗星的左右边界
       binding. starLayout?.let { layout ->
            globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
                starBoundaries.clear()
                for (star in starViews) {
                    starBoundaries.add(star.left to star.right)
                }
                layout.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
            }
            layout.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        }
        // 设置滑动触摸监听
        binding.starLayout?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    binding.starLayout?.parent?.requestDisallowInterceptTouchEvent(true)
                    val newRating = getRatingForTouchPosition(event.x)
                    if (newRating != currentRating) {
                        currentRating = newRating
                        updateStars(currentRating)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    binding.starLayout?.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }

        // 提交按钮
        binding.tvCancel.setOnClickListener {
            finish()
        }
        binding.btSubmit.setOnClickListener {
            Toast.makeText(this@RateUsActivity, getString(R.string.feedback), Toast.LENGTH_SHORT)
                .show()
            if(currentRating==5){
                rateUS()
            }
            finish()
        }
    }

    /**
     * 根据触摸点 X 坐标计算应点亮几颗星
     * @param touchX 相对于 starLayout 的 X 坐标
     */
    private fun getRatingForTouchPosition(touchX: Float): Int {
        // 优先使用缓存边界
        if (starBoundaries.isNotEmpty()) {
            for (i in starBoundaries.indices) {
                if (touchX < starBoundaries[i].first) return i
            }
            return starBoundaries.size
        }

        // 降级方案：实时获取 left（要求布局已完成）
        if (starViews.isNotEmpty() && starViews[0].width > 0) {
            for (i in starViews.indices) {
                if (touchX < starViews[i].left) return i
            }
            return starViews.size
        }

        // 完全未就绪时返回当前评分，不更新
        return currentRating
    }

    /**
     * 刷新星星图片
     * 注意：请确保 res/mipmap/star_filled.png 和 star_empty.png 存在
     */
    private fun updateStars(rating: Int) {
        for (i in starViews.indices) {
            val resId = if (i < rating) R.mipmap.star_filled else R.mipmap.star_empty
            starViews[i].setImageResource(resId)
        }
    }


}