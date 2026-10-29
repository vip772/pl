package t.mma.hooks.items.floatingchat

import android.view.View
import android.view.ViewGroup
import java.lang.reflect.Method

/** One synchronous padding/layout transaction; no observer, timer or scroll callback. */
internal class ChatListBottomInset {
    private var owner: View? = null
    private var countMethod: Method? = null
    private var lastMethod: Method? = null
    private var scrollStateMethod: Method? = null
    private var count = 0
    private var gap = 0
    private var shortContent = false

    fun beforePadding(list: View, target: Int) {
        ChatBottomTrace.event(list, "anchor-before") {
            "target=$target pendingCount=$count " + ChatBottomTrace.listGeometry(list)
        }
        if (count != 0 && owner === list) { traceSkip(list, "pending-anchor"); return }
        clear()
        if (list !is ViewGroup || target == list.paddingBottom ||
            list.canScrollVertically(1)) { traceSkip(list, "type-unchanged-or-history"); return }
        if (owner !== list) {
            owner = list
            countMethod = method(list, "getCount")
            lastMethod = method(list, "getLastVisiblePosition")
            scrollStateMethod = method(list, "getScrollState")
        }
        if (value(scrollStateMethod, list) != 0) { traceSkip(list, "scroll-state"); return }
        val size = value(countMethod, list) ?: run { traceSkip(list, "count-unavailable"); return }
        if (size <= 0 || value(lastMethod, list) != size - 1) { traceSkip(list, "last-position"); return }
        val row = list.getChildAt(list.childCount - 1) ?: return
        val oldGap = list.height - list.paddingBottom - row.bottom
        // Consume short-conversation bottom space before lifting covered rows.
        shortContent = oldGap > 2 || !list.canScrollVertically(-1)
        if (!shortContent && oldGap !in -2..2) { traceSkip(list, "bottom-gap"); return }
        count = size
        gap = if (shortContent) 0 else oldGap
        ChatBottomTrace.event(list, "anchor-captured") { "count=$count gap=$gap short=$shortContent target=$target" }
    }

    fun afterLayout(list: View) {
        if (count == 0) return
        val expected = count
        val wasShort = shortContent
        ChatBottomTrace.event(list, "anchor-after") {
            "expected=$expected gap=$gap short=$wasShort " + ChatBottomTrace.listGeometry(list)
        }
        clear() // Consume before scrollBy, which may synchronously lay out children.
        if (list !== owner || list !is ViewGroup ||
            value(scrollStateMethod, list) != 0 || value(countMethod, list) != expected ||
            value(lastMethod, list) != expected - 1) { traceSkip(list, "layout-owner-motion-or-data-changed"); return }
        val row = list.getChildAt(list.childCount - 1) ?: return
        val overflow = row.bottom - (list.height - list.paddingBottom - gap)
        // Native LinearLayoutManager may already have compensated. Never move twice,
        // select another item, follow an append, or pull history back to the bottom.
        ChatBottomTrace.event(list, "anchor-result") { "overflow=$overflow short=$wasShort apply=${overflow != 0 && (!wasShort || overflow > 0)}" }
        if (overflow != 0 && (!wasShort || overflow > 0)) list.scrollBy(0, overflow)
    }

    private fun traceSkip(list: View, reason: String) {
        ChatBottomTrace.event(list, "anchor-skip-$reason") { reason }
    }
    fun clear() { count = 0 }
    private fun method(view: View, name: String): Method? =
        runCatching { view.javaClass.getMethod(name) }.getOrNull()
    private fun value(method: Method?, view: View): Int? =
        runCatching { (method?.invoke(view) as? Number)?.toInt() }.getOrNull()
}
