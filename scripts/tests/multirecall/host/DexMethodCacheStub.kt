package t.mma.dexkit

import android.content.Context
import android.content.SharedPreferences
import java.lang.reflect.Method

/** Synthetic descriptor cache: in-memory stores keyed by cache name, reset per scenario. */
object DexMethodCache {
    val store = LinkedHashMap<String, Method>()
    val listStore = LinkedHashMap<String, List<Method>>()
    val cleared = mutableListOf<String>()
    var runtimeKeyOverride: String = "runtime-key"

    fun prefs(context: Context, name: String): SharedPreferences = t.mma.preferences.HchatStorage.store(name)

    fun runtimeKey(context: Context, classLoader: ClassLoader): String = runtimeKeyOverride

    fun load(prefs: SharedPreferences, runtimeKey: String, classLoader: ClassLoader, name: String): Method? =
        if (runtimeKey.isBlank()) null else store[name]

    fun save(prefs: SharedPreferences, runtimeKey: String, name: String, method: Method?) {
        if (runtimeKey.isBlank() || method == null) return
        store[name] = method
    }

    fun loadList(prefs: SharedPreferences, runtimeKey: String, classLoader: ClassLoader, name: String): List<Method> =
        if (runtimeKey.isBlank()) emptyList() else listStore[name].orEmpty()

    fun saveList(prefs: SharedPreferences, runtimeKey: String, name: String, methods: List<Method>) {
        if (runtimeKey.isBlank() || methods.isEmpty()) return
        listStore[name] = methods.toList()
    }

    fun clear(prefs: SharedPreferences, runtimeKey: String, name: String) {
        cleared.add(name)
        store.remove(name)
        listStore.remove(name)
    }

    fun reset() {
        store.clear()
        listStore.clear()
        cleared.clear()
        runtimeKeyOverride = "runtime-key"
    }
}
