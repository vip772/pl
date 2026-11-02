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

/** Synthetic reflection surface: plain JVM reflection, no obfuscation. */
object KavaReflector {
    @JvmStatic
    fun declaredFields(clazz: Class<*>?): List<Field> = clazz?.declaredFields?.toList() ?: emptyList()

    @JvmStatic
    fun declaredMethods(clazz: Class<*>?): List<Method> = clazz?.declaredMethods?.toList() ?: emptyList()

    @JvmStatic
    fun isStatic(field: Field?): Boolean = field != null && java.lang.reflect.Modifier.isStatic(field.modifiers)

    @JvmStatic
    fun isStatic(method: Method?): Boolean = method != null && java.lang.reflect.Modifier.isStatic(method.modifiers)

    @JvmStatic
    fun findMethod(clazz: Class<*>?, name: String?): Method? {
        if (clazz == null || name == null) return null
        var owner: Class<*>? = clazz
        while (owner != null) {
            owner.declaredMethods.firstOrNull { it.name == name && it.parameterCount == 0 }?.let { return it }
            owner = owner.superclass
        }
        return null
    }

    @JvmStatic
    fun findDeclaredField(clazz: Class<*>?, name: String?): Field? {
        if (clazz == null || name == null) return null
        return clazz.declaredFields.firstOrNull { it.name == name }
    }

    @JvmStatic
    fun readField(receiver: Any?, name: String?): Any? {
        if (receiver == null || name == null) return null
        var owner: Class<*>? = receiver.javaClass
        while (owner != null) {
            owner.declaredFields.firstOrNull { it.name == name }?.let { return readField(it, receiver) }
            owner = owner.superclass
        }
        return null
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
    fun invoke(method: Method?, receiver: Any?, vararg args: Any?): Any? {
        method ?: return null
        return runCatching {
            method.isAccessible = true
            method.invoke(receiver, *args)
        }.getOrNull()
    }
}
