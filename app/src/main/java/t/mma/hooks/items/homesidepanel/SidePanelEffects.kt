package t.mma.hooks.items.homesidepanel

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** One overlay, bounded particles, and no timer while hidden or paused. */
internal class SidePanelEffects(context: Context) : View(context) {
    var running = false
        set(value) { field = value; if (value) invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!running || !isShown) return
        val prefs = HomeSidePanelSettings.preferences(context)
        val time = android.os.SystemClock.uptimeMillis() / 1000f
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
        postInvalidateDelayed(50L)
    }
}