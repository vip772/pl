package t.mma.hooks.items.homeextension

import android.view.View

/** Background beauty must leave this feature's surfaces and avatar feedback intact. */
object HomeExtensionSurface {
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