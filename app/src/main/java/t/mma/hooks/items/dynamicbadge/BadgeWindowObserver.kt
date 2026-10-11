package t.mma.hooks.items.dynamicbadge

import android.view.View
import android.view.ViewTreeObserver

/** A registration belongs to a window observer, not permanently to a recycled row. */
internal class BadgeWindowObserver(
    private val host: View,
    private val action: () -> Unit
) {
    private var observer: ViewTreeObserver? = null
    private var disposed = false
    private val listener = ViewTreeObserver.OnPreDrawListener {
        if (!disposed && host.isAttachedToWindow) action()
        true
    }

    fun attach() {
        if (disposed || !host.isAttachedToWindow) return
        val current = host.viewTreeObserver
        if (observer === current) return
        detach()
        if (current.isAlive) {
            current.addOnPreDrawListener(listener)
            observer = current
        }
    }

    fun detach() {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(listener)
        // Android may merge a dead observer's listeners into its new window.
        val current = host.viewTreeObserver
        if (current !== observer && current.isAlive) current.removeOnPreDrawListener(listener)
        observer = null
    }

    fun dispose() {
        disposed = true
        detach()
    }
}
