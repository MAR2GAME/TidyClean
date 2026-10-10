package com.tidyclean.storagecleaner.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.tidyclean.storagecleaner.bean.AppUsageDonutSlice
import kotlin.apply
import kotlin.collections.forEach
import kotlin.collections.sumOf
import kotlin.math.min

class AppUsageDonutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color =  0xFFE6EAF0.toInt()
        strokeCap = Paint.Cap.ROUND
    }

    private val arcBounds = RectF()
    private var slices: List<AppUsageDonutSlice> = emptyList()

    fun setSlices(slices: List<AppUsageDonutSlice>) {
        this.slices = slices
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val strokeWidth = min(width, height) *  0.08f
        ringPaint.strokeWidth = strokeWidth
        backgroundPaint.strokeWidth = strokeWidth

        val radiusInset = strokeWidth / 2 + 6f
        arcBounds.set(
            radiusInset,
            radiusInset,
            width - radiusInset,
            height - radiusInset
        )

        canvas.drawArc(arcBounds, -90f, 360f, false, backgroundPaint)

        val total = slices.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0f) return

        var startAngle = -90f
        slices.forEach { slice ->
            val sweepAngle = (slice.value / total) * 360f
            ringPaint.color = slice.color
            canvas.drawArc(arcBounds, startAngle, sweepAngle, false, ringPaint)
            startAngle += sweepAngle
        }
    }
}
