package t.mma.hooks.items.backgroundbeauty

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import t.mma.utils.KavaReflector
import java.util.WeakHashMap

/** 8.0.78 ss.xml/t8.xml: preserve native footer and overlap its verified blr input region. */
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
        val strip = named("blr") ?: return
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
        // dd6383f/eb6e056 verified the reserved footer region as blr in t8.xml.
        // Use its measured height, capped by the host footer, so panel/keyboard height is excluded.
        val overlap = if (footer.visibility == View.VISIBLE && strip.visibility == View.VISIBLE)
            strip.height.coerceAtMost(footer.height).coerceAtLeast(0) else 0
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
