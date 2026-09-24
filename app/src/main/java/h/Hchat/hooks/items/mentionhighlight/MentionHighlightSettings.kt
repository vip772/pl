package h.Hchat.hooks.items.mentionhighlight

import android.content.Context
import h.Hchat.preferences.HchatStorage

object MentionHighlightSettings {
    const val PREFS_NAME = "Hchat_mention_highlight_config"
    const val KEY_ENABLE = "mention_highlight_enable"
    const val KEY_COLOR = "mention_highlight_color"
    const val DEFAULT_COLOR = "#FFFF0000,#FFFF0000"

    fun parseColors(value: String?): Pair<Int, Int> {
        val parts = value.orEmpty().split(',').map { cleanColor(it) }.filter { it.isNotEmpty() }
        val start = parts.firstOrNull() ?: "#FFFF0000"
        val end = parts.getOrNull(1) ?: start
        return runCatching { android.graphics.Color.parseColor(start) to android.graphics.Color.parseColor(end) }
            .getOrDefault(android.graphics.Color.RED to android.graphics.Color.RED)
    }

    fun cleanColorSpec(value: String?): String {
        val parts = value.orEmpty().split(',').map { cleanColor(it) }.filter { it.isNotEmpty() }.take(2)
        if (parts.isEmpty()) return ""
        return if (parts.size == 1) "${parts[0]},${parts[0]}" else "${parts[0]},${parts[1]}"
    }

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
