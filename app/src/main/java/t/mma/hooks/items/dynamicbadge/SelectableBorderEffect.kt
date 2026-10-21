package t.mma.hooks.items.dynamicbadge

import android.graphics.*

/** Reused geometry and buffers for electrical borders and the contact-style orbit trail. */
internal class SelectableBorderEffect {
    private val outline = Path()
    private val bolt = Path()
    private val measure = PathMeasure()
    private val point = FloatArray(2)
    private val tangent = FloatArray(2)
    private val previous = RectF()
    private var previousRadius = -1f
    private var tick = Long.MIN_VALUE
    private var previousDensity = 0f
    private var length = 0f

    fun draw(canvas: Canvas, paint: Paint, rect: RectF, radius: Float, style: Int, phase: Float, density: Float, millis: Long, period: Int) {
        if (previous != rect || previousRadius != radius) {
            previous.set(rect); previousRadius = radius
            outline.reset(); outline.addRoundRect(rect, radius, radius, Path.Direction.CW)
            measure.setPath(outline, true); length = measure.length; tick = Long.MIN_VALUE
        }
        if (length <= 0f) return
        val alpha = paint.alpha
        val width = paint.strokeWidth
        val oldStyle = paint.style
        if (style == 2) {
            paint.style = Paint.Style.FILL
            // Same clockwise head and 24 fading tail samples as ContactOrbitRow.
            for (i in 24 downTo 0) {
                val strength = 1f - i / 25f
                val distance = ((phase - i * .006f + 1f) % 1f) * length
                measure.getPosTan(distance, point, null)
                paint.alpha = (alpha * strength * strength).toInt()
                canvas.drawCircle(point[0], point[1], density * .15f + (width / 2f + density - density * .15f) * strength, paint)
            }
        } else {
            val nextTick = millis / (period / 32L).coerceAtLeast(18L)
            if (tick != nextTick || previousDensity != density) {
                tick = nextTick; previousDensity = density; bolt.reset()
                val count = (length / (5f * density)).toInt().coerceIn(48, 256)
                for (i in 0 until count) {
                    measure.getPosTan(length * i / count, point, tangent)
                    var n = i * 374761393 + tick.toInt() * 668265263
                    n = (n xor (n ushr 13)) * 1274126177
                    val displacement = (((n xor (n ushr 16)) and 65535) / 32767.5f - 1f) * 2.4f * density
                    val x = point[0] - tangent[1] * displacement
                    val y = point[1] + tangent[0] * displacement
                    if (i == 0) bolt.moveTo(x, y) else bolt.lineTo(x, y)
                }
                bolt.close()
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = width * 2.2f; paint.alpha = alpha * 35 / 255; canvas.drawPath(bolt, paint)
            paint.strokeWidth = width; paint.alpha = alpha; canvas.drawPath(bolt, paint)
        }
        paint.alpha = alpha; paint.strokeWidth = width; paint.style = oldStyle
    }
}
