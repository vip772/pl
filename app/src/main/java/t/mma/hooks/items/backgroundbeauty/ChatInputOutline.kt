package t.mma.hooks.items.backgroundbeauty

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView

import java.lang.ref.WeakReference

/** Align the visible border with the smiley ink, without resizing the editor or its hit area. */
internal class ChatInputOutline(
    editor: View, icon: View?, private val inkTop: Int, private val inkBottom: Int,
    private val stroke: Float, color: Int, private val radius: Float
) : Drawable() {
    private val editorRef = WeakReference(editor)
    private val iconRef = WeakReference(icon)
    private val editorPosition = IntArray(2)
    private val iconPosition = IntArray(2)
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        this.color = color
    }
    override fun draw(canvas: Canvas) {
        val editor = editorRef.get() ?: return
        val icon = iconRef.get()
        val half = stroke / 2f
        var top = bounds.top.toFloat()
        var bottom = bounds.bottom.toFloat()
        // The smiley only defines the compact, single-line border. WeChat already
        // measures and scrolls the multiline editor; its full bounds must enclose
        // that text viewport, including after the editor reaches its height limit.
        val multiline = (editor as? TextView)?.let {
            maxOf(it.lineCount, it.layout?.lineCount ?: 0) > 1
        } ?: false
        if (!multiline && icon != null && icon.isShown && inkBottom > inkTop) {
            editor.getLocationInWindow(editorPosition)
            icon.getLocationInWindow(iconPosition)
            val offset = iconPosition[1] - editorPosition[1]
            top = (offset + inkTop).toFloat().coerceIn(bounds.top.toFloat(), bounds.bottom.toFloat())
            bottom = (offset + inkBottom).toFloat().coerceIn(top, bounds.bottom.toFloat())
        }
        if (bottom - top <= stroke) return
        rect.set(bounds.left + half, top + half, bounds.right - half, bottom - half)
        canvas.drawRoundRect(rect, radius, radius, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Drawable opacity is not used")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
