package t.mma.hooks.items.backgroundbeauty

import t.mma.utils.KavaReflector
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/** Successful metadata only; values and lookup failures are never retained. */
internal class NativeFieldAccessors {
    private val fields = ConcurrentHashMap<Class<*>, ConcurrentHashMap<String, Field>>()

    fun read(owner: Any?, name: String): Any? {
        owner ?: return null
        val members = fields.getOrPut(owner.javaClass) { ConcurrentHashMap() }
        val field = members[name] ?: resolve(owner.javaClass, name)?.also {
            members.putIfAbsent(name, it)
        }
        return KavaReflector.readField(field, owner)
    }

    private fun resolve(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            KavaReflector.findDeclaredField(current, name)?.let { return it }
            current = current.superclass
        }
        return null
    }
}
