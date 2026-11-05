package t.mma.hooks.items.mentionall

import android.content.Context
import t.mma.preferences.HchatStorage

/** Automatically backed up user settings. Member lists are never persisted. */
object MentionAllSettings {
    const val PREFS = "Hchat_mention_all_config"
    const val ENABLE = "enable"
    const val COMMAND = "command"
    const val DEFAULT_COMMAND = "#aa"

    fun preferences(context: Context) = HchatStorage.preferences(context, PREFS)
}
