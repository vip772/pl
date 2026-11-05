package t.mma.hooks.items.mentionextension

import android.content.Context
import t.mma.preferences.HchatStorage

/** User settings only. No message data or runtime cache belongs in this store. */
internal object AvatarMentionSettings {
    const val PREFS = "Hchat_avatar_mention_config"
    const val ENABLE = "enabled"
    const val POSITION = "position"
    const val COLOR = "color"
    const val ABOVE = "above"
    const val BELOW = "below"
    const val DEFAULT_COLOR = "#FF4D67,#7C6BFF,#FFC857"

    fun preferences(context: Context) = HchatStorage.preferences(context, PREFS)
    fun enabled(context: Context) = preferences(context).getBoolean(ENABLE, false)
    fun position(value: String?) = if (value == BELOW) BELOW else ABOVE
}
