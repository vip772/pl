package h.Hchat.hooks.items.floatingheader

import android.content.Context
import h.Hchat.preferences.HchatStorage
import kotlin.math.roundToInt

object FloatingChatHeaderSettings {
    const val PREFS_NAME = "Hchat_floating_chat_header_config"
    const val KEY_ENABLED = "enabled"
    const val KEY_RADIUS_DP = "radius_dp"
    const val KEY_HORIZONTAL_MARGIN_DP = "horizontal_margin_dp"
    const val KEY_VERTICAL_MARGIN_DP = "vertical_margin_dp"
    const val KEY_ELEVATION_DP = "elevation_dp"
    const val KEY_OPACITY = "opacity"

    const val DEFAULT_ENABLED = false
    const val DEFAULT_RADIUS_DP = 18f
    const val DEFAULT_HORIZONTAL_MARGIN_DP = 12f
    const val DEFAULT_VERTICAL_MARGIN_DP = 6f
    const val DEFAULT_ELEVATION_DP = 8f
    const val DEFAULT_OPACITY = 0.95f

    fun enabled(context: Context) = HchatStorage.preferences(context, PREFS_NAME)
        .getBoolean(KEY_ENABLED, DEFAULT_ENABLED)
    fun radiusDp(context: Context) = float(context, KEY_RADIUS_DP, DEFAULT_RADIUS_DP, 0f, 40f)
    fun horizontalMarginDp(context: Context) = float(context, KEY_HORIZONTAL_MARGIN_DP, DEFAULT_HORIZONTAL_MARGIN_DP, 0f, 32f)
    fun verticalMarginDp(context: Context) = float(context, KEY_VERTICAL_MARGIN_DP, DEFAULT_VERTICAL_MARGIN_DP, 0f, 24f)
    fun elevationDp(context: Context) = float(context, KEY_ELEVATION_DP, DEFAULT_ELEVATION_DP, 0f, 24f)
    fun opacity(context: Context) = float(context, KEY_OPACITY, DEFAULT_OPACITY, 0.5f, 1f)

    fun normalize(value: Float, min: Float, max: Float): Float =
        (value.coerceIn(min, max) * 100f).roundToInt() / 100f

    private fun float(context: Context, key: String, default: Float, min: Float, max: Float): Float =
        normalize(HchatStorage.preferences(context, PREFS_NAME).getFloat(key, default), min, max)
}