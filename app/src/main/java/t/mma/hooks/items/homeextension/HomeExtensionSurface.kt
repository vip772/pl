package t.mma.hooks.items.homeextension

import android.view.View

/** Background beauty must leave this feature's surfaces and avatar feedback intact. */
object HomeExtensionSurface {
    private val geometryOwners = java.util.WeakHashMap<View, Boolean>()
    @JvmStatic fun ownGeometry(view: View, owned: Boolean) {
        synchronized(geometryOwners) { if (owned) geometryOwners[view] = true else geometryOwners.remove(view) }
    }
    @JvmStatic fun ownsGeometry(view: View): Boolean = synchronized(geometryOwners) { geometryOwners.containsKey(view) }
    interface Root
    fun contains(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current is Root) return true
            current = current.parent as? View
        }
        return false
    }
}