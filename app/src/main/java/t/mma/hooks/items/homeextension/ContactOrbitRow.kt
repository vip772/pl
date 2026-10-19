package t.mma.hooks.items.homeextension

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import t.mma.hooks.items.antirecall.AntiRecallSettings
import t.mma.hooks.items.antirecall.DynamicRecallGradient
import t.mma.preferences.HchatStorage

/** One clock for all avatars. Scroll/focus/layout events resume an otherwise idle row. */
internal class ContactOrbitRow(context: Context) : LinearLayout(context) {
    private val orbits = ArrayList<OrbitAvatar>(8)
    private val visibleRect = Rect()
    private var observer: ViewTreeObserver? = null
    private var scheduled = false
    private val scroll = ViewTreeObserver.OnScrollChangedListener { refresh() }
    private val frame = object : Runnable {
        override fun run() {
            scheduled = false
            if (!isAttachedToWindow || !isShown || !hasWindowFocus()) return
            val progress = (SystemClock.uptimeMillis() % 3000L) / 3000f
            var visible = false
            for (orbit in orbits) {
                if (orbit.isShown && orbit.getGlobalVisibleRect(visibleRect)) {
                    orbit.progress = progress
                    orbit.invalidate()
                    visible = true
                }
            }
            if (visible) { scheduled = true; postOnAnimation(this) }
        }
    }
    fun wrap(avatar: ImageView): View = OrbitAvatar(context, avatar).also { orbits.add(it) }
    private fun refresh() {
        if (!scheduled && isAttachedToWindow && isShown && hasWindowFocus()) {
            scheduled = true
            postOnAnimation(frame)
        }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observer = viewTreeObserver.also { it.addOnScrollChangedListener(scroll) }
        updateColors()
        refresh()
    }
    override fun onDetachedFromWindow() {
        observer?.takeIf { it.isAlive }?.removeOnScrollChangedListener(scroll)
        observer = null
        removeCallbacks(frame); scheduled = false
        super.onDetachedFromWindow()
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) { updateColors(); refresh() }
        else { removeCallbacks(frame); scheduled = false }
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        // This callback can occur from the superclass constructor.
        if (visibility == VISIBLE) post { refresh() }
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        refresh()
    }
    private fun updateColors() {
        val prefs = HchatStorage.preferences(context, AntiRecallSettings.PREFS_NAME)
        val colors = DynamicRecallGradient.parts(prefs.getString(AntiRecallSettings.KEY_DYNAMIC_COLOR,
            DynamicRecallGradient.DEFAULT).orEmpty()).map { Color.parseColor(it) }
        for (orbit in orbits) orbit.updateColors(intArrayOf(colors[0], colors[1], colors[2], colors[0]))
    }

    private class OrbitAvatar(context: Context, avatar: ImageView) : FrameLayout(context) {
        private val density = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val outline = Path()
        private val measure = PathMeasure()
        private val bounds = RectF()
        private val point = FloatArray(2)
        private var length = 0f
        var progress = 0f
        init {
            // 5dp reserve: orbit centre 2dp beyond the avatar, plus the dot radius.
            addView(avatar, LayoutParams((46 * density + .5f).toInt(), (46 * density + .5f).toInt(), Gravity.CENTER))
        }
        fun updateColors(colors: IntArray) {
            paint.shader = SweepGradient(width / 2f, height / 2f, colors, floatArrayOf(0f, 1f / 3f, 2f / 3f, 1f))
        }
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val half = 25 * density // 23dp avatar half-size + 2dp outside its edge
            bounds.set(w / 2f - half, h / 2f - half, w / 2f + half, h / 2f + half)
            outline.reset()
            outline.addRoundRect(bounds, 16 * density, 16 * density, Path.Direction.CW)
            measure.setPath(outline, true)
            length = measure.length
            val prefs = HchatStorage.preferences(context, AntiRecallSettings.PREFS_NAME)
            val colors = DynamicRecallGradient.parts(prefs.getString(AntiRecallSettings.KEY_DYNAMIC_COLOR,
                DynamicRecallGradient.DEFAULT).orEmpty()).map { Color.parseColor(it) }
            updateColors(intArrayOf(colors[0], colors[1], colors[2], colors[0]))
        }
        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            if (length <= 0f) return
            // Reuse the path, sample buffer and paint; no allocations in the drawing loop.
            for (i in 24 downTo 0) {
                val strength = 1f - i / 25f
                val distance = ((progress - i * .006f + 1f) % 1f) * length
                measure.getPosTan(distance, point, null)
                paint.alpha = (255 * strength * strength).toInt()
                canvas.drawCircle(point[0], point[1], density * (0.2f + 1.3f * strength), paint)
            }
        }
    }
}
