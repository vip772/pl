package h.Hchat.hooks.items.profileid

object ProfileGroupJoinTimeSettings {
    const val PREFS_NAME = "Hchat_profile_group_join_time_config"
    const val KEY_ENABLE = "profile_group_join_time_enable"
    const val KEY_COLOR = "profile_group_join_time_color"
    const val DEFAULT_ENABLE = false
    const val DEFAULT_COLOR = "#FF000000,#FF000000"
    fun cleanColorSpec(value: String?): String = ProfileAddTimeSettings.cleanColorSpec(value)
}

object ProfileGroupInviterSettings {
    const val PREFS_NAME = "Hchat_profile_group_inviter_config"
    const val KEY_ENABLE = "profile_group_inviter_enable"
    const val KEY_COLOR = "profile_group_inviter_color"
    const val DEFAULT_ENABLE = false
    const val DEFAULT_COLOR = "#FF000000,#FF000000"
    fun cleanColorSpec(value: String?): String = ProfileAddTimeSettings.cleanColorSpec(value)
}
