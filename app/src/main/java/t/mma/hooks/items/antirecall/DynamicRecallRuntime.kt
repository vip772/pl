package t.mma.hooks.items.antirecall

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import t.mma.hooks.items.dynamicbadge.DynamicBadgeSettings
import t.mma.preferences.HchatStorage
import t.mma.utils.HLog
import java.util.WeakHashMap
import java.lang.ref.WeakReference

/** Uses the existing chat binder; never changes native backgrounds or layout params. */
internal class DynamicRecallRuntime(private val context: Context) {
    private val prefs = HchatStorage.preferences(context, AntiRecallSettings.PREFS_NAME)
    private val handler = Handler(Looper.getMainLooper())
    private val rows = WeakHashMap<View, WeakReference<Binding>>()
    private val refreshRows = Runnable { rows.values.toList().forEach { it.get()?.refresh() } }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == AntiRecallSettings.KEY_ENABLE ||
            key == DynamicRecallStore.KEY_RECORDS || key.startsWith("dynamic_recall_")) {
            handler.removeCallbacks(refreshRows)
            handler.post(refreshRows)
        }
    }
    init { prefs.registerOnSharedPreferenceChangeListener(listener) }

    fun bind(root: View, talker: String, id: Long, serverId: Long, content: () -> View?) {
        clear(root)
        Binding(root, talker, id, serverId, content).also {
            rows[root] = WeakReference(it)
            root.addOnAttachStateChangeListener(it)
            if (root.isAttachedToWindow) it.attach()
        }
    }
    fun clear(root: View) { rows.remove(root)?.get()?.dispose() }
    fun destroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        rows.values.toList().forEach { it.get()?.dispose() }
        rows.clear()
        handler.removeCallbacksAndMessages(null)
    }
    private fun enabled() = prefs.getBoolean(AntiRecallSettings.KEY_ENABLE, false) &&
        prefs.getBoolean(AntiRecallSettings.KEY_DYNAMIC_ENABLE, false)

    private inner class Binding(
        val root: View, val talker: String, val id: Long, val serverId: Long,
        var content: () -> View?
    ) : View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener {
        private var observer: ViewTreeObserver? = null
        private var target: View? = null
        private var stroke: RecallStroke? = null
        fun attach() {
            detachObserver()
            refresh()
        }
        fun refresh() {
            if (rows[root]?.get() !== this || !root.isAttachedToWindow || !enabled() ||
                !DynamicRecallStore.contains(context, talker, id, serverId)) {
                detachObserver(); removeStroke(); return
            }
            if (observer !== root.viewTreeObserver) {
                detachObserver()
                root.viewTreeObserver.takeIf { it.isAlive }?.let {
                    observer = it; it.addOnGlobalLayoutListener(this)
                }
            }
            try {
                val next = content()?.takeIf { it !== root && it.width > 0 && it.height > 0 }
                if (next !== target) {
                    removeStroke()
                    if (next != null) {
                        target = next
                        stroke = RecallStroke(next).also { next.overlay.add(it) }
                    }
                }
                stroke?.update()
            } catch (error: Throwable) {
                removeStroke()
                HLog.e("[Hchat:动态撤回] 消息描边更新失败", error)
            }
        }
        override fun onGlobalLayout() = refresh()
        override fun onViewAttachedToWindow(view: View) = attach()
        override fun onViewDetachedFromWindow(view: View) { detachObserver(); removeStroke() }
        private fun detachObserver() {
            observer?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(this)
            observer = null
        }
        private fun removeStroke() {
            stroke?.stop()
            stroke?.let { target?.overlay?.remove(it) }
            stroke = null; target = null
        }
        fun dispose() {
            detachObserver(); removeStroke()
            root.removeOnAttachStateChangeListener(this)
            content = { null }
        }
    }

    private inner class RecallStroke(private val view: View) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val rect = RectF()
        private val visible = Rect()
        private var running = false
        private val outline = Path()
        private val segment = Path()
        private val measure = PathMeasure()
        private val gradientMatrix = Matrix()
        private var gradient: SweepGradient? = null
        private var perimeter = 0f
        private var dashCount = 1
        private var period = DynamicBadgeSettings.DEFAULT_NUMBER_SPEED
        private val frame = object : Runnable {
            override fun run() {
                if (!running) return
                if (!enabled() || !view.isAttachedToWindow) { stop(); return }
                if (view.isShown && view.getGlobalVisibleRect(visible)) {
                    invalidateSelf()
                    view.postOnAnimation(this)
                } else view.postDelayed(this, 250L)
            }
        }
        fun update() {
            setBounds(0, 0, view.width, view.height)
            val density = view.resources.displayMetrics.density
            paint.strokeWidth = prefs.getInt(AntiRecallSettings.KEY_DYNAMIC_STROKE,
                DynamicBadgeSettings.DEFAULT_NUMBER_STROKE_WIDTH)
                .coerceIn(DynamicBadgeSettings.MIN_STROKE_WIDTH, DynamicBadgeSettings.MAX_STROKE_WIDTH) * density
            val inset = paint.strokeWidth / 2f
            rect.set(inset, inset, view.width - inset, view.height - inset)
            val colors = DynamicRecallGradient.parts(prefs.getString(AntiRecallSettings.KEY_DYNAMIC_COLOR,
                DynamicRecallGradient.DEFAULT).orEmpty()).map { Color.parseColor(it) }
            gradient = SweepGradient(rect.centerX(), rect.centerY(),
                intArrayOf(colors[0], colors[1], colors[2], colors[0]), floatArrayOf(0f, 1f / 3f, 2f / 3f, 1f))
            outline.reset()
            val radius = minOf(12f * density, rect.height() / 2f)
            if (rect.width() > 0 && rect.height() > 0) outline.addRoundRect(rect, radius, radius, Path.Direction.CW)
            measure.setPath(outline, true)
            perimeter = measure.length
            dashCount = (perimeter / maxOf(24f * density, paint.strokeWidth * 5f)).toInt().coerceAtLeast(1)
            paint.strokeCap = Paint.Cap.ROUND
            period = prefs.getInt(AntiRecallSettings.KEY_DYNAMIC_SPEED, DynamicBadgeSettings.DEFAULT_NUMBER_SPEED)
                .coerceIn(DynamicBadgeSettings.MIN_SPEED, DynamicBadgeSettings.MAX_SPEED)
            if (!running) { running = true; view.postOnAnimation(frame) }
            invalidateSelf()
        }
        fun stop() { running = false; view.removeCallbacks(frame) }
        override fun draw(canvas: Canvas) {
            if (!running || !enabled() || !view.isShown || rect.width() <= 0 || rect.height() <= 0) return
            if (perimeter <= 0f) return
            val progress = (SystemClock.uptimeMillis() % period).toFloat() / period
            gradientMatrix.setRotate(progress * 360f, rect.centerX(), rect.centerY())
            gradient?.setLocalMatrix(gradientMatrix)
            paint.shader = gradient
            // Evenly spaced segments advance clockwise around the complete closed path.
            // Split seam-crossing segments so no dash disappears at the closing corner.
            val spacing = perimeter / dashCount
            val length = spacing * .58f
            val offset = progress * perimeter
            segment.reset()
            repeat(dashCount) { index ->
                val start = (index * spacing + offset) % perimeter
                val end = start + length
                measure.getSegment(start, minOf(end, perimeter), segment, true)
                if (end > perimeter) measure.getSegment(0f, end - perimeter, segment, true)
            }
            canvas.drawPath(segment, paint)
            paint.shader = null
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
