package com.example.chessassistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 10f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f
    private var shouldDraw = false

    fun drawArrow(x1: Float, y1: Float, x2: Float, y2: Float) {
        fromX = x1
        fromY = y1
        toX = x2
        toY = y2
        shouldDraw = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (shouldDraw) {
            canvas.drawLine(fromX, fromY, toX, toY, paint)
            canvas.drawCircle(toX, toY, 15f, paint) // Simple arrowhead
        }
    }
}
