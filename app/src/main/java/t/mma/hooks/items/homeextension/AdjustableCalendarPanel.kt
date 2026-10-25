package t.mma.hooks.items.homeextension

import android.content.Context
import android.graphics.Bitmap
import android.view.MotionEvent
import android.widget.LinearLayout

/** Foreground children keep their own layout/clicks; transforms only update the shader. */
internal class AdjustableCalendarPanel(context: Context) : LinearLayout(context) {
    private var transform: PhotoTransformController? = null
    fun setBackgroundImage(bitmap: Bitmap, radius: Float, opacity: Int, revision: Long) {
        transform?.detach()
        val image = CalendarBackgroundDrawable(bitmap, radius, opacity)
        background = image
        // A replacement gets a fresh transform namespace; stale views cannot overwrite it.
        transform = PhotoTransformController(this, "${CalendarBackground.SLOT}_$revision",
            { bitmap.width to bitmap.height }, { image.setTransform(it) })
        transform?.updateMatrix()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        transform?.updateMatrix()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean =
        transform?.onTouchEvent(event) ?: super.onTouchEvent(event)
    override fun onDetachedFromWindow() {
        transform?.detach()
        super.onDetachedFromWindow()
    }
}
