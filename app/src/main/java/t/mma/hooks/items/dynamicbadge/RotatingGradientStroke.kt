package t.mma.hooks.items.dynamicbadge

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient

/** Shared drawing primitive for number badges and retained recalled messages. */
internal object RotatingGradientStroke {
    private class Entry(val start: Int, val end: Int, val x: Float, val y: Float) {
        val shader = SweepGradient(x, y, intArrayOf(start, end, start), floatArrayOf(0f, .5f, 1f))
        val matrix = Matrix()
    }
    private val cache = ThreadLocal.withInitial { java.util.WeakHashMap<Paint, Entry>() }
    fun draw(canvas: Canvas, paint: Paint, rect: RectF, radius: Float, start: Int, end: Int, phase: Float) {
        paint.style = Paint.Style.STROKE
        val entries = cache.get()
        val x = rect.centerX()
        val y = rect.centerY()
        val entry = entries[paint]?.takeIf { it.start == start && it.end == end && it.x == x && it.y == y }
            ?: Entry(start, end, x, y).also { entries[paint] = it }
        entry.matrix.setRotate(phase, x, y)
        entry.shader.setLocalMatrix(entry.matrix)
        paint.shader = entry.shader
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null
    }
}