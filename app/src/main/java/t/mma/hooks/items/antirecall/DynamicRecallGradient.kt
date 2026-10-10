package t.mma.hooks.items.antirecall

import android.graphics.Color

/** Old two-stop values keep their endpoints and gain an interpolated midpoint. */
internal object DynamicRecallGradient {
    const val DEFAULT = "#FF4D67,#7C6BFF,#FFC857"
    private val colorPattern = Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")
    fun parts(value: String): List<String> {
        if (value.isBlank()) return DEFAULT.split(",")
        val raw = value.split(',').take(3).map { part ->
            part.trim().takeIf { it.matches(colorPattern) }.orEmpty()
        }
        val defaults = DEFAULT.split(',')
        val start = raw.getOrNull(0).orEmpty().ifEmpty { defaults[0] }
        val end = (if (raw.size >= 3) raw[2] else raw.getOrNull(1)).orEmpty().ifEmpty { start }
        val middle = if (raw.size >= 3) raw[1].ifEmpty { midpoint(start, end) } else midpoint(start, end)
        return listOf(start, middle, end)
    }
    private fun midpoint(start: String, end: String): String {
        val a = Color.parseColor(start)
        val b = Color.parseColor(end)
        val result = Color.argb((Color.alpha(a) + Color.alpha(b)) / 2,
            (Color.red(a) + Color.red(b)) / 2, (Color.green(a) + Color.green(b)) / 2,
            (Color.blue(a) + Color.blue(b)) / 2)
        return "#%08X".format(result)
    }
}