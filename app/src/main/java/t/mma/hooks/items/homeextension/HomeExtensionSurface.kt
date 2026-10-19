package t.mma.hooks.items.homeextension

import android.view.View

/** Background beauty must leave this feature's surfaces and avatar feedback intact. */
object HomeExtensionSurface {
    const val TAG = "Hchat.HomeExtension.Root"
    fun contains(view: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current.tag == TAG) return true
            current = current.parent as? View
        }
        return false
    }
}