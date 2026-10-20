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
    private var shaderWidth = -1
    private var backgroundAlpha = -1
    private val panel = GradientDrawable().apply { cornerRadius = 18 * resources.displayMetrics.density }
    init { setTextColor(Color.WHITE); background = panel }
    override fun onDraw(canvas: Canvas) {
        val spec = settings.getString(HomeExtensionSettings.LYRIC_COLOR, "#FFFFFF,#FFFFFF,#FFFFFF").orEmpty()
        if (spec != colorSpec || width != shaderWidth) {
            val colors = DynamicRecallGradient.parts(spec).map { Color.parseColor(it) }.toIntArray()
            paint.shader = LinearGradient(paddingLeft.toFloat(), 0f, (width-paddingRight).coerceAtLeast(paddingLeft+1).toFloat(), 0f, colors, null, Shader.TileMode.CLAMP)
            colorSpec = spec; shaderWidth = width
        }
        val alpha = settings.getInt(HomeExtensionSettings.LYRIC_ALPHA, 40).coerceIn(0,100)
        if (alpha != backgroundAlpha) { panel.setColor(Color.argb(alpha*255/100,0,0,0)); backgroundAlpha=alpha }
        super.onDraw(canvas)
        postInvalidateDelayed(250)
    }
}
