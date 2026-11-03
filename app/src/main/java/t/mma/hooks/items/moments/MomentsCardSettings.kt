package t.mma.hooks.items.moments

/** User settings only; no view, resource ID or runtime cache is persisted. */
object MomentsCardSettings {
    const val PREFS_NAME = "Hchat_moments_card_config"
    const val ENABLE = "enable"
    const val BORDER = "border_enable"
    const val COLORS = "border_colors"
    const val DEFAULT_COLORS = "#FF4D67,#7C6BFF,#FFC857"
    const val GAP_DP = 6
    const val STROKE_DP = 2
    const val CORNER_RADIUS_DP = 32
    const val CONTENT_INSET_DP = 12
    const val CARD_BACKGROUND = 0xFFF3F3F3.toInt()
}
