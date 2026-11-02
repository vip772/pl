package t.mma.preferences

import android.content.Context
import android.content.SharedPreferences

object HchatStorage {
    private val named = LinkedHashMap<String, FakePrefs>()

    @JvmStatic
    fun preferences(context: Context, name: String): SharedPreferences = store(name)

    fun store(name: String): FakePrefs = named.getOrPut(name) { FakePrefs() }

    fun reset() { named.clear() }
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
        override fun apply(): SharedPreferences.Editor = this
    }

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        listeners.remove(listener)
    }

    fun toggle(value: Boolean, key: String) {
        enabled = value
        change(key)
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
