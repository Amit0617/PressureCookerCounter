package com.example.pressurecookercounter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val amplitudes = mutableListOf<Int>() // raw amplitudes
    private val maxPoints = 100 // max number of points to show on screen

    fun addAmplitude(amp: Int) {
        amplitudes.add(amp)
        if (amplitudes.size > maxPoints) amplitudes.removeAt(0)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val midY = height / 2f
        val maxAmp = amplitudes.maxOrNull()?.toFloat() ?: 1f

        val widthPerSample = width / maxPoints.toFloat()
        amplitudes.forEachIndexed { i, amp ->
            val normAmp = amp / maxAmp
            val lineHeight = normAmp * height * 0.8f
            canvas.drawLine(
                i * widthPerSample,
                midY - lineHeight / 2,
                i * widthPerSample,
                midY + lineHeight / 2,
                paint
            )
        }
    }
}
