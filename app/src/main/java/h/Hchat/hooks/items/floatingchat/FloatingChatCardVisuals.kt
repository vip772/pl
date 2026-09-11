package h.Hchat.hooks.items.floatingchat

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.View
import java.util.WeakHashMap
import kotlin.math.roundToInt

object FloatingChatCardVisuals {
    private const val LIGHT_SURFACE_COLOR = 0xFFF7F7F7.toInt()
    private const val DARK_SURFACE_COLOR = 0xFF242424.toInt()
    private const val DARK_STROKE_COLOR = 0x24FFFFFF

    private val originals = WeakHashMap<View, android.graphics.drawable.Drawable?>()
    private val applied = WeakHashMap<View, GradientDrawable>()

    fun apply(view: View, radiusDp: Int) {
        if (!originals.containsKey(view)) originals[view] = view.background
        val density = view.resources.displayMetrics.density
        val drawable = applied[view] ?: GradientDrawable().also { applied[view] = it }
        drawable.cornerRadius = radiusDp * density
        if (view.context.isDarkMode()) {
            drawable.setColor(DARK_SURFACE_COLOR)
            drawable.setStroke(density.roundToInt().coerceAtLeast(1), DARK_STROKE_COLOR)
        } else {
            drawable.setColor(LIGHT_SURFACE_COLOR)
            drawable.setStroke(0, 0)
        }
        view.background = drawable
    }

    private fun Context.isDarkMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun restore(view: View) {
        if (!originals.containsKey(view)) return
        view.background = originals.remove(view)
        applied.remove(view)
    }
}
