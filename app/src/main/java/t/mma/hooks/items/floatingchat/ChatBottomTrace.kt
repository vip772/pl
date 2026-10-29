package t.mma.hooks.items.floatingchat

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import t.mma.utils.HLog
import java.util.WeakHashMap

/** Temporary event-driven diagnostics. No timer, listener or message content capture. */
internal object ChatBottomTrace {
    private class Gate(val start: Long) {
        var last = -1000L
        var count = 0
    }
    private val gates = WeakHashMap<View, MutableMap<String, Gate>>()
    /** Called only inside event's accepted lazy detail block, never on idle scroll. */
    fun listGeometry(view: View): String {
        fun number(name: String): Any? = runCatching {
            view.javaClass.getMethod(name).invoke(view)
        }.getOrNull()
        val group = view as? ViewGroup
        val row = group?.takeIf { it.childCount > 0 }?.getChildAt(group.childCount - 1)
        val listLocation = IntArray(2).also(view::getLocationOnScreen)
        val rowLocation = IntArray(2)
        row?.getLocationOnScreen(rowLocation)
        return "list=${view.javaClass.name}#${System.identityHashCode(view)} " +
            "count=${number("getCount")} last=${number("getLastVisiblePosition")} " +
            "scroll=${number("getScrollState")} up=${view.canScrollVertically(-1)} down=${view.canScrollVertically(1)} " +
            "children=${group?.childCount} y=${listLocation[1]} h=${view.height} padT=${view.paddingTop} padB=${view.paddingBottom} " +
            "ty=${view.translationY} pending=${view.isLayoutRequested} rowBottom=${row?.bottom} " +
            "rowScreenBottom=${row?.let { rowLocation[1] + it.height }} rowTy=${row?.translationY} " +
            "gap=${row?.let { view.height - view.paddingBottom - it.bottom }}"
    }
    fun event(view: View, phase: String, detail: () -> String) {
        val now = SystemClock.uptimeMillis()
        val gate = gates.getOrPut(view) { HashMap() }.getOrPut(phase) { Gate(now) }
        if (now - gate.start > 120_000L || gate.count >= 80 || now - gate.last < 500L) return
        gate.last = now
        gate.count++
        val chain = StringBuilder()
        var node: View? = view
        repeat(8) {
            val current = node ?: return@repeat
            if (chain.isNotEmpty()) chain.append(" <- ")
            chain.append(current.javaClass.simpleName).append('#').append(System.identityHashCode(current))
                .append('[').append(current.left).append(',').append(current.top).append(',')
                .append(current.width).append('x').append(current.height).append("] vis=").append(current.visibility)
                .append(" ty=").append(current.translationY).append(" alpha=").append(current.alpha).append(" pending=").append(current.isLayoutRequested)
                .append(" padB=").append(current.paddingBottom).append(" bg=")
                .append(current.background?.javaClass?.simpleName).append(':').append(current.background?.alpha)
                .append(" color=").append((current.background as? android.graphics.drawable.ColorDrawable)?.color)
                .append(" fg=").append(current.foreground?.javaClass?.simpleName)
            val margins = current.layoutParams as? ViewGroup.MarginLayoutParams
            if (margins != null) chain.append(" margin=").append(margins.topMargin).append('/').append(margins.bottomMargin)
            node = current.parent as? View
        }
        HLog.i("[Hchat:ChatBottomTrace] phase=$phase age=${now - gate.start} n=${gate.count} ${detail()} chain=$chain")
    }
}
