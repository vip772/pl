package t.mma.hooks.items.mentionextension

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import java.lang.ref.WeakReference
import kotlin.math.ceil

/** Row overlay: outside MaskLayout, inside the message row's reserved padding. */
internal class AvatarMentionDecoration(
    row: ViewGroup,
    avatar: View
) : Drawable(), View.OnLayoutChangeListener, View.OnAttachStateChangeListener {
    private val rowRef = WeakReference(row)
    private val avatarRef = WeakReference(avatar)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * row.resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val avatarRect = Rect()
    private val textBounds = Rect()
    private val intermediateViews = ArrayList<WeakReference<View>>()
    private val originalPadding = intArrayOf(row.paddingLeft, row.paddingTop, row.paddingRight, row.paddingBottom)
    private val gap = 3f * row.resources.displayMetrics.density
    private val font = paint.fontMetrics
    private val textHeight = font.descent - font.ascent
    private val reserve = ceil(textHeight + gap).toInt()
    private var label = ""
    private var above = true
    private var colorSpec = ""
    private var colors = intArrayOf()
    private var centerX = 0f
    private var baseline = 0f
    private var drawableVisible = false
    private var gradientWidth = -1f
    private var gradientCenter = Float.NaN
    private var closed = false

    init {
        row.overlay.add(this)
        row.addOnLayoutChangeListener(this)
        avatar.addOnLayoutChangeListener(this)
        row.addOnAttachStateChangeListener(this)
        var parent = avatar.parent
        while (parent is View && parent !== row) {
            parent.addOnLayoutChangeListener(this)
            intermediateViews.add(WeakReference(parent))
            parent = parent.parent
        }
    }

    fun matches(avatar: View) = avatarRef.get() === avatar

    fun update(text: String, position: String, spec: String, stops: IntArray) {
        if (closed) return
        val row = rowRef.get() ?: return
        val nextAbove = position == AvatarMentionSettings.ABOVE
        val changed = label != text || above != nextAbove || colorSpec != spec
        label = text
        above = nextAbove
        if (colorSpec != spec || colors.isEmpty()) {
            colorSpec = spec
            colors = stops
            gradientWidth = -1f
        }
        val top = originalPadding[1] + if (above && text.isNotEmpty()) reserve else 0
        val bottom = originalPadding[3] + if (!above && text.isNotEmpty()) reserve else 0
        if (row.paddingTop != top || row.paddingBottom != bottom) {
            row.setPadding(originalPadding[0], top, originalPadding[2], bottom)
        }
        if (changed) {
            gradientWidth = -1f
            paint.getTextBounds(label, 0, label.length, textBounds)
        }
        place()
    }

    private fun place() {
        val row = rowRef.get() ?: return
        val avatar = avatarRef.get() ?: return
        drawableVisible = !closed && label.isNotEmpty() && avatar.isShown && avatar.width > 0 && avatar.height > 0
        if (!drawableVisible) {
            invalidateSelf()
            return
        }
        // Verify ancestry every bind/layout; recycled rows may replace their avatar.
        var parent = avatar.parent
        while (parent !== row && parent is View) parent = parent.parent
        if (parent !== row) {
            drawableVisible = false
            invalidateSelf()
            return
        }
        avatar.getDrawingRect(avatarRect)
        // Native avatar styles include 6dp padding. Anchor to the visible image,
        // rather than the larger touch target/MaskLayout bounds.
        avatarRect.left += avatar.paddingLeft
        avatarRect.top += avatar.paddingTop
        avatarRect.right -= avatar.paddingRight
        avatarRect.bottom -= avatar.paddingBottom
        row.offsetDescendantRectToMyCoords(avatar, avatarRect)
        centerX = avatarRect.exactCenterX()
        baseline = if (above) avatarRect.top - gap - textBounds.bottom else avatarRect.bottom + gap - textBounds.top
        val width = paint.measureText(label).coerceAtLeast(1f)
        if (gradientWidth != width || gradientCenter != centerX) {
            paint.shader = LinearGradient(centerX - width / 2, 0f, centerX + width / 2, 0f,
                colors, null, Shader.TileMode.CLAMP)
            gradientWidth = width
            gradientCenter = centerX
        }
        setBounds(0, 0, row.width, row.height)
        invalidateSelf()
    }

    override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int,
        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) = place()

    override fun onViewAttachedToWindow(v: View) = place()
    override fun onViewDetachedFromWindow(v: View) {
        drawableVisible = false
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (drawableVisible && avatarRef.get()?.isShown == true) canvas.drawText(label, centerX, baseline, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    fun close() {
        if (closed) return
        closed = true
        avatarRef.get()?.removeOnLayoutChangeListener(this)
        intermediateViews.forEach { it.get()?.removeOnLayoutChangeListener(this) }
        intermediateViews.clear()
        rowRef.get()?.let { row ->
            row.removeOnLayoutChangeListener(this)
            row.removeOnAttachStateChangeListener(this)
            row.overlay.remove(this)
            row.setPadding(originalPadding[0], originalPadding[1], originalPadding[2], originalPadding[3])
        }
        paint.shader = null
    }
}
