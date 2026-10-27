package t.mma.preferences

import android.content.Context
import android.content.SharedPreferences

/** All writes, including quick toggles/import, use the same preference objects. Keep settings intact. */
object ConversationGroupingExclusion {
    const val GROUP_PREFS = "Hchat_conversation_groups"
    const val GROUP_KEY = "enabled"
    const val BAR_PREFS = "Hchat_conversation_group_bar_config"
    const val BAR_KEY = "enable"
    private val lock = Any()
    private val bindings = java.util.IdentityHashMap<SharedPreferences, SharedPreferences.OnSharedPreferenceChangeListener>()
    fun bind(context: Context, name: String, prefs: SharedPreferences) {
        if (name != GROUP_PREFS && name != BAR_PREFS) return
        synchronized(lock) {
            if (bindings.containsKey(prefs)) return
            val ownKey = if (name == GROUP_PREFS) GROUP_KEY else BAR_KEY
            val otherName = if (name == GROUP_PREFS) BAR_PREFS else GROUP_PREFS
            val otherKey = if (name == GROUP_PREFS) BAR_KEY else GROUP_KEY
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if ((key == null || key == ownKey) && prefs.getBoolean(ownKey, false)) synchronized(lock) {
                    val other = HchatStorage.preferences(context, otherName)
                    if (other.getBoolean(otherKey, false)) other.edit().putBoolean(otherKey, false).apply()
                }
            }
            bindings[prefs] = listener
            prefs.registerOnSharedPreferenceChangeListener(listener)
            // A restored legacy pair may start with both true. Deterministically prefer the bar.
            if (name == BAR_PREFS && prefs.getBoolean(BAR_KEY, false)) {
                val other = HchatStorage.preferences(context, GROUP_PREFS)
                if (other.getBoolean(GROUP_KEY, false)) other.edit().putBoolean(GROUP_KEY, false).apply()
            }
        }
    }
}