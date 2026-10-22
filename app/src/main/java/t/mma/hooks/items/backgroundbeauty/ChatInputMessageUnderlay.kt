package t.mma.hooks.items.backgroundbeauty

import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import t.mma.utils.HLog
import t.mma.utils.KavaReflector
import java.util.IdentityHashMap
import java.util.WeakHashMap

/** 8.0.78 ss.xml/t8.xml: preserve native footer and overlap its verified blr input region. */
internal class ChatInputMessageUnderlay {
    private class State(val content: View, val list: ViewGroup, val margin: Int, val clip: Boolean) {
        var basePadding = list.paddingBottom
        var appliedPadding = list.paddingBottom
        var overlap = 0
        var appliedMargin = margin
        var appliedClip = clip
        var lastDiagnostic = ""
    }
    private val states = WeakHashMap<ViewGroup, State>()
    fun apply(scroll: ViewGroup) {
        if (scroll.javaClass.name != SCROLL) return
        fun named(name: String): View? {
            val id = scroll.resources.getIdentifier(name, "id", scroll.context.packageName)
            return if (id == 0) null else scroll.findViewById<View>(id)
        }
        val content = named("bki") ?: return
        val footer = (named("k52") as? ViewGroup) ?: return
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
        val input = findEditText(footer)
        val stripRect = strip.screenRect()
        val footerRect = footer.screenRect()
        val contentRect = content.screenRect()
        val listRect = list.screenRect()
        val overlapRect = intersectionRect(listRect, footerRect)
        val coveringDescendants = collectCoveringDescendants(footer, overlapRect)
        val snapshot = "scroll=${scroll.diag()} content=${content.diag()} list=${list.diag()} footer=${footer.diag()} strip=${strip.diag()} input=${input?.diag()} inputAncestors=${input?.ancestorChain()} rects(content=$contentRect,list=$listRect,footer=$footerRect,strip=$stripRect,overlap=$overlapRect) listFooterOverlap=${intersectionHeight(listRect, footerRect)} contentFooterOverlap=${intersectionHeight(contentRect, footerRect)} stripContentOverlap=${intersectionHeight(stripRect, contentRect)} listPadding=${list.paddingLeft},${list.paddingTop},${list.paddingRight},${list.paddingBottom} clipToPadding=${list.clipToPadding} footerDescendantsWithSurfaces=$coveringDescendants"
        if (snapshot != state.lastDiagnostic) {
            HLog.i("[Hchat:沉浸式背景] CHROMEINPUT_DIAG $snapshot")
            state.lastDiagnostic = snapshot
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

    private fun findEditText(root: View): EditText? {
        val queue = ArrayDeque<View>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            if (view is EditText) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) queue.add(view.getChildAt(i))
        }
        return null
    }

    private fun View.screenRect(): Rect {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + width, location[1] + height)
    }

    private fun View.ancestorChain(): List<String> {
        val chain = ArrayList<String>()
        var node: View? = this
        var depth = 0
        while (node != null && depth++ < 24) {
            chain += node.diag()
            node = node.parent as? View
        }
        return chain
    }

    private fun intersectionRect(a: Rect, b: Rect): Rect = Rect(
        maxOf(a.left, b.left), maxOf(a.top, b.top),
        minOf(a.right, b.right).coerceAtLeast(maxOf(a.left, b.left)),
        minOf(a.bottom, b.bottom).coerceAtLeast(maxOf(a.top, b.top))
    )

    private fun intersectionHeight(a: Rect, b: Rect): Int =
        (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0)

    private fun View.diag(): String {
        val bg = background
        val fg = (this as? ViewGroup)?.foreground
        val idName = if (id == View.NO_ID) "-" else runCatching {
            resources.getResourceEntryName(id)
        }.getOrDefault("?")
        val bounds = screenRect()
        val identity = IdentityHashMap<View, Boolean>()
        var node: View? = this
        var parentClip = true
        while (node is View) {
            if (identity.put(node, true) != null) break
            if (node !== this && node is ViewGroup && (!node.clipChildren || !node.clipToPadding)) parentClip = false
            node = node.parent as? View
        }
        return "${javaClass.name}#$idName@${System.identityHashCode(this)} rect=$bounds bg=${bg?.javaClass?.name}:${(bg as? ColorDrawable)?.color} fg=${fg?.javaClass?.name}:${(fg as? ColorDrawable)?.color} visibility=$visibility shown=$isShown elevation=$elevation z=$z clipChildren=${(this as? ViewGroup)?.clipChildren} clipToPadding=${(this as? ViewGroup)?.clipToPadding} parentClip=$parentClip"
    }

    private fun collectCoveringDescendants(root: ViewGroup, target: Rect): List<String> {
        val result = ArrayList<String>()
        val queue = ArrayDeque<View>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 240) {
            val view = queue.removeFirst()
            if (target.width() > 0 && target.height() > 0 && view !== root && view.visibility == View.VISIBLE && view.isShown && Rect.intersects(view.screenRect(), target)) {
                val bg = view.background
                val fg = (view as? ViewGroup)?.foreground
                if (bg != null || fg != null) result += view.diag()
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) queue.add(view.getChildAt(i))
        }
        return result
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
