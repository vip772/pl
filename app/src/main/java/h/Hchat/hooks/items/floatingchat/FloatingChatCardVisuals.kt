package h.Hchat.hooks.items.floatingchat

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.View
import java.util.WeakHashMap
import kotlin.math.roundToInt

object FloatingChatCardVisuals {
    private val originals = WeakHashMap<View, android.graphics.drawable.Drawable?>()
    private val applied = WeakHashMap<View, GradientDrawable>()

    fun apply(view: View, radiusDp: Int) {
        if (!view.context.isDarkMode()) {
            restore(view)
            return
        }
        if (!originals.containsKey(view)) originals[view] = view.background
        val density = view.resources.displayMetrics.density
        val drawable = applied[view] ?: GradientDrawable().also { applied[view] = it }
        drawable.cornerRadius = radiusDp * density
        drawable.setColor(0xFF242424.toInt())
        drawable.setStroke((density).roundToInt().coerceAtLeast(1), 0x24FFFFFF)
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
