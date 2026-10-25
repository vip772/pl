package t.mma.hooks.items.homeextension
import android.graphics.Matrix
import android.view.View
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import t.mma.preferences.HchatStorage

/** Shared by the homepage photo and calendar BACKGROUND. No per-frame tasks. */
internal class PhotoTransformController(
    private val view: View,
    private val identity: String,
    private val imageSize: () -> Pair<Int, Int>?,
    private val applyMatrix: (Matrix) -> Unit
) {
    private val prefs = HchatStorage.preferences(view.context, "Hchat_home_photo_transform")
    private val matrixValue = Matrix()
    private var zoom = prefs.getFloat("${identity}_z", 1f).coerceIn(1f, 4f)
    private var offsetX = prefs.getFloat("${identity}_x", .5f).coerceIn(0f, 1f)
    private var offsetY = prefs.getFloat("${identity}_y", .5f).coerceIn(0f, 1f)
    private var editing = false
    private var lastX = 0f
    private var lastY = 0f
    private var rangeX = 0f
    private var rangeY = 0f
    private val longPress = GestureDetector(view.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onLongPress(e: MotionEvent) {
            editing = true
            lastX = e.x; lastY = e.y
            view.parent?.requestDisallowInterceptTouchEvent(true)
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
    })
    private val scale = ScaleGestureDetector(view.context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
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
    fun updateMatrix() {
        val image = imageSize() ?: return
        val width = view.width; val height = view.height
        if (width <= 0 || height <= 0 || image.first <= 0 || image.second <= 0) return
        val base = maxOf(width.toFloat() / image.first, height.toFloat() / image.second)
        val value = base * zoom
        rangeX = (image.first * value - width).coerceAtLeast(0f)
        rangeY = (image.second * value - height).coerceAtLeast(0f)
        matrixValue.setScale(value, value)
        matrixValue.postTranslate(-rangeX * offsetX, -rangeY * offsetY)
        applyMatrix(matrixValue)
    }
    private fun save() { prefs.edit().putFloat("${identity}_z", zoom).putFloat("${identity}_x", offsetX).putFloat("${identity}_y", offsetY).apply() }
    fun onTouchEvent(event: MotionEvent): Boolean {
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
                editing = false; view.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
    fun detach() {
        if (editing) save()
        editing = false; view.parent?.requestDisallowInterceptTouchEvent(false)

    }
}
