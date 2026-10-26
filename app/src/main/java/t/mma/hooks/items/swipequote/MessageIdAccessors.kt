package t.mma.hooks.items.swipequote

import t.mma.utils.KavaReflector
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Reuse successfully resolved members; never retain message objects or ID values. */
internal class MessageIdAccessors {
    private class Members {
        val methods = ConcurrentHashMap<String, Method>()
        val fields = ConcurrentHashMap<String, Field>()
    }
    private val byClass = ConcurrentHashMap<Class<*>, Members>()

    fun read(message: Any): Long {
        val type = message.javaClass
        val members = byClass.getOrPut(type) { Members() }
        for (name in METHOD_NAMES) {
            // Missing/failed lookups remain retryable: KavaReflector returns null for both.
            val method = members.methods[name] ?: KavaReflector.findMethod(type, name)?.also {
                members.methods.putIfAbsent(name, it)
            }
            positive(KavaReflector.invoke(method, message))?.let { return it }
        }
        for (name in FIELD_NAMES) {
            val field = members.fields[name] ?: findField(type, name)?.also {
                members.fields.putIfAbsent(name, it)
            }
            positive(KavaReflector.readField(field, message))?.let { return it }
        }
        return 0L
    }

    private fun findField(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            KavaReflector.findDeclaredField(current, name)?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun positive(value: Any?): Long? {
        val parsed = when (value) {
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull()
            else -> null
        }
        return parsed?.takeIf { it > 0L }
    }

    private companion object {
        val METHOD_NAMES = arrayOf("getMsgId", "getMsgID", "getId")
        val FIELD_NAMES = arrayOf("field_msgId", "msgId", "msgID", "id")
    }
}
