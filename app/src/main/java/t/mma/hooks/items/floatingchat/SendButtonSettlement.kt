package t.mma.hooks.items.floatingchat

import android.view.View
import android.widget.Button
import java.util.WeakHashMap

/** Event-driven repair only; no frame loop, child traversal or visibility override. */
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
    fun schedule(footer: View, currentButton: () -> Button?) {
        if (pending.containsKey(footer)) return
        val task = Runnable {
            pending.remove(footer)
            if (footer.isAttachedToWindow && active[footer].isNullOrEmpty()) {
                val button = currentButton()
                if (button != null && button.isAttachedToWindow && button.visibility == View.VISIBLE &&
                    button.isShown && button.alpha != 1f) button.alpha = 1f
            }
        }
        pending[footer] = task
        footer.postOnAnimation(task)
    }
    fun cancel(footer: View) {
        pending.remove(footer)?.let(footer::removeCallbacks)
        active.remove(footer)
    }
}
