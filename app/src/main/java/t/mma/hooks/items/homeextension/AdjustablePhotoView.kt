package t.mma.hooks.items.homeextension
import android.content.Context
import android.view.MotionEvent
import android.widget.ImageView

/** Uses the same long-press/drag/pinch and persisted transform as calendar backgrounds. */
internal class AdjustablePhotoView(context: Context, identity: String) : ImageView(context) {
    private val transform = PhotoTransformController(this, identity,
        { drawable?.let { it.intrinsicWidth to it.intrinsicHeight } }, { imageMatrix = it })
    init { scaleType = ScaleType.MATRIX }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh); transform.updateMatrix()
    }
    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        // ImageView may invoke this before subclass initialization.
        if (width > 0 && height > 0) post { transform.updateMatrix() }
    }
    override fun onTouchEvent(event: MotionEvent) = transform.onTouchEvent(event)
    override fun onDetachedFromWindow() {
        transform.detach(); super.onDetachedFromWindow()
    }
}
