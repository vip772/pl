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

/** 8.0.78 ss.xml/t8.xml: preserve native footer and overlap the visible ssr input row, excluding the app panel. */
internal class ChatInputMessageUnderlay {
    private class State(val content: View, val list: ViewGroup, val margin: Int, val clip: Boolean) {
        var basePadding = list.paddingBottom
        var appliedPadding = list.paddingBottom
        var overlap = 0
        var appliedMargin = margin
        var appliedClip = clip
        var nativeHeight = -1
        var expandedHeight = -1
        var nativeWidth = 0
        var nativeLeft = 0
        var nativeTop = 0
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
        val strip = named("ssr") ?: return
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
        // t8.xml: blr is ABOVE bl8/ssr, not the input row. Runtime logs show
        // blr.height=0 while ssr.height=171. Reserve only footer-top..row-bottom;
        // bkc/AppPanel below ssr must never contribute to this overlap.
        val rowOverlap = inputRowOverlap(footer, strip)
        // Native footer includes an offscreen panel. Cover the short visible tail
        // below ssr as well, but never extend through an opened panel.
        val visibleFooter = intersectionRect(scroll.screenRect(), footer.screenRect())
        val tail = (visibleFooter.bottom - stripRect.bottom).coerceAtLeast(0)
        val bottomTail = if (rowOverlap > 0 && tail < strip.height) tail else 0
        val overlap = rowOverlap + bottomTail
        if (list.paddingBottom != state.appliedPadding) state.basePadding = list.paddingBottom
        val padding = state.basePadding + overlap
        if (list.paddingBottom != padding) {
            list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, padding)
        }
        state.appliedPadding = padding
        state.appliedClip = if (overlap > 0) false else state.clip
        if (list.clipToPadding != state.appliedClip) list.clipToPadding = state.appliedClip
        // Never change LinearLayout margins: they also move the following footer.
        // Enlarge only the content AFTER native layout, leaving footer bounds/hit area intact.
        if (state.expandedHeight < 0 || content.height != state.expandedHeight) {
            state.nativeHeight = content.height
            state.nativeWidth = content.width
            state.nativeLeft = content.left
            state.nativeTop = content.top
        }
        val height = state.nativeHeight + overlap
        if (content.height != height) resizeContent(state, height)
        state.expandedHeight = height
        state.overlap = overlap
    }

    private fun resizeContent(state: State, height: Int) {
        state.content.measure(
            View.MeasureSpec.makeMeasureSpec(state.nativeWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        state.content.layout(state.nativeLeft, state.nativeTop,
            state.nativeLeft + state.nativeWidth, state.nativeTop + height)
    }

    // Called before native onLayout: native measurement/placement must never consume
    // our enlarged measured height, otherwise footer placement drifts on the next pass.
    fun beforeLayout(scroll: ViewGroup) {
        val state = states[scroll] ?: return
        if (state.expandedHeight >= 0 && state.content.measuredHeight == state.expandedHeight) {
            resizeContent(state, state.nativeHeight)
        }
        state.expandedHeight = -1
    }

    private fun inputRowOverlap(footer: ViewGroup, row: View): Int {
        if (footer.visibility != View.VISIBLE || !row.isShown || row.height <= 0) return 0
        var node: View = row
        var bottom = row.height
        var depth = 0
        while (node !== footer && depth++ < 24) {
            bottom += node.top
            val parent = node.parent as? View ?: return 0
            if (parent.visibility != View.VISIBLE) return 0
            node = parent
        }
        return if (node === footer) bottom.coerceIn(0, footer.height.coerceAtLeast(0)) else 0
    }

    private fun findEditText(root: View): EditText? {
        val queue = ArrayDeque<View>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            if (view is EditText && view.isShown && view.width > 0 && view.height > 0) return view
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
        if (state.expandedHeight >= 0 && state.content.height == state.expandedHeight) {
            resizeContent(state, state.nativeHeight)
        }
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
