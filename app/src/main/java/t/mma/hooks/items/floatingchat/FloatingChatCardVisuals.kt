package t.mma.hooks.items.floatingchat

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

    fun apply(view: View, radiusDp: Int, transparency: Int = -1) {
        if (!originals.containsKey(view)) originals[view] = view.background
        val density = view.resources.displayMetrics.density
        val drawable = applied[view] ?: GradientDrawable().also { applied[view] = it }
        val radius = radiusDp * density
        if (drawable.cornerRadius != radius) drawable.cornerRadius = radius
        val darkColor = FloatingChatOpacity.color(DARK_SURFACE_COLOR, transparency)
        val lightColor = FloatingChatOpacity.color(LIGHT_SURFACE_COLOR, transparency)
        if (view.context.isDarkMode()) {
            if (drawable.color?.defaultColor != darkColor) {
                drawable.setColor(darkColor)
                drawable.setStroke(density.roundToInt().coerceAtLeast(1), FloatingChatOpacity.color(DARK_STROKE_COLOR, transparency))
            }
        } else {
            if (drawable.color?.defaultColor != lightColor) {
                drawable.setColor(lightColor)
                drawable.setStroke(0, 0)
            }
        }

        // Native tint/Drawable alpha can survive a background identity change.
        if (drawable.alpha != 255) drawable.alpha = 255
        if (view.backgroundTintList != null) view.backgroundTintList = null
        if (view.backgroundTintMode != null) view.backgroundTintMode = null
        if (view.background !== drawable) view.background = drawable
    }

    private fun Context.isDarkMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val presentationOwners = WeakHashMap<View, Boolean>()
    internal fun ownPresentation(view: View) { presentationOwners[view] = true }
    internal fun releasePresentation(view: View) { presentationOwners.remove(view) }
    fun contains(view: View): Boolean = applied.containsKey(view) || presentationOwners.containsKey(view)
    fun restore(view: View) {
        if (!originals.containsKey(view)) return
        view.background = originals.remove(view)
        applied.remove(view)
    }
}
