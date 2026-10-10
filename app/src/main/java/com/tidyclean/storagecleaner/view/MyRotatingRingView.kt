package com.tidyclean.storagecleaner.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.view.isVisible
import com.tidyclean.storagecleaner.R


import kotlin.apply
import kotlin.let
import kotlin.ranges.coerceAtLeast
import kotlin.ranges.coerceIn

class MyRotatingRingView
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val DEFAULT_STROKE_WIDTH = 20f
        private const val DEFAULT_RING_COLOR = "#007AFF"
        private const val DEFAULT_BACKGROUND_COLOR = "#33007AFF"
        private const val DEFAULT_ANIMATION_DURATION = 800L
        private const val DEFAULT_ARC_LENGTH = 180f
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val rectF = RectF()
    private var startAngle = 0f

    // 缓存计算值
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    // 可配置属性
    private var ringStrokeWidth = DEFAULT_STROKE_WIDTH
    private var ringColor = Color.parseColor(DEFAULT_RING_COLOR)
    private var backgroundColor = Color.parseColor(DEFAULT_BACKGROUND_COLOR)
    private var animationDuration = DEFAULT_ANIMATION_DURATION
    private var arcLength = DEFAULT_ARC_LENGTH

    // Choreographer 动画控制
    private var isAnimating = false
    private var lastFrameTimeNanos = 0L
    private var accumulatedAngle = 0f

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAnimating) return

            // 计算时间差（毫秒）
            val deltaMs = if (lastFrameTimeNanos == 0L) {
                0f
            } else {
                (frameTimeNanos - lastFrameTimeNanos) / 1_000_000f
            }

            lastFrameTimeNanos = frameTimeNanos

            // 根据动画时长计算旋转速度（度/毫秒），避免除以零
            if (animationDuration > 0 && deltaMs > 0) {
                accumulatedAngle += (360f / animationDuration) * deltaMs
                accumulatedAngle %= 360f
                startAngle = accumulatedAngle
                invalidate()
            }

            // 注册下一帧回调
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        initAttributes(attrs)
    }

    private fun setupPaints() {
        ringPaint.apply {
            strokeWidth = ringStrokeWidth
            color = ringColor
        }

        backgroundPaint.apply {
            strokeWidth = ringStrokeWidth
            color = backgroundColor
        }
    }

    private fun initAttributes(attrs: AttributeSet?) {
        if (attrs == null) return

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.RotatingRingView)
        try {
            ringStrokeWidth = typedArray.getDimension(
                R.styleable.RotatingRingView_ringWidth,
                DEFAULT_STROKE_WIDTH
            )
            ringColor = typedArray.getColor(
                R.styleable.RotatingRingView_ringColor,
                ringColor
            )
            backgroundColor = typedArray.getColor(
                R.styleable.RotatingRingView_backgroundColor,
                backgroundColor
            )

            animationDuration = typedArray.getInt(
                R.styleable.RotatingRingView_animationDuration,
                DEFAULT_ANIMATION_DURATION.toInt()
            ).toLong()

            arcLength = typedArray.getFloat(
                R.styleable.RotatingRingView_arcLength,
                DEFAULT_ARC_LENGTH
            ).coerceIn(0f, 360f)
        } finally {
            typedArray.recycle()
        }

        setupPaints()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateDimensions(w, h)
    }

    private fun updateDimensions(width: Int, height: Int) {
        centerX = width / 2f
        centerY = height / 2f
        radius = kotlin.comparisons.minOf(centerX, centerY) - ringStrokeWidth / 2f

        rectF.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
    }

    override fun onDraw(canvas: Canvas) {
        // 绘制背景圆环
        canvas.drawArc(rectF, 0f, 360f, false, backgroundPaint)
        // 绘制旋转圆环
        canvas.drawArc(rectF, startAngle, arcLength, false, ringPaint)
    }

    /**
     * 开启动画，可重复调用，若已在运行则不会重复注册回调。
     */
    fun startAnimation() {
        if (isAnimating) return
        isAnimating = true
        lastFrameTimeNanos = 0L // 首帧跳过时间差，避免跳变
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    /**
     * 停止动画，移除所有待执行的回调。
     */
    fun stopAnimation() {
        if (!isAnimating) return
        isAnimating = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    fun setRingColor(@ColorInt color: Int) {
        ringColor = color
        ringPaint.color = color
        invalidate()
    }

    fun setMyBackgroundColor(@ColorInt color: Int) {
        backgroundColor = color
        backgroundPaint.color = color
        invalidate()
    }

    fun setRingWidth(width: Float) {
        ringStrokeWidth = width
        ringPaint.strokeWidth = width
        backgroundPaint.strokeWidth = width
        updateDimensions(width = width.toInt(), height = height) // 重新计算圆环区域
        invalidate()
    }

    fun setAnimationDuration(duration: Long) {
        animationDuration = duration
        // 正在运行时无需重启，速度会立即根据新时长计算
    }

    fun setArcLength(length: Float) {
        arcLength = length.coerceIn(0f, 360f)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isVisible) {
            startAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    @SuppressLint("InlinedApi")
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        when (visibility) {
            VISIBLE -> startAnimation()
            else -> stopAnimation()
        }
    }

    // 推荐：给 View 设置最小尺寸，避免因布局错误导致看不到圆弧
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minSize = (ringStrokeWidth * 2 + arcLength).toInt().coerceAtLeast(suggestedMinimumWidth)
        val w = resolveSize(minSize, widthMeasureSpec)
        val h = resolveSize(minSize, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }
}