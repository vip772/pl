package t.mma.hooks.items.homeextension

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import t.mma.hooks.items.antirecall.DynamicRecallGradient

/** Apply settings on draw so an already-open window follows the settings editor. */
internal class FloatingMusicLyricView(c: Context) : TextView(c) {
    private val settings = HomeExtensionSettings.preferences(c)
    private var colorSpec: String? = null
    private var shaderLeft = Float.NaN
    private var shaderRight = Float.NaN
    private var lyricShader: Shader? = null
    private var backgroundAlpha = -1
    private val panel = GradientDrawable().apply { cornerRadius = 18 * resources.displayMetrics.density }
    init { setTextColor(Color.WHITE); background = panel }
    override fun onDraw(canvas: Canvas) {
        val spec = settings.getString(HomeExtensionSettings.LYRIC_COLOR, "#FFFFFF,#FFFFFF,#FFFFFF").orEmpty()
        val textLayout = layout
        if (textLayout != null && textLayout.lineCount > 0) {
            // Use the same Layout coordinates as TextView, including wide single-line
            // layouts, centered text and marquee. Padding belongs to the canvas transform.
            val left = minOf(textLayout.getLineLeft(0), textLayout.getLineRight(0))
            val right = maxOf(textLayout.getLineLeft(0), textLayout.getLineRight(0)).coerceAtLeast(left + 1f)
            if (spec != colorSpec || left != shaderLeft || right != shaderRight) {
                val colors = DynamicRecallGradient.parts(spec).map { Color.parseColor(it) }.toIntArray()
                lyricShader = LinearGradient(left, 0f, right, 0f, colors, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                colorSpec = spec; shaderLeft = left; shaderRight = right
            }
        } else {
            lyricShader = null
            shaderLeft = Float.NaN; shaderRight = Float.NaN
        }
        // Text/layout updates share this TextPaint; restore the shader on every draw.
        paint.shader = lyricShader
        val alpha = settings.getInt(HomeExtensionSettings.LYRIC_ALPHA, 40).coerceIn(0,100)
        if (alpha != backgroundAlpha) { panel.setColor(Color.argb(alpha*255/100,0,0,0)); backgroundAlpha=alpha }
        super.onDraw(canvas)
        postInvalidateDelayed(250)
    }
}
