package t.mma.hooks.items.swipequote

import android.app.AlertDialog
import android.graphics.drawable.InsetDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewGroup

/** Styling for the swipe confirmation only; native measurement and click handlers stay intact. */
internal object SwipeRepeatDialogStyle {
    fun apply(dialog: AlertDialog, centerInTitleSpace: Boolean = true) {
        val window = dialog.window ?: return
        val decor = window.decorView
        // Keep the original title's measured space so the dialog does not change size.
        val titleId = decor.resources.getIdentifier("alertTitle", "id", "android")
        decor.findViewById<View>(titleId)?.visibility = View.INVISIBLE
        val panelId = decor.resources.getIdentifier("parentPanel", "id", "android")
        val panel = decor.findViewById<ViewGroup>(panelId) ?: return
        val density = decor.resources.displayMetrics.density
        fun dp(value: Float) = value * density
        val negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        fun capsule(button: android.widget.Button, color: Int) {
            val fill = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(100f)
            }
            val mask = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(100f)
            }
            button.backgroundTintList = null
            button.background = RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                InsetDrawable(fill, dp(5f).toInt(), 0, dp(5f).toInt(), 0),
                InsetDrawable(mask, dp(5f).toInt(), 0, dp(5f).toInt(), 0)
            )
            button.setTextColor(Color.WHITE)
            button.stateListAnimator = null
        }
        capsule(negative, Color.rgb(241, 147, 52))
        capsule(positive, Color.rgb(66, 170, 105))
        // Strip native rectangular surfaces, including theme-specific panel backgrounds.
        fun clearSurfaces(view: View) {
            if (view === negative || view === positive) return
            val padding = intArrayOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
            view.background = null
            view.setPadding(padding[0], padding[1], padding[2], padding[3])
            if (view is ViewGroup) for (index in 0 until view.childCount) {
                clearSurfaces(view.getChildAt(index))
            }
        }
        clearSurfaces(panel)
        val decorPadding = intArrayOf(decor.paddingLeft, decor.paddingTop, decor.paddingRight, decor.paddingBottom)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        decor.setPadding(decorPadding[0], decorPadding[1], decorPadding[2], decorPadding[3])
        panel.post {
            if (!dialog.isShowing) return@post
            val panelPosition = IntArray(2)
            val buttonPosition = IntArray(2)
            panel.getLocationInWindow(panelPosition)
            negative.getLocationInWindow(buttonPosition)
            val shoulder = (buttonPosition[1] - panelPosition[1] - dp(8f))
                .coerceIn(dp(24f), panel.height.toFloat())
            val inset = (buttonPosition[0] - panelPosition[0] - dp(12f))
                .coerceIn(0f, panel.width * 0.6f)
            panel.background = CardBackground(inset, shoulder, dp(20f))
            val message = panel.findViewById<TextView>(android.R.id.message)
            if (message != null) {
                if (!centerInTitleSpace) return@post
                // Preserve the original mode's outer size while changing its text metrics.
                panel.layoutParams = panel.layoutParams.apply { width = panel.width; height = panel.height }
                message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                message.gravity = Gravity.CENTER
                message.textAlignment = View.TEXT_ALIGNMENT_CENTER
                // Reuse the hidden title space without changing the card outline.
                var ancestor = message.parent
                while (ancestor is ViewGroup && ancestor !== panel) {
                    ancestor.clipChildren = false
                    ancestor.clipToPadding = false
                    ancestor = ancestor.parent
                }
                message.post positionMessage@{
                    if (!dialog.isShowing) return@positionMessage
                    val position = IntArray(2)
                    message.getLocationInWindow(position)
                    val targetCenter = panelPosition[1] + shoulder / 2f
                    val currentCenter = position[1] + message.height / 2f
                    message.translationY += targetCenter - currentCenter
                }
            }
        }
    }

    /** Full-width text area with a lower-right extension around the two actions. */
    private class CardBackground(
        private val inset: Float,
        private val shoulder: Float,
        radius: Float
    ) : Drawable() {
        private val path = Path()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 214, 226)
            pathEffect = CornerPathEffect(radius)
        }

        override fun onBoundsChange(bounds: android.graphics.Rect) {
            val left = bounds.left.toFloat()
            val top = bounds.top.toFloat()
            val right = bounds.right.toFloat()
            val bottom = bounds.bottom.toFloat()
            path.reset()
            path.moveTo(left, top)
            path.lineTo(right, top)
            path.lineTo(right, bottom)
            path.lineTo(left + inset, bottom)
            path.lineTo(left + inset, top + shoulder)
            path.lineTo(left, top + shoulder)
            path.close()
        }

        override fun draw(canvas: Canvas) { canvas.drawPath(path, paint) }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
