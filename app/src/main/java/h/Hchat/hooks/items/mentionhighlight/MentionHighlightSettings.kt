package h.Hchat.hooks.items.mentionhighlight

import android.content.Context
import h.Hchat.preferences.HchatStorage

object MentionHighlightSettings {
    const val PREFS_NAME = "Hchat_mention_highlight_config"
    const val KEY_ENABLE = "mention_highlight_enable"
    const val KEY_COLOR = "mention_highlight_color"
    const val DEFAULT_COLOR = "#FFFF0000"

    fun preferences(context: Context) = HchatStorage.preferences(context, PREFS_NAME)
}
