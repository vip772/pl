package t.mma.hooks.items.automerge

import android.content.Context
import t.mma.preferences.HchatStorage

object AutoMergeSettings {
    const val PREFS_NAME = "Hchat_auto_merge_config"
    const val KEY_ENABLE = "auto_merge_enable"
    const val DEFAULT_ENABLE = false

    fun preferences(context: Context) = HchatStorage.preferences(context, PREFS_NAME)
}
