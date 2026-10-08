package t.mma.hooks.items.dynamicbadge

object DynamicBadgeSettings {
    const val ID = "dynamic_badge"
    const val PREFS_NAME = "Hchat_dynamic_badge_config"
    const val KEY_ENABLE = "dynamic_badge_enable"
    const val KEY_NUMBER_ENABLE = "dynamic_badge_number_enable"
    const val KEY_DOT_ENABLE = "dynamic_badge_dot_enable"
    const val KEY_NUMBER_START = "dynamic_badge_number_start"
    const val KEY_NUMBER_END = "dynamic_badge_number_end"
    const val KEY_NUMBER_SPEED = "dynamic_badge_number_speed"
    const val KEY_DOT_START = "dynamic_badge_dot_start"
    const val KEY_DOT_END = "dynamic_badge_dot_end"
    const val KEY_DOT_SPEED = "dynamic_badge_dot_speed"
    const val DEFAULT_ENABLE = false
    const val DEFAULT_NUMBER_ENABLE = true
    const val DEFAULT_DOT_ENABLE = true
    const val DEFAULT_NUMBER_START = 0xFFFF4D67.toInt()
    const val DEFAULT_NUMBER_END = 0xFFFFC857.toInt()
    const val DEFAULT_DOT_START = 0xFFFF3B30.toInt()
    const val DEFAULT_DOT_END = 0xFFFF8A00.toInt()
    const val DEFAULT_NUMBER_SPEED = 2400
    const val DEFAULT_DOT_SPEED = 900
    const val MIN_SPEED = 300
    const val MAX_SPEED = 5000
}
