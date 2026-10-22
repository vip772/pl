package t.mma.hooks.items.backgroundbeauty

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import t.mma.utils.KavaReflector
import java.util.WeakHashMap

/** 8.0.78 ss.xml/t8.xml: keep native footer/panel hierarchy and overlap only the input strip. */
internal class ChatInputMessageUnderlay {
    private class State(val content: View, val list: ViewGroup, val margin: Int, val clip: Boolean) {
        var basePadding = list.paddingBottom
        var appliedPadding = list.paddingBottom
        var overlap = 0
        var appliedMargin = margin
        var appliedClip = clip
    }
    private val states = WeakHashMap<ViewGroup, State>()

    fun apply(scroll: ViewGroup) {
        if (scroll.javaClass.name != SCROLL) return
        fun named(name: String): View? {
            val id = scroll.resources.getIdentifier(name, "id", scroll.context.packageName)
            return if (id == 0) null else scroll.findViewById<View>(id)
        }
        val content = named("bki") ?: return
        val footer = named("k52") ?: return
        val strip = named("bfp") ?: return
        val wrapper = named("bm6") ?: return
        if (content.parent !== scroll || footer.parent !== scroll ||
            content.javaClass.name != "com.tencent.mm.pluginsdk.ui.chat.ChattingContent" ||
            footer.javaClass.name != "com.tencent.mm.pluginsdk.ui.chat.ChatFooter") return
        val lp = content.layoutParams as? LinearLayout.LayoutParams ?: return
        // Native getListView() returns u3; in 8.0.78 the concrete instance is a View (RecyclerView).
        val method = KavaReflector.findMethodRecursive(wrapper.javaClass, "getListView") ?: return
        val list = KavaReflector.invokeOrThrow(method, wrapper) as? ViewGroup ?: return
        val state = states[scroll]?.takeIf { it.content === content && it.list === list } ?: run {
            restore(scroll)
            State(content, list, lp.bottomMargin, list.clipToPadding).also { states[scroll] = it }
        }
        // t8.xml: the outer bfp is the actual input+panel container; ssr is the input row.
        // bfp owns @color/a9e. Its actual measured height includes the input, AppPanel and mode changes.
        // The independent emoji panel stays in place above the overlapped input strip.
        val input = named("ssr") ?: return
        val panel = named("bkc") ?: return
        // bfp itself is the solid outer surface; ssr supplies the row height, while bkc contains
        // the independently positioned AppPanel. Overlap only the opaque strip above that panel.
        val panelVisible = panel.visibility == View.VISIBLE && panel.height > 0
        fun topInAncestor(view: View, ancestor: View): Int? {
            var current: View = view
            var top = 0
            while (current !== ancestor) {
                top += current.top
                current = current.parent as? View ?: return null
            }
            return top
        }
        val inputTop = topInAncestor(input, strip) ?: return
        val panelTop = topInAncestor(panel, strip)
        val stripBottom = if (panelVisible) panelTop ?: return else inputTop + input.height
        val overlap = if (footer.visibility == View.VISIBLE && input.visibility == View.VISIBLE)
            (stripBottom - inputTop).coerceIn(0, strip.height) else 0
        if (list.paddingBottom != state.appliedPadding) state.basePadding = list.paddingBottom
        val padding = state.basePadding + overlap
        if (list.paddingBottom != padding) {
            list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, padding)
        }
        state.appliedPadding = padding
        state.appliedClip = if (overlap > 0) false else state.clip
        if (list.clipToPadding != state.appliedClip) list.clipToPadding = state.appliedClip
        state.appliedMargin = state.margin - overlap
        if (lp.bottomMargin != state.appliedMargin) {
            lp.bottomMargin = state.appliedMargin
            content.layoutParams = lp
        }
        state.overlap = overlap
    }

    fun restore(scroll: ViewGroup) {
        val state = states.remove(scroll) ?: return
        val lp = state.content.layoutParams as? LinearLayout.LayoutParams
        if (lp != null && lp.bottomMargin == state.appliedMargin) {
            lp.bottomMargin = state.margin
            state.content.layoutParams = lp
        }
        val list = state.list
        if (list.paddingBottom == state.appliedPadding) {
            list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, state.basePadding)
        }
        if (list.clipToPadding == state.appliedClip) list.clipToPadding = state.clip
    }

    fun restoreAll() { states.keys.toList().forEach(::restore) }
    companion object { const val SCROLL = "com.tencent.mm.pluginsdk.ui.chat.ChattingScrollLayout" }
}
