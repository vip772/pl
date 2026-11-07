package t.mma.hooks.items.swipequote

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
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
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView

/** Owns measurement of the text body and the lower-right action extension. */
internal object SwipeRepeatDialogStyle {
    fun confirmation(context: Context, text: CharSequence, confirm: () -> Unit): AlertDialog {
        return object : AlertDialog(context) {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                val card = ConfirmationCard(context, text,
                    cancel = { cancel() },
                    confirm = {
                        if (isShowing) {
                            dismiss()
                            confirm()
                        }
                    })
                // Dialog.show() calls onCreate before attaching the decor to WindowManager.
                // Set final content and width now to center the first frame with its
                // measured height, without a second visible resize.
                setContentView(card)
                window?.apply {
                    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    decorView.setPadding(0, 0, 0, 0)
                    val available = context.resources.displayMetrics.widthPixels
                    setLayout(minOf(available - card.dp(48), card.dp(360)), ViewGroup.LayoutParams.WRAP_CONTENT)
                }
            }
        }.apply {
            setCancelable(true)
            setCanceledOnTouchOutside(true)
        }
    }

    private class ConfirmationCard(
        context: Context,
        text: CharSequence,
        cancel: () -> Unit,
        confirm: () -> Unit
    ) : ViewGroup(context) {
        private val message = TextView(context).apply {
            this.text = text
            textSize = 18f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        private val negative = action("假的", Color.rgb(241, 147, 52), cancel)
        private val positive = action("真的", Color.rgb(66, 170, 105), confirm)
        private var bodyHeight = 0
        fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

        init {
            addView(message)
            addView(negative)
            addView(positive)
        }

        private fun action(label: String, color: Int, click: () -> Unit) = Button(context).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 0)
            minimumWidth = 0
            minimumHeight = 0
            stateListAnimator = null
            val fill = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            val mask = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), fill, mask)
            setOnClickListener { click() }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val actionsHeight = dp(72)
            val availableBody = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED)
                MeasureSpec.getSize(0x3fffffff) else (MeasureSpec.getSize(heightMeasureSpec) - actionsHeight).coerceAtLeast(0)
            message.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(availableBody, MeasureSpec.AT_MOST))
            bodyHeight = maxOf(dp(104), message.measuredHeight).coerceAtMost(availableBody)
            message.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(bodyHeight, MeasureSpec.EXACTLY))
            val size = MeasureSpec.makeMeasureSpec(dp(56), MeasureSpec.EXACTLY)
            negative.measure(size, size)
            positive.measure(size, size)
            setMeasuredDimension(width, bodyHeight + actionsHeight)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            message.layout(0, 0, width, bodyHeight)
            val size = dp(56)
            val edge = dp(16)
            val gap = dp(10)
            val x = width - edge - size
            val y = bodyHeight + dp(8)
            positive.layout(x, y, x + size, y + size)
            negative.layout(x - gap - size, y, x - gap, y + size)
            if (changed || background == null) {
                background = CardBackground((negative.left - dp(16)).toFloat(), bodyHeight.toFloat(), dp(20).toFloat())
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
