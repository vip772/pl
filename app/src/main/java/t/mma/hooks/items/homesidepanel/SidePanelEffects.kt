package t.mma.hooks.items.homesidepanel

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** One overlay, bounded particles, and no timer while hidden or paused. */
internal class SidePanelEffects(context: Context, private val lightning: Boolean) : View(context) {
    var running = false
        set(value) { field = value; if (value) invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var gradientKey = ""
    private var gradient: SweepGradient? = null
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!running || !isShown) return
        val prefs = HomeSidePanelSettings.preferences(context)
        val time = android.os.SystemClock.uptimeMillis() / 1000f
        if (lightning) {
            if (!prefs.getBoolean(SidePanelAppearance.LIGHTNING, false)) return
            val key = "${width}:${height}:" + prefs.getString(SidePanelAppearance.COLORS, "")
            if (key != gradientKey) {
                gradientKey = key
                val colors = SidePanelAppearance.colors(context)
                gradient = SweepGradient(width / 2f, height / 2f, colors + colors[0], null)
            }
            paint.shader = gradient
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = resources.displayMetrics.density * prefs.getFloat(SidePanelAppearance.WIDTH, 1.5f).coerceIn(.5f, 6f)
            val radius = min(width, height) * .43f
            repeat(4) { bolt ->
                path.reset()
                repeat(13) { step ->
                    val angle = time * 1.6f + bolt * PI.toFloat() / 2f + step * .035f
                    val r = radius + if (step % 2 == 0) 0f else radius * .12f
                    val x = width / 2f + cos(angle) * r; val y = height / 2f + sin(angle) * r
                    if (step == 0) path.moveTo(x,y) else path.lineTo(x,y)
                }
                canvas.drawPath(path, paint)
            }
            paint.shader = null
        } else {
            if (!prefs.getBoolean(SidePanelAppearance.EFFECT, false)) return
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(255, 151, 191)
            paint.textSize = resources.displayMetrics.density * 20f
            repeat(10) { index ->
                val phase = (time * .16f + index * .173f) % 1f
                paint.alpha = (sin(phase * PI).toFloat().coerceAtLeast(0f) * 110).toInt()
                val x = width * ((index * .271f + .08f) % .92f)
                val y = height * (1f - phase)
                canvas.drawText(if (prefs.getString(SidePanelAppearance.EFFECT_KIND, "heart") == "heart") "❤" else "✨", x, y, paint)
            }
        }
        postInvalidateDelayed(50L)
    }
}