package t.mma.hooks.items.homesidepanel

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import t.mma.hooks.items.antirecall.DynamicRecallGradient
import t.mma.hooks.items.dynamicbadge.DynamicBadgeSettings

/** Local foreground only: no layout changes, smoke, bitmap allocations or global redraws. */
internal class AvatarHeaderEffect(private val host: View) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private val outline = Path()
    private val trail = Path()
    private val measure = PathMeasure()
    private val matrix = Matrix()
    private var shader: SweepGradient? = null
    private var colors = ""
    private var period = 2400
    private var perimeter = 0f
    private var width = -1
    private var height = -1
    private var running = false
    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            if (!host.isAttachedToWindow || !host.isShown || !host.hasWindowFocus()) {
                stop()
                return
            }
            invalidateSelf()
            host.postOnAnimation(this)
        }
    }
    fun update(value: String, speed: Int) {
        period = speed.coerceIn(DynamicBadgeSettings.MIN_SPEED, DynamicBadgeSettings.MAX_SPEED)
        if (colors != value || width != host.width || height != host.height) {
            colors = value
            width = host.width
            height = host.height
            val density = host.resources.displayMetrics.density
            val inset = 3f * density
            rect.set(inset, inset, width - inset, height - inset)
            outline.reset()
            if (rect.width() > 0 && rect.height() > 0) outline.addRoundRect(rect, rect.height() / 2f, rect.height() / 2f, Path.Direction.CW)
            measure.setPath(outline, true)
            perimeter = measure.length
            val c = DynamicRecallGradient.parts(value).map(Color::parseColor)
            shader = SweepGradient(rect.centerX(), rect.centerY(),
                intArrayOf(c[0], c[1], c[2], c[0]), floatArrayOf(0f, 1f / 3f, 2f / 3f, 1f))
        }
        if (!running) {
            running = true
            host.postOnAnimation(frame)
        }
        invalidateSelf()
    }
    fun stop() {
        running = false
        host.removeCallbacks(frame)
    }
    override fun draw(canvas: Canvas) {
        if (!running || perimeter <= 0f || !host.isShown) return
        val density = host.resources.displayMetrics.density
        val phase = (SystemClock.uptimeMillis() % period).toFloat() / period
        matrix.setRotate(phase * 360f, rect.centerX(), rect.centerY())
        shader?.setLocalMatrix(matrix)
        paint.shader = shader
        // Faint complete capsule, then a bright moving arc with a soft layered halo.
        paint.strokeWidth = 1.2f * density
        paint.alpha = 85
        canvas.drawPath(outline, paint)
        trail.reset()
        val start = phase * perimeter
        val end = start + perimeter * .38f
        measure.getSegment(start, minOf(end, perimeter), trail, true)
        if (end > perimeter) measure.getSegment(0f, end - perimeter, trail, true)
        paint.strokeWidth = 5f * density
        paint.alpha = 25
        canvas.drawPath(trail, paint)
        paint.strokeWidth = 3f * density
        paint.alpha = 65
        canvas.drawPath(trail, paint)
        paint.strokeWidth = 1.4f * density
        paint.alpha = 255
        canvas.drawPath(trail, paint)
        paint.shader = null
    }
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Drawable opacity is not used")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
