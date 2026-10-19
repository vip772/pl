package t.mma.hooks.items.homeextension

import android.content.Context
import android.graphics.Matrix
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import t.mma.preferences.HchatStorage

/** Static photos only. Transform preferences are isolated from header render preferences. */
internal class AdjustablePhotoView(context: Context, private val identity: String) : ImageView(context) {
    private val prefs = HchatStorage.preferences(context, "Hchat_home_photo_transform")
    private val matrixValue = Matrix()
    private var zoom = prefs.getFloat("${identity}_z", 1f).coerceIn(1f, 4f)
    private var offsetX = prefs.getFloat("${identity}_x", .5f).coerceIn(0f, 1f)
    private var offsetY = prefs.getFloat("${identity}_y", .5f).coerceIn(0f, 1f)
    private var editing = false
    private var lastX = 0f
    private var lastY = 0f
    private var rangeX = 0f
    private var rangeY = 0f
    private val longPress = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onLongPress(e: MotionEvent) {
            editing = true
            lastX = e.x; lastY = e.y
            parent?.requestDisallowInterceptTouchEvent(true)
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
    })
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector) = editing
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (!editing) return false
            val old = zoom
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, 4f)
            val factor = zoom / old
            val oldLeft = -rangeX * offsetX
            val oldTop = -rangeY * offsetY
            updateMatrix()
            offsetX = if (rangeX > 0) ((detector.focusX - (detector.focusX - oldLeft) * factor) / -rangeX).coerceIn(0f, 1f) else .5f
            offsetY = if (rangeY > 0) ((detector.focusY - (detector.focusY - oldTop) * factor) / -rangeY).coerceIn(0f, 1f) else .5f
            updateMatrix()
            return true
        }
    })
    init { scaleType = ScaleType.MATRIX }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh); updateMatrix()
    }
    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        // ImageView can call this override before subclass fields are initialised.
        if (width > 0 && height > 0) post { updateMatrix() }
    }
    private fun updateMatrix() {
        val image = drawable ?: return
        if (width <= 0 || height <= 0 || image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0) return
        val base = maxOf(width.toFloat() / image.intrinsicWidth, height.toFloat() / image.intrinsicHeight)
        val value = base * zoom
        rangeX = (image.intrinsicWidth * value - width).coerceAtLeast(0f)
        rangeY = (image.intrinsicHeight * value - height).coerceAtLeast(0f)
        matrixValue.setScale(value, value)
        matrixValue.postTranslate(-rangeX * offsetX, -rangeY * offsetY)
        imageMatrix = matrixValue
    }
    private fun save() { prefs.edit().putFloat("${identity}_z", zoom).putFloat("${identity}_x", offsetX).putFloat("${identity}_y", offsetY).apply() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        longPress.onTouchEvent(event)
        if (editing) scale.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = event.x; lastY = event.y }
            MotionEvent.ACTION_MOVE -> {
                if (editing && event.pointerCount == 1 && !scale.isInProgress) {
                    if (rangeX > 0) offsetX = (offsetX - (event.x - lastX) / rangeX).coerceIn(0f, 1f)
                    if (rangeY > 0) offsetY = (offsetY - (event.y - lastY) / rangeY).coerceIn(0f, 1f)
                    updateMatrix()
                }
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val remaining = if (event.actionIndex == 0) 1 else 0
                if (remaining < event.pointerCount) { lastX = event.getX(remaining); lastY = event.getY(remaining) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (editing) save()
                editing = false; parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
    override fun onDetachedFromWindow() {
        if (editing) save()
        editing = false; parent?.requestDisallowInterceptTouchEvent(false)
        super.onDetachedFromWindow()
    }
}
