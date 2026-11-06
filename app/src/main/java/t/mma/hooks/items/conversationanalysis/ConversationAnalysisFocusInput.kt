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
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** A compact single-line prompt above a content-sized editor. Uses the host night theme. */
internal object ConversationAnalysisFocusInput {
    fun create(context: Context): LinearLayout {
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        fun dp(value: Float): Int = (value * context.resources.displayMetrics.density + .5f).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val prompt = TextView(context).apply {
                text = "（可选）让 AI 重点关注什么？例如：大家聊了吃的吗"
                textSize = 12f
                setTextColor(if (dark) 0xffbdb4c5.toInt() else 0xff746a79.toInt())
                setSingleLine(true)
                // Keep the complete prompt on narrow screens and with larger system fonts.
                setAutoSizeTextTypeUniformWithConfiguration(8, 12, 1, TypedValue.COMPLEX_UNIT_SP)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
            addView(prompt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30f)))
            val editor = EditText(context).apply {
                contentDescription = "AI总结关注内容（可选）"
                textSize = 15f
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

    /** Rebuild the shader only when bounds change; no bitmaps, layers, or draw-time allocation. */
    private class FocusBorder(density: Float, dark: Boolean) : Drawable() {
        private val radius = 14f * density
        private val strokeWidth = 1.5f * density
        private val shape = RectF()
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0xff29232e.toInt() else 0xfffffbff.toInt()
        }
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
            canvas.drawRoundRect(shape, radius, radius, fill)
            canvas.drawRoundRect(shape, radius, radius, stroke)
        }

        override fun setAlpha(alpha: Int) {
            fill.alpha = alpha
            stroke.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            fill.colorFilter = colorFilter
            stroke.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Drawable opacity is no longer used by Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
