package t.mma.hooks.items.conversationanalysis

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.AbsoluteSizeSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout

/** Native hint visibility with a transparent, content-sized editor and themed border. */
internal object ConversationAnalysisFocusInput {
    fun create(context: Context): LinearLayout {
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        fun dp(value: Float): Int = (value * context.resources.displayMetrics.density + .5f).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val editor = FocusEditor(context).apply {
                contentDescription = "AI总结关注内容（可选）"
                textSize = 15f
                setHintTextColor(if (dark) 0xffbdb4c5.toInt() else 0xff746a79.toInt())
                setTextColor(if (dark) 0xfff4edf7.toInt() else 0xff302737.toInt())
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                gravity = Gravity.TOP or Gravity.START
                minLines = 1
                minimumHeight = dp(48f)
                setHorizontallyScrolling(false)
                setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
                background = FocusBorder(context.resources.displayMetrics.density, dark)
            }
            addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** Android hides the hint on input; size it separately without shrinking entered text. */
    private class FocusEditor(context: Context) : EditText(context) {
        private val prompt = "（可选）让 AI 重点关注什么？例如：大家聊了吃的吗"
        private val hintPaint = TextPaint(paint).apply {
            textSize = 12f * resources.displayMetrics.scaledDensity
        }

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            super.onSizeChanged(width, height, oldWidth, oldHeight)
            if (width == oldWidth && hint != null) return
            val available = (width - compoundPaddingLeft - compoundPaddingRight).coerceAtLeast(0)
            val display = TextUtils.ellipsize(prompt, hintPaint, available.toFloat(), TextUtils.TruncateAt.END)
            hint = SpannableString(display).apply {
                setSpan(AbsoluteSizeSpan(hintPaint.textSize.toInt()), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    /** Rebuild the shader only when bounds change; no bitmaps, layers, or draw-time allocation. */
    private class FocusBorder(density: Float, dark: Boolean) : Drawable() {
        private val radius = 14f * density
        private val strokeWidth = 1.5f * density
        private val shape = RectF()
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.strokeWidth = this@FocusBorder.strokeWidth
        }
        private val colors = if (dark) intArrayOf(0xffff829b.toInt(), 0xffef9fd8.toInt(), 0xffc3a0ff.toInt())
            else intArrayOf(0xffec4569.toInt(), 0xffdf61b3.toInt(), 0xff9760df.toInt())

        override fun onBoundsChange(bounds: Rect) {
            val inset = strokeWidth / 2f
            shape.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
            stroke.shader = if (bounds.width() > 0) LinearGradient(
                bounds.left.toFloat(), 0f, bounds.right.toFloat(), 0f,
                colors, null, Shader.TileMode.CLAMP
            ) else null
        }

        override fun draw(canvas: Canvas) {
            canvas.drawRoundRect(shape, radius, radius, stroke)
        }

        override fun setAlpha(alpha: Int) {
            stroke.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            stroke.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Drawable opacity is no longer used by Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
