package h.Hchat.hooks.items.mentionhighlight

import android.content.Context
import h.Hchat.preferences.HchatStorage

object MentionHighlightSettings {
    const val PREFS_NAME = "Hchat_mention_highlight_config"
    const val KEY_ENABLE = "mention_highlight_enable"
    const val KEY_COLOR = "mention_highlight_color"
    const val DEFAULT_COLOR = "#FFFF0000"

    fun cleanColor(value: String?): String {
        val raw = value.orEmpty().trim()
        if (raw.isEmpty()) return ""
        val normalized = if (raw.startsWith("#")) raw else "#$raw"
        val hex = normalized.substring(1)
        if (hex.length != 6 && hex.length != 8) return ""
        if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return ""
        return "#${hex.uppercase()}"
    }

    fun preferences(context: Context) = HchatStorage.preferences(context, PREFS_NAME)
}
