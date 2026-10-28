package t.mma.hooks.items.floatingchat

import android.graphics.drawable.Drawable
import android.view.View
import t.mma.utils.KavaReflector
import java.lang.reflect.Method
import java.util.WeakHashMap

/** Own the ActionBar's independent drawable layers while its floating presentation is active. */
internal object FloatingChatActionBarBackgrounds {
    private val originals = WeakHashMap<View, MutableMap<Method, Drawable?>>()
    private var writing = false

    fun contains(view: View): Boolean = originals.containsKey(view)

    fun claim(view: View) {
        if (originals.containsKey(view)) return
        val layers = linkedMapOf<Method, Drawable?>()
        for ((setter, field) in listOf("setPrimaryBackground" to "h", "setStackedBackground" to "i", "setSplitBackground" to "m")) {
            val method = KavaReflector.findMethodRecursive(view.javaClass, setter, Drawable::class.java) ?: continue
            val nativeField = KavaReflector.findFieldRecursive(view.javaClass, field) ?: continue
            nativeField.isAccessible = true
            layers[method] = nativeField.get(view) as? Drawable
        }
        originals[view] = layers
        writing = true
        try { layers.keys.forEach { it.invoke(view, null) } } finally { writing = false }
    }

    // Called BEFORE the native setter: save its requested value, then let the
    // caller pass null once. No reflective setter call or recursive Hook entry.
    fun onNativeWrite(view: View, method: Method, drawable: Drawable?): Boolean {
        if (writing) return false
        val layers = originals[view] ?: return false
        if (!layers.containsKey(method)) return false
        layers[method] = drawable
        return true
    }

    fun restore(view: View) {
        val layers = originals.remove(view) ?: return
        writing = true
        try { layers.forEach { (method, drawable) -> method.invoke(view, drawable) } } finally { writing = false }
    }
}
