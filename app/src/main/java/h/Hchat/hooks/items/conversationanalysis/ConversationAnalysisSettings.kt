package h.Hchat.hooks.items.conversationanalysis

import android.content.Context
import h.Hchat.preferences.HchatStorage

object ConversationAnalysisSettings {
    const val PREFS_NAME = "Hchat_conversation_analysis_config"
    const val KEY_ENABLE = "conversation_analysis_enable"
    const val DEFAULT_ENABLE = false

    fun isEnabled(context: Context): Boolean =
        HchatStorage.preferences(context, PREFS_NAME)
            .getBoolean(KEY_ENABLE, DEFAULT_ENABLE)
}
