package android.content

interface SharedPreferences {
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun getString(key: String, defValue: String?): String?
    fun edit(): Editor
    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)
    fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)

    fun interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?)
    }

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply(): Editor
    }
}

class Context {
    val applicationContext: Context get() = this
}
