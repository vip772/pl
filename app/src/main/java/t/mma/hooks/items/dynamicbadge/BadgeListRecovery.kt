package t.mma.hooks.items.dynamicbadge

import android.view.View
import android.view.ViewTreeObserver

/** Coalesced, layout-driven recovery for lists populated after the first resume. */
internal class BadgeListRecovery(
    private val host: View,
    private val onError: (Throwable) -> Unit = {},
    private var repair: () -> Unit
) : View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener {
    private var observer: ViewTreeObserver? = null
    private var disposed = false
    private var posted = false
    private val runRepair = Runnable {
        posted = false
        if (!disposed && host.isAttachedToWindow) repair()
    }
    private val retries = listOf(100L, 300L, 1000L, 3000L).map { delay ->
        delay to Runnable { schedule() }
    }

    init {
        try {
            host.addOnAttachStateChangeListener(this)
            if (host.isAttachedToWindow) attach()
        } catch (error: Throwable) {
            dispose()
            throw error
        }
    }
    private fun attach() {
        if (disposed) return
        cancelPending()
        detachObserver()
        host.viewTreeObserver.takeIf { it.isAlive }?.let {
            observer = it
            it.addOnGlobalLayoutListener(this)
        }
        schedule()
        retries.forEach { (delay, callback) -> host.postDelayed(callback, delay) }
    }
    private fun schedule() {
        if (disposed || posted || !host.isAttachedToWindow) return
        posted = true
        try { host.postOnAnimation(runRepair) } catch (error: Throwable) {
            posted = false
            throw error
        }
    }
    override fun onGlobalLayout() = schedule()
    override fun onViewAttachedToWindow(view: View) { if (!disposed) attach() }
    override fun onViewDetachedFromWindow(view: View) {
        cancelPending()
        detachObserver()
    }
    private fun release(block: () -> Unit) {
        try { block() } catch (error: Throwable) {
            try { onError(error) } catch (_: Throwable) { }
        }
    }
    private fun cancelPending() {
        posted = false
        release { host.removeCallbacks(runRepair) }
        retries.forEach { (_, callback) -> release { host.removeCallbacks(callback) } }
    }
    private fun detachObserver() {
        val old = observer
        observer = null
        release { old?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(this) }
        release {
            val current = host.viewTreeObserver
            if (current !== old && current.isAlive) current.removeOnGlobalLayoutListener(this)
        }
    }
    fun dispose() {
        if (disposed) return
        disposed = true
        repair = {}
        cancelPending()
        detachObserver()
        release { host.removeOnAttachStateChangeListener(this) }
    }
}
