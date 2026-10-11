package t.mma.hooks.items.dynamicbadge

import android.view.View
import android.view.ViewTreeObserver

/** Coalesced, layout-driven recovery for lists populated after the first resume. */
internal class BadgeListRecovery(
    private val host: View,
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
        host.addOnAttachStateChangeListener(this)
        if (host.isAttachedToWindow) attach()
    }
    private fun attach() {
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
        host.postOnAnimation(runRepair)
    }
    override fun onGlobalLayout() = schedule()
    override fun onViewAttachedToWindow(view: View) { if (!disposed) attach() }
    override fun onViewDetachedFromWindow(view: View) {
        cancelPending()
        detachObserver()
    }
    private fun cancelPending() {
        posted = false
        host.removeCallbacks(runRepair)
        retries.forEach { (_, callback) -> host.removeCallbacks(callback) }
    }
    private fun detachObserver() {
        observer?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(this)
        val current = host.viewTreeObserver
        if (current !== observer && current.isAlive) current.removeOnGlobalLayoutListener(this)
        observer = null
    }
    fun dispose() {
        disposed = true
        cancelPending()
        detachObserver()
        host.removeOnAttachStateChangeListener(this)
        repair = {}
    }
}