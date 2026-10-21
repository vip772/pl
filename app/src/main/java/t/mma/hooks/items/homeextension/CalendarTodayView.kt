package t.mma.hooks.items.homeextension

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import t.mma.hooks.items.dynamicbadge.RotatingGradientStroke
import t.mma.hooks.items.dynamicbadge.SelectableBorderEffect

/** Calendar owns its animation, independently of unread badge preferences. */
internal class CalendarTodayView(context: Context, private val effectEnabled: Boolean, private val effectStyle: Int) : TextView(context) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = RectF()
    private val visibleArea = Rect()
    private val effect = SelectableBorderEffect()
    private var observer: ViewTreeObserver? = null
    private var scheduled = false
    private val scroll = ViewTreeObserver.OnScrollChangedListener { resumeEffect() }
    private val frame = object : Runnable {
        override fun run() {
            scheduled = false
            if (!canAnimate()) return
            invalidate()
            scheduled = true
            postOnAnimation(this)
        }
    }
    private fun canAnimate() = effectEnabled && isAttachedToWindow && isShown && hasWindowFocus() && getGlobalVisibleRect(visibleArea)
    private fun resumeEffect() {
        if (!scheduled && canAnimate()) { scheduled = true; postOnAnimation(frame) }
    }
    private fun stopEffect() { removeCallbacks(frame); scheduled = false }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observer = viewTreeObserver.also { it.addOnScrollChangedListener(scroll) }
        resumeEffect()
    }
    override fun onDetachedFromWindow() {
        stopEffect()
        observer?.takeIf { it.isAlive }?.removeOnScrollChangedListener(scroll)
        observer = null
        super.onDetachedFromWindow()
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) resumeEffect() else stopEffect()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // Framework may call this before this class's fields have been initialized.
        if (isAttachedToWindow) { if (isShown) resumeEffect() else stopEffect() }
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) { if (visibility == View.VISIBLE) resumeEffect() else stopEffect() }
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resumeEffect()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!effectEnabled) return
        val density = resources.displayMetrics.density
        val inset = 4f * density
        border.set(inset, inset, width - inset, height - inset)
        if (border.width() <= 0 || border.height() <= 0) return
        val now = SystemClock.uptimeMillis()
        val phase = (now % 3000L) / 3000f
        ink.color = Color.rgb(126, 245, 255)
        ink.strokeWidth = 1.5f * density
        ink.style = Paint.Style.STROKE
        if (effectStyle == 0) RotatingGradientStroke.draw(canvas, ink, border, border.height()/2f, Color.rgb(126,245,255), Color.rgb(255,117,202), phase*360f)
        else effect.draw(canvas, ink, border, border.height()/2f, effectStyle, phase, density, now, 3000)
    }
}
