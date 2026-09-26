package h.Hchat.hooks.items.conversationanalysis

import android.content.Context

internal data class ConversationAiProfile(
    val id: Int,
    val name: String,
    val address: String,
    val path: String,
    val key: String,
    val model: String
)

internal class ConversationAnalysisAiConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(ConversationAnalysisSettings.PREFS_NAME, Context.MODE_PRIVATE)

    fun activeProfileId(): Int = prefs.getInt(KEY_ACTIVE_PROFILE, 1).coerceIn(1, PROFILE_COUNT)

    fun setActiveProfile(id: Int) {
        prefs.edit().putInt(KEY_ACTIVE_PROFILE, id.coerceIn(1, PROFILE_COUNT)).apply()
    }

    fun profile(id: Int): ConversationAiProfile {
        val safeId = id.coerceIn(1, PROFILE_COUNT)
        migrateLegacyIfNeeded(safeId)
        val suffix = "_$safeId"
        return ConversationAiProfile(
            id = safeId,
            name = prefs.getString(KEY_NAME + suffix, "方案$safeId").orEmpty().ifBlank { "方案$safeId" },
            address = prefs.getString(KEY_ADDRESS + suffix, "").orEmpty(),
            path = prefs.getString(KEY_PATH + suffix, "/chat/completions").orEmpty().ifBlank { "/chat/completions" },
            key = prefs.getString(KEY_KEY + suffix, "").orEmpty(),
            model = prefs.getString(KEY_MODEL + suffix, "").orEmpty()
        )
    }

    fun activeProfile(): ConversationAiProfile = profile(activeProfileId())

    fun sampleLimit(): Int = prefs.getInt(KEY_SAMPLE_LIMIT, 0).coerceIn(0, 50_000)
    fun contextWindow(): String = prefs.getString(KEY_CONTEXT_WINDOW, "128k").orEmpty().let { value ->
        if (value in setOf("128k", "256k", "512k", "1m", "2m")) value else "128k"
    }
    fun setSampling(contextWindow: String, sampleLimit: Int) {
        prefs.edit().putString(KEY_CONTEXT_WINDOW, contextWindow).putInt(KEY_SAMPLE_LIMIT, sampleLimit.coerceIn(0, 50_000)).apply()
    }

    fun save(profile: ConversationAiProfile) {
        val id = profile.id.coerceIn(1, PROFILE_COUNT)
        val suffix = "_$id"
        prefs.edit()
            .putString(KEY_NAME + suffix, profile.name.trim().ifBlank { "方案$id" })
            .putString(KEY_ADDRESS + suffix, profile.address.trim())
            .putString(KEY_PATH + suffix, profile.path.trim().ifBlank { "/chat/completions" })
            .putString(KEY_KEY + suffix, profile.key.trim())
            .putString(KEY_MODEL + suffix, profile.model.trim())
            .putInt(KEY_ACTIVE_PROFILE, activeProfileId())
            .apply()
    }

    private fun migrateLegacyIfNeeded(id: Int) {
        if (id != 1 || prefs.contains(KEY_ADDRESS + "_1")) return
        if (!prefs.contains(LEGACY_ADDRESS) && !prefs.contains(LEGACY_KEY) && !prefs.contains(LEGACY_MODEL)) return
        prefs.edit()
            .putString(KEY_NAME + "_1", "方案1")
            .putString(KEY_ADDRESS + "_1", prefs.getString(LEGACY_ADDRESS, "").orEmpty())
            .putString(KEY_PATH + "_1", prefs.getString(LEGACY_PATH, "/chat/completions").orEmpty())
            .putString(KEY_KEY + "_1", prefs.getString(LEGACY_KEY, "").orEmpty())
            .putString(KEY_MODEL + "_1", prefs.getString(LEGACY_MODEL, "").orEmpty())
            .apply()
    }

    companion object {
        const val PROFILE_COUNT = 2
        const val KEY_ACTIVE_PROFILE = "analysis_ai_active_profile"
        const val KEY_NAME = "analysis_ai_profile_name"
        const val KEY_ADDRESS = "analysis_ai_address"
        const val KEY_PATH = "analysis_ai_path"
        const val KEY_KEY = "analysis_ai_key"
        const val KEY_MODEL = "analysis_ai_model"
        const val LEGACY_ADDRESS = "analysis_ai_address"
        const val LEGACY_PATH = "analysis_ai_path"
        const val LEGACY_KEY = "analysis_ai_key"
        const val LEGACY_MODEL = "analysis_ai_model"
        const val KEY_CONTEXT_WINDOW = "analysis_ai_context_window"
        const val KEY_SAMPLE_LIMIT = "analysis_ai_message_limit"
    }
}