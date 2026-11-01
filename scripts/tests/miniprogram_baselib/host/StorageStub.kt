package t.mma.preferences

import android.content.Context
import android.content.SharedPreferences

/** Synthetic preference store: one instance per prefs name, listener list is observable. */
object HchatStorage {
    val prefs = FakePrefs()

    private val named = LinkedHashMap<String, FakePrefs>()

    @JvmStatic
    fun preferences(context: Context, name: String): SharedPreferences =
        named.getOrPut(name) { if (name == RealFeaturePrefsName.NAME) prefs else FakePrefs() }

    fun reset() {
        named.clear()
        prefs.reset()
    }

    private object RealFeaturePrefsName {
        const val NAME = "Hchat_fake_mini_program_base_lib_config"
    }
}

class FakePrefs : SharedPreferences {
    val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    private val values = LinkedHashMap<String, Any?>()
    var enabled: Boolean = false

    override fun getBoolean(key: String, defValue: Boolean): Boolean = enabled

    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            values[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            values.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            values.clear()
            return this
        }

        override fun apply() {}
    }

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        listeners.remove(listener)
    }

    /** Toggle the feature switch and replay the production change callback path. */
    fun toggle(value: Boolean) {
        enabled = value
        change("fake_mini_program_base_lib_enable")
    }

    fun change(key: String) {
        listeners.toList().forEach { it.onSharedPreferenceChanged(this, key) }
    }

    fun reset() {
        listeners.clear()
        values.clear()
        enabled = false
    }
}