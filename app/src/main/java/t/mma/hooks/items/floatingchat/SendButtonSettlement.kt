package t.mma.hooks.items.floatingchat

import android.view.View
import android.animation.Animator
import java.util.WeakHashMap

/** Event-driven repair only; no frame loop or child traversal. */
internal object SendButtonSettlement {
    private val active = WeakHashMap<View, MutableSet<Any>>()
    private val pending = WeakHashMap<View, Runnable>()
    fun started(footer: View, animator: Any) {
        active.getOrPut(footer) { HashSet() }.add(animator)
    }
    fun finished(footer: View, animator: Any) {
        active[footer]?.let { animations ->
            animations.remove(animator)
            if (animations.isEmpty()) active.remove(footer)
        }
    }
    fun schedule(footer: View, currentButton: () -> View?) {
        if (pending.containsKey(footer)) return
        val task = Runnable {
            pending.remove(footer)
            // Some native cancellation paths remove listeners before cancel(), so
            // no end callback arrives. Prune only animators proven to have stopped.
            active[footer]?.let { animations ->
                animations.removeAll { it is Animator && !it.isStarted && !it.isRunning }
                if (animations.isEmpty()) active.remove(footer)
            }
            if (footer.isAttachedToWindow && active[footer].isNullOrEmpty()) {
                val button = currentButton()
                if (button != null && button.isAttachedToWindow && button.visibility == View.VISIBLE &&
                    button.isShown && button.alpha != 1f) button.alpha = 1f
            }
        }
        pending[footer] = task
        footer.postOnAnimation(task)
    }
    // Only called after native animations stop and the host confirms text mode.
    fun settleControls(send: View, attach: View, canSend: Boolean): View {
        val shown = if (canSend) send else attach
        val hidden = if (canSend) attach else send
        if (hidden.visibility != View.GONE) hidden.visibility = View.GONE
        if (shown.visibility != View.VISIBLE) shown.visibility = View.VISIBLE
        if (canSend) send.layoutParams?.let { params ->
            // Matches native h0.onAnimationEnd; interrupted width animators can
            // otherwise leave the send button at the smaller plus-button width.
            if (params.width != android.view.ViewGroup.LayoutParams.WRAP_CONTENT) {
                params.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                send.layoutParams = params
            }
        }
        return shown
    }
    fun cancel(footer: View) {
        pending.remove(footer)?.let(footer::removeCallbacks)
        active.remove(footer)
    }
}
