package t.mma.utils
object HLog {
    val logs = mutableListOf<String>()
    fun d(message: String) {}
    fun i(message: String) { logs.add(message) }
    fun w(message: String) { logs.add(message) }
    fun e(message: String) { logs.add(message) }
    fun e(message: String, throwable: Throwable?) { logs.add(message) }
}

/**
 * Synthetic reflection surface: the class loader handed in decides what the
 * synthetic WeChat bridge looks like, so loader replacement is observable.
 */
object KavaReflector {
    private val bridges = LinkedHashMap<ClassLoader, String>()

    fun bridgeFor(loader: ClassLoader, className: String?) {
        if (className == null) bridges.remove(loader) else bridges[loader] = className
    }

    fun loadClass(name: String?, classLoader: ClassLoader?): Class<*>? {
        val loader = classLoader ?: return null
        val mapped = bridges[loader] ?: return null
        if (name != mapped) return null
        return SyntheticBridge::class.java
    }

    fun findMethod(
        clazz: Class<*>?,
        name: String?,
        vararg parameterTypes: Class<*>
    ): java.lang.reflect.Method? {
        if (clazz == null) return null
        return runCatching {
            clazz.getDeclaredMethod(name, *parameterTypes)
        }.getOrNull()
    }

    fun findMethodRecursive(clazz: Class<*>?, name: String?, vararg parameterTypes: Class<*>): java.lang.reflect.Method? =
        findMethod(clazz, name, *parameterTypes)

    fun isStatic(method: java.lang.reflect.Method?): Boolean =
        method != null && java.lang.reflect.Modifier.isStatic(method.modifiers)
}

/** Synthetic WeChat JS bridge class: exactly the signature the feature hooks. */
class SyntheticBridge {
    fun subscribeHandler(event: String, data: String, callbackId: Int, extra: String) {
        lastEvent = event
        lastData = data
    }

    companion object {
        var lastEvent: String? = null
        var lastData: String? = null
        fun reset() {
            lastEvent = null
            lastData = null
        }
    }
}