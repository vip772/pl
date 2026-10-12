package t.mma.hooks.items.antirecall

import android.graphics.Color

/** Solid color plus independent coverage; never converts animated content into a bitmap. */
internal object DynamicRecallTint {
    const val DEFAULT_COLOR = "#808080"
    const val DEFAULT_OPACITY = 35
    fun normalize(value: String?): String {
        val text = value?.trim().orEmpty()
        return if (text.matches(Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))) text.uppercase(java.util.Locale.ROOT)
            else DEFAULT_COLOR
    }
    fun opacity(value: Int): Int = value.coerceIn(0, 80)
    fun color(value: String?, coverage: Int): Int {
        val parsed = Color.parseColor(normalize(value))
        val alpha = (Color.alpha(parsed) * opacity(coverage) / 100f).toInt()
        return Color.argb(alpha, Color.red(parsed), Color.green(parsed), Color.blue(parsed))
    }
}
