package t.mma.hooks.items.profileid

object ProfileRealNameTailSettings {
    const val PREFS_NAME = "Hchat_profile_real_name_tail_config"
    const val KEY_ENABLE = "enabled"
    const val KEY_PREFIX = "prefix"
    const val KEY_COLOR = "color"
    const val DEFAULT_ENABLE = false
    const val DEFAULT_PREFIX = "＊＊"
    const val DEFAULT_COLOR = "#FF000000,#FF000000,#FF000000"

    fun prefix(value: String?): String = value.orEmpty().trim().ifEmpty { DEFAULT_PREFIX }

    fun cleanColorSpec(value: String?): String = ProfileGroupJoinTimeSettings.cleanColorSpec(value)

    /** The source is an existing validated masked real name, never a contact nickname. */
    fun headerText(maskedName: String?, rawPrefix: String?): String {
        val name = maskedName.orEmpty().trim()
        val last = name.codePoints().toArray().lastOrNull()
        val tail = last?.takeIf { Character.isLetter(it) }?.let { String(Character.toChars(it)) }
        return "实名尾字：" + if (tail == null) "未知" else prefix(rawPrefix) + tail
    }
}
