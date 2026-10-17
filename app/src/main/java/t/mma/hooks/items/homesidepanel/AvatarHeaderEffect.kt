package t.mma.hooks.items.homesidepanel

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import t.mma.hooks.items.antirecall.DynamicRecallGradient
import t.mma.hooks.items.dynamicbadge.DynamicBadgeSettings

/** Local foreground only: no layout changes, smoke, bitmap allocations or global redraws. */
internal class AvatarHeaderEffect(private val host: View, private val content: View? = null) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()
    private val contentRect = RectF()
    private val contentLocation = Rect()
    private val previousContentRect = RectF()
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
    private var strokeDp = 1.4f
    private var lightning = false
    private var lightningScale = 1f
    private var customInset: Float? = null
    private val bolt = Path()
    private val branches = Path()
    private val point = FloatArray(2)
    private val tangent = FloatArray(2)
    private var boltTick = -1L
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
    fun update(value: String, speed: Int, strokeWidth: Float, electric: Boolean, scaleLightningWidth: Boolean = false, avatarInsetDp: Float? = null) {
        val nextWidth = strokeWidth.coerceIn(0.5f, 4f)
        lightningScale = if (scaleLightningWidth) nextWidth / 1.4f else 1f
        if (strokeDp != nextWidth || lightning != electric || customInset != avatarInsetDp) {
            customInset = avatarInsetDp
            width = -1
            strokeDp = nextWidth
            lightning = electric
            boltTick = -1L
        }
        period = speed.coerceIn(DynamicBadgeSettings.MIN_SPEED, DynamicBadgeSettings.MAX_SPEED)
        contentRect.set(0f, 0f, host.width.toFloat(), host.height.toFloat())
        val text = content as? TextView
        if (text != null && host is ViewGroup) {
            val layout = text.layout
            if (layout != null && layout.lineCount > 0) {
                contentLocation.set(0, 0, text.width, text.height)
                host.offsetDescendantRectToMyCoords(text, contentLocation)
                var left = Float.MAX_VALUE
                var right = 0f
                for (line in 0 until layout.lineCount) {
                    left = minOf(left, layout.getLineLeft(line))
                    right = maxOf(right, layout.getLineRight(line))
                }
                val density = host.resources.displayMetrics.density
                // 路径在真实字形外侧；闪电的抖动空间由父容器提供，不再被文字 View 裁切。
                val gap = (if (lightning) 6f else 4f) * density
                val metrics = text.paint.fontMetrics
                val baseline = contentLocation.top + text.baseline.toFloat()
                contentRect.set(
                    contentLocation.left + text.totalPaddingLeft + left - text.scrollX - gap,
                    baseline + metrics.ascent - gap,
                    contentLocation.left + text.totalPaddingLeft + right - text.scrollX + gap,
                    baseline + layout.getLineBaseline(layout.lineCount - 1) - layout.getLineBaseline(0) + metrics.descent + gap
                )
                if (!contentRect.intersect(2f * density, 2f * density, host.width - 2f * density, host.height - 2f * density)) {
                    contentRect.setEmpty()
                }
            }
        }
        if (colors != value || width != host.width || height != host.height || contentRect != previousContentRect) {
            previousContentRect.set(contentRect)
            colors = value
            width = host.width
            height = host.height
            val density = host.resources.displayMetrics.density
            val inset = (customInset ?: if (lightning) 9f else strokeDp / 2f + 2f) * density
            rect.set(contentRect)
            if (text == null) rect.inset(inset, inset)
            outline.reset()
            if (rect.width() > 0 && rect.height() > 0) outline.addRoundRect(rect, rect.height() / 2f, rect.height() / 2f, Path.Direction.CW)
            measure.setPath(outline, true)
            perimeter = measure.length
            boltTick = -1L
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
        if (lightning) {
            drawLightning(canvas, density)
            paint.shader = null
            return
        }
        // The entire perimeter stays visible while the gradient and highlight move.
        paint.strokeWidth = strokeDp * density
        paint.alpha = 220
        canvas.drawPath(outline, paint)
        trail.reset()
        val start = phase * perimeter
        val end = start + perimeter * .38f
        measure.getSegment(start, minOf(end, perimeter), trail, true)
        if (end > perimeter) measure.getSegment(0f, end - perimeter, trail, true)
        paint.strokeWidth = (strokeDp + 3f) * density
        paint.alpha = 25
        canvas.drawPath(trail, paint)
        paint.strokeWidth = (strokeDp + 1.5f) * density
        paint.alpha = 65
        canvas.drawPath(trail, paint)
        paint.strokeWidth = strokeDp * density
        paint.alpha = 255
        canvas.drawPath(trail, paint)
        paint.shader = null
    }
    private fun noise(index: Int, tick: Long): Float {
        var n = index * 374761393 + tick.toInt() * 668265263
        n = (n xor (n ushr 13)) * 1274126177
        return ((n xor (n ushr 16)) and 65535) / 32767.5f - 1f
    }
    private fun drawLightning(canvas: Canvas, density: Float) {
        // Rebuild only at the electrical flicker cadence; paths and sample arrays are reused.
        val tick = SystemClock.uptimeMillis() / (period / 32L).coerceAtLeast(18L)
        if (tick != boltTick) {
            boltTick = tick
            bolt.reset()
            branches.reset()
            val count = (perimeter / (5f * density)).toInt().coerceIn(48, 256)
            for (i in 0 until count) {
                measure.getPosTan(perimeter * i / count, point, tangent)
                val displacement = noise(i, tick) * 2.4f * density
                val x = point[0] - tangent[1] * displacement
                val y = point[1] + tangent[0] * displacement
                if (i == 0) bolt.moveTo(x, y) else bolt.lineTo(x, y)
                if (i % 9 == 0) {
                    val reach = (2f + kotlin.math.abs(noise(i + 7, tick)) * 1.8f) * density
                    branches.moveTo(x, y)
                    branches.lineTo(x - tangent[1] * reach + tangent[0] * density,
                        y + tangent[0] * reach + tangent[1] * density)
                }
            }
            bolt.close()
        }
        paint.strokeWidth = 4f * density * lightningScale
        paint.alpha = 35
        canvas.drawPath(bolt, paint)
        paint.strokeWidth = 2.2f * density * lightningScale
        paint.alpha = 100
        canvas.drawPath(bolt, paint)
        paint.strokeWidth = 1f * density * lightningScale
        paint.alpha = 255
        canvas.drawPath(bolt, paint)
        paint.strokeWidth = 0.65f * density * lightningScale
        paint.alpha = 200
        canvas.drawPath(branches, paint)
        paint.shader = null
        paint.color = Color.WHITE
        paint.alpha = 210
        paint.strokeWidth = 0.4f * density * lightningScale
        canvas.drawPath(bolt, paint)
    }
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Drawable opacity is not used")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
