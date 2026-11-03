package t.mma.hooks.items.dynamicbadge

import android.view.View
import android.view.ViewTreeObserver

/** A registration belongs to a window observer, not permanently to a recycled row. */
internal class BadgeWindowObserver(
    private val host: View,
    private val onError: (Throwable) -> Unit = {},
    private var action: () -> Unit
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
            observer = current
            try { current.addOnPreDrawListener(listener) } catch (error: Throwable) {
                detach()
                throw error
            }
        }
    }

    private fun release(block: () -> Unit) {
        try { block() } catch (error: Throwable) {
            try { onError(error) } catch (_: Throwable) { }
        }
    }
    fun detach() {
        val old = observer
        observer = null
        release { old?.takeIf { it.isAlive }?.removeOnPreDrawListener(listener) }
        release {
            val current = host.viewTreeObserver
            if (current !== old && current.isAlive) current.removeOnPreDrawListener(listener)
        }
    }
    fun dispose() {
        if (disposed) return
        disposed = true
        action = {}
        detach()
    }
}
