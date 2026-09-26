package h.Hchat.hooks.items.conversationanalysis

import android.content.Context

object ConversationAnalysisSettings {
    const val PREFS_NAME = "Hchat_conversation_analysis_config"
    const val KEY_ENABLE = "conversation_analysis_enable"
    const val DEFAULT_ENABLE = false

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLE, DEFAULT_ENABLE)
}
