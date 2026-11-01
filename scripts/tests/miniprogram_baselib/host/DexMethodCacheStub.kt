package t.mma.dexkit

import android.content.Context
import android.content.SharedPreferences
import t.mma.preferences.HchatStorage
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/** Synthetic descriptor cache: a single store shared by every synthetic process. */
object DexMethodCache {
    val store = LinkedHashMap<String, Method>()
    val constructorStore = LinkedHashMap<String, Constructor<*>>()
    val cleared = mutableListOf<String>()
    var crossProcessReady = true

    fun prefs(context: Context, name: String): SharedPreferences = HchatStorage.prefs

    fun runtimeKey(context: Context, classLoader: ClassLoader): String = "runtime"

    fun load(
        prefs: SharedPreferences,
        runtimeKey: String,
        classLoader: ClassLoader,
        name: String
    ): Method? = store[name]

    fun loadCrossProcess(
        prefs: SharedPreferences,
        runtimeKey: String,
        classLoader: ClassLoader,
        name: String
    ): Method? = if (crossProcessReady) store[name] else null

    fun save(prefs: SharedPreferences, runtimeKey: String, name: String, method: Method?) {
        if (method != null) store[name] = method
    }

    fun loadConstructor(
        prefs: SharedPreferences,
        runtimeKey: String,
        classLoader: ClassLoader,
        name: String
    ): Constructor<*>? = constructorStore[name]

    fun saveConstructor(
        prefs: SharedPreferences,
        runtimeKey: String,
        name: String,
        constructor: Constructor<*>
    ) {
        constructorStore[name] = constructor
    }

    fun clear(prefs: SharedPreferences, runtimeKey: String, name: String) {
        cleared.add(name)
        store.remove(name)
        constructorStore.remove(name)
    }

    fun reset() {
        store.clear()
        constructorStore.clear()
        cleared.clear()
        crossProcessReady = true
    }
}