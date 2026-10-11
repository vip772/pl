package t.mma.hooks.items.hchatextra

import android.view.View
import android.view.ViewTreeObserver
import java.util.WeakHashMap

/** Keeps a failed card bind pending until the host row receives a usable layout. */
internal class MessageDetailsPendingLayoutObserver {
    private val entries = WeakHashMap<View, Entry>()

    fun observe(root: View, action: () -> Boolean) {
        entries[root]?.let {
            it.updateAction(action)
            return
        }
        val entry = Entry(root, action)
        entries[root] = entry
        root.addOnAttachStateChangeListener(entry)
        if (root.isAttachedToWindow) entry.attach(root)
    }

    fun cancel(root: View) {
        entries.remove(root)?.dispose()
    }

    fun clear() {
        val old = entries.values.toList()
        entries.clear()
        old.forEach { it.dispose() }
    }

    private inner class Entry(
        private val root: View,
        private var action: () -> Boolean
    ) : View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener {
        private var observer: ViewTreeObserver? = null
        private var active = true
        private var posted = false
        private var polling = false
        // Poll briefly for initial measurement; afterwards only real layout/attach
        // events may retry. An unsupported holder must not spin every 32 ms forever.
        private var pollsRemaining = 8
        private val poll = Runnable { onPoll() }

        fun updateAction(next: () -> Boolean) { action = next }
        fun attach(view: View) {
            if (!active) return
            removeObserver()
            val current = view.viewTreeObserver
            if (current.isAlive) {
                current.addOnGlobalLayoutListener(this)
                observer = current
            }
            schedulePoll()
        }
        override fun onViewAttachedToWindow(view: View) = attach(view)
        override fun onViewDetachedFromWindow(view: View) {
            removeObserver()
            stopPoll()
            posted = false
        }
        override fun onGlobalLayout() = scheduleAction()

        private fun scheduleAction() {
            if (!active || entries[root] !== this || !root.isAttachedToWindow || posted) return
            posted = true
            root.post {
                posted = false
                if (!active || entries[root] !== this || !root.isAttachedToWindow) return@post
                if (action()) cancel(root) else schedulePoll()
            }
        }

        private fun schedulePoll() {
            if (!active || entries[root] !== this || !root.isAttachedToWindow || polling || pollsRemaining <= 0) return
            pollsRemaining--
            polling = true
            root.postDelayed(poll, 32L)
        }

        private fun onPoll() {
            polling = false
            if (!active || entries[root] !== this || !root.isAttachedToWindow) return
            scheduleAction()
            if (entries[root] === this) schedulePoll()
        }

        private fun stopPoll() {
            if (polling) root.removeCallbacks(poll)
            polling = false
        }

        private fun removeObserver() {
            observer?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(this)
            val current = root.viewTreeObserver
            if (current !== observer && current.isAlive) current.removeOnGlobalLayoutListener(this)
            observer = null
        }

        fun dispose() {
            active = false
            stopPoll()
            removeObserver()
            root.removeOnAttachStateChangeListener(this)
            action = { true }
        }
    }
}
