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

    fun beforePadding(list: View, target: Int) {
        if (count != 0 && owner === list) return
        clear()
        if (list !is ViewGroup || target == list.paddingBottom ||
            !list.canScrollVertically(-1) || list.canScrollVertically(1)) return
        if (owner !== list) {
            owner = list
            countMethod = method(list, "getCount")
            lastMethod = method(list, "getLastVisiblePosition")
            scrollStateMethod = method(list, "getScrollState")
        }
        if (value(scrollStateMethod, list) != 0) return
        val size = value(countMethod, list) ?: return
        if (size <= 0 || value(lastMethod, list) != size - 1) return
        val row = list.getChildAt(list.childCount - 1) ?: return
        val oldGap = list.height - list.paddingBottom - row.bottom
        if (oldGap !in -2..2) return
        count = size
        gap = oldGap
    }

    fun afterLayout(list: View) {
        if (count == 0) return
        val expected = count
        clear() // Consume before scrollBy, which may synchronously lay out children.
        if (list !== owner || list !is ViewGroup ||
            value(scrollStateMethod, list) != 0 || value(countMethod, list) != expected ||
            value(lastMethod, list) != expected - 1) return
        val row = list.getChildAt(list.childCount - 1) ?: return
        val overflow = row.bottom - (list.height - list.paddingBottom - gap)
        // Native LinearLayoutManager may already have compensated. Never move twice,
        // select another item, follow an append, or pull history back to the bottom.
        if (overflow != 0) list.scrollBy(0, overflow)
    }

    fun clear() { count = 0 }
    private fun method(view: View, name: String): Method? =
        runCatching { view.javaClass.getMethod(name) }.getOrNull()
    private fun value(method: Method?, view: View): Int? =
        runCatching { (method?.invoke(view) as? Number)?.toInt() }.getOrNull()
}
