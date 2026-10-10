package t.mma.hooks.items.dynamicbadge

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient

/** Shared drawing primitive for number badges and retained recalled messages. */
internal object RotatingGradientStroke {
    fun draw(canvas: Canvas, paint: Paint, rect: RectF, radius: Float, start: Int, end: Int, phase: Float) {
        paint.style = Paint.Style.STROKE
        paint.shader = SweepGradient(rect.centerX(), rect.centerY(), intArrayOf(start, end, start),
            floatArrayOf(0f, .5f, 1f)).apply {
            setLocalMatrix(Matrix().apply { setRotate(phase, rect.centerX(), rect.centerY()) })
        }
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null
    }
}