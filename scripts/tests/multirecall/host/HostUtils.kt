package t.mma.utils

import java.lang.reflect.Field
import java.lang.reflect.Method

object HLog {
    val logs = mutableListOf<String>()
    fun d(message: String) {}
    fun i(message: String) { logs.add(message) }
    fun w(message: String) { logs.add(message) }
    fun e(message: String) { logs.add(message) }
    fun e(message: String, throwable: Throwable?) { logs.add(message) }
}

/**
 * Synthetic reflection surface: plain JVM reflection, no obfuscation. Every helper the
 * production runtimes use is implemented so their files compile unchanged.
 */
object KavaReflector {
    @JvmStatic
    fun loadClass(name: String?, classLoader: ClassLoader?): Class<*>? {
        if (name.isNullOrEmpty()) return null
        return runCatching { Class.forName(name, false, classLoader) }.getOrNull()
    }

    @JvmStatic
    fun declaredMethods(clazz: Class<*>?): List<Method> =
        clazz?.declaredMethods?.toList() ?: emptyList()

    @JvmStatic
    fun declaredFields(clazz: Class<*>?): List<Field> =
        clazz?.declaredFields?.toList() ?: emptyList()

    @JvmStatic
    fun isStatic(method: Method?): Boolean =
        method != null && java.lang.reflect.Modifier.isStatic(method.modifiers)

    @JvmStatic
    fun isStatic(field: Field?): Boolean =
        field != null && java.lang.reflect.Modifier.isStatic(field.modifiers)

    @JvmStatic
    fun findMethodRecursive(clazz: Class<*>?, name: String?, vararg parameterTypes: Class<*>): Method? {
        if (clazz == null || name == null) return null
        var owner: Class<*>? = clazz
        while (owner != null) {
            val found = runCatching { owner.getDeclaredMethod(name, *parameterTypes) }.getOrNull()
            if (found != null) return found
            owner = owner.superclass
        }
        return null
    }

    @JvmStatic
    fun findFieldRecursive(clazz: Class<*>?, name: String?): Field? {
        if (clazz == null || name == null) return null
        var owner: Class<*>? = clazz
        while (owner != null) {
            val found = runCatching { owner.getDeclaredField(name) }.getOrNull()
            if (found != null) return found
            owner = owner.superclass
        }
        return null
    }

    @JvmStatic
    fun readField(receiver: Any?, fieldName: String?): Any? {
        if (receiver == null || fieldName == null) return null
        return readField(findFieldRecursive(receiver.javaClass, fieldName), receiver)
    }

    @JvmStatic
    fun readField(field: Field?, receiver: Any?): Any? {
        field ?: return null
        return runCatching {
            field.isAccessible = true
            field.get(receiver)
        }.getOrNull()
    }

    @JvmStatic
    fun writeField(receiver: Any?, fieldName: String?, value: Any?): Boolean {
        if (receiver == null || fieldName == null) return false
        return writeField(findFieldRecursive(receiver.javaClass, fieldName), receiver, value)
    }

    @JvmStatic
    fun writeField(field: Field?, receiver: Any?, value: Any?): Boolean {
        field ?: return false
        return runCatching {
            field.isAccessible = true
            field.set(receiver, value)
            true
        }.getOrDefault(false)
    }

    @JvmStatic
    fun invoke(method: Method?, receiver: Any?, vararg args: Any?): Any? {
        method ?: return null
        return runCatching {
            method.isAccessible = true
            method.invoke(receiver, *args)
        }.getOrNull()
    }

    @JvmStatic
    fun invokeMethod(receiver: Any?, name: String?, vararg args: Any?): Any? {
        if (receiver == null || name == null) return null
        val method = findCompatibleMethod(receiver.javaClass, name, *args) ?: return null
        return invoke(method, receiver, *args)
    }

    @JvmStatic
    fun invokeOrThrow(method: Method?, receiver: Any?, vararg args: Any?): Any? {
        method ?: error("missing method")
        method.isAccessible = true
        return method.invoke(receiver, *args)
    }

    @JvmStatic
    fun invokeSuccessfully(method: Method?, receiver: Any?, vararg args: Any?): Boolean {
        method ?: return false
        return runCatching {
            method.isAccessible = true
            method.invoke(receiver, *args)
            true
        }.getOrDefault(false)
    }

    @JvmStatic
    fun findCompatibleMethod(clazz: Class<*>?, name: String?, vararg args: Any?): Method? {
        if (clazz == null || name == null) return null
        var owner: Class<*>? = clazz
        while (owner != null) {
            for (method in owner.declaredMethods) {
                if (method.name != name) continue
                if (method.parameterTypes.size != args.size) continue
                val compatible = method.parameterTypes.withIndex().all { (index, type) ->
                    args[index] == null || box(type).isInstance(args[index])
                }
                if (compatible) return method
            }
            owner = owner.superclass
        }
        return null
    }

    private fun box(type: Class<*>): Class<*> = when (type) {
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        else -> type
    }
}
