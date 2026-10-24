package t.mma.hooks.items.backgroundbeauty

import t.mma.utils.ChatScrollTrace
import t.mma.utils.ChatScrollDiagnostic
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import t.mma.utils.KavaReflector
import java.util.WeakHashMap

/** 8.0.78 ss.xml/t8.xml: preserve native footer and overlap the visible ssr input row, excluding the app panel. */
internal class ChatInputMessageUnderlay {
    private class State(val content: View, val list: ViewGroup, val margin: Int, val clip: Boolean) {
        var targets: Targets? = null
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
    }
    private class Targets(val content: View, val footer: ViewGroup, val strip: View, val wrapper: View, val list: ViewGroup) {
        fun valid(scroll: ViewGroup): Boolean = content.parent === scroll && footer.parent === scroll &&
            inside(strip, footer) && inside(wrapper, content) && inside(list, wrapper)
        private fun inside(child: View, ancestor: View): Boolean {
            var node: View? = child
            repeat(32) {
                if (node === ancestor) return true
                node = node?.parent as? View ?: return false
            }
            return false
        }
    }
    private val location = IntArray(2)
    private val stripBounds = Rect()
    private val scrollBounds = Rect()
    private val footerBounds = Rect()
    private val states = WeakHashMap<ViewGroup, State>()
    fun apply(scroll: ViewGroup) {
        val diagnosticStarted = ChatScrollDiagnostic.begin()
        try {
        if (scroll.javaClass.name != SCROLL) return
        val target = states[scroll]?.targets?.takeIf { it.valid(scroll) } ?: resolveTargets(scroll) ?: return
        val content = target.content
        val footer = target.footer
        val strip = target.strip
        val list = target.list
        val lp = content.layoutParams as? LinearLayout.LayoutParams ?: return
        val state = states[scroll]?.takeIf { it.content === content && it.list === list } ?: run {
            restore(scroll)
            State(content, list, lp.bottomMargin, list.clipToPadding).also { states[scroll] = it; it.targets = target }
        }
        state.targets = target
        ChatScrollTrace.watch(scroll, content, list, footer)
        ChatScrollTrace.snapshot("underlay.beforeApply", content)
        val stripRect = strip.screenRect(stripBounds)
        // t8.xml: blr is ABOVE bl8/ssr, not the input row. Runtime logs show
        // blr.height=0 while ssr.height=171. Reserve only footer-top..row-bottom;
        // bkc/AppPanel below ssr must never contribute to this overlap.
        val rowOverlap = inputRowOverlap(footer, strip)
        // Native footer includes an offscreen panel. Cover the short visible tail
        // below ssr as well, but never extend through an opened panel.
        val visibleFooter = intersectionRect(scroll.screenRect(scrollBounds), footer.screenRect(footerBounds))
        val tail = (visibleFooter.bottom - stripRect.bottom).coerceAtLeast(0)
        val bottomTail = if (rowOverlap > 0 && tail < strip.height) tail else 0
        val overlap = rowOverlap + bottomTail
        if (list.paddingBottom != state.appliedPadding) state.basePadding = list.paddingBottom
        val padding = state.basePadding + overlap
        if (list.paddingBottom != padding) {
            ChatScrollDiagnostic.hit("underlay.paddingWrite")
            list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, padding)
        }
        state.appliedPadding = padding
        state.appliedClip = if (overlap > 0) false else state.clip
        if (list.clipToPadding != state.appliedClip) list.clipToPadding = state.appliedClip
        // Never change LinearLayout margins: they also move the following footer.
        // Enlarge only the content AFTER native layout, leaving footer bounds/hit area intact.
        if (state.expandedHeight < 0 || content.height != state.expandedHeight ||
            content.width != state.nativeWidth || content.left != state.nativeLeft || content.top != state.nativeTop) {
            state.nativeHeight = content.height
            state.nativeWidth = content.width
            state.nativeLeft = content.left
            state.nativeTop = content.top
        }
        val height = state.nativeHeight + overlap
        ChatScrollDiagnostic.geometry(state.nativeHeight, height, overlap)
        if (content.height != height) {
            ChatScrollDiagnostic.hit("underlay.expand")
            resizeContent(state, height)
        }
        state.expandedHeight = height
        state.overlap = overlap
        ChatScrollTrace.snapshot("underlay.afterApply", content)

        } finally { ChatScrollDiagnostic.end("underlay.apply", diagnosticStarted) }
    }

    private fun resolveTargets(scroll: ViewGroup): Targets? {
        ChatScrollDiagnostic.hit("underlay.resolveTargets")
        fun named(name: String): View? {
            val id = scroll.resources.getIdentifier(name, "id", scroll.context.packageName)
            return if (id == 0) null else scroll.findViewById<View>(id)
        }
        val content = named("bki") ?: return null
        val footer = (named("k52") as? ViewGroup) ?: return null
        val strip = named("ssr") ?: return null
        val wrapper = named("bm6") ?: return null
        if (content.parent !== scroll || footer.parent !== scroll ||
            content.javaClass.name != "com.tencent.mm.pluginsdk.ui.chat.ChattingContent" ||
            footer.javaClass.name != "com.tencent.mm.pluginsdk.ui.chat.ChatFooter") return null
        // Native getListView() returns u3; in 8.0.78 the concrete instance is a View (RecyclerView).
        val method = KavaReflector.findMethodRecursive(wrapper.javaClass, "getListView") ?: return null
        val list = KavaReflector.invokeOrThrow(method, wrapper) as? ViewGroup ?: return null
        return Targets(content, footer, strip, wrapper, list).takeIf { it.valid(scroll) }
    }
    private fun resizeContent(state: State, height: Int) {
        val diagnosticStarted = ChatScrollDiagnostic.begin()
        try {
        val previousPhase = if (ChatScrollDiagnostic.isActive()) ChatScrollTrace.enter("underlayResize") else null
        try {
        val needsMeasure = state.content.measuredWidth != state.nativeWidth ||
            state.content.measuredHeight != height || state.content.isLayoutRequested
        if (needsMeasure) {
            ChatScrollDiagnostic.hit("underlay.actualMeasure")
            val measureStarted = ChatScrollDiagnostic.begin()
            try { state.content.measure(
                View.MeasureSpec.makeMeasureSpec(state.nativeWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            ) } finally { ChatScrollDiagnostic.end("underlay.measureOnly", measureStarted) }
        }
        if (needsMeasure || state.content.left != state.nativeLeft || state.content.top != state.nativeTop ||
            state.content.width != state.nativeWidth || state.content.height != height ||
            state.content.isLayoutRequested) {
            ChatScrollDiagnostic.hit("underlay.actualLayout")
            val layoutStarted = ChatScrollDiagnostic.begin()
            try { state.content.layout(state.nativeLeft, state.nativeTop,
                state.nativeLeft + state.nativeWidth, state.nativeTop + height) }
            finally { ChatScrollDiagnostic.end("underlay.layoutOnly", layoutStarted) }
        }
        } finally { if (ChatScrollDiagnostic.isActive() || previousPhase != null) ChatScrollTrace.leave(previousPhase) }
        } finally { ChatScrollDiagnostic.end("underlay.measureLayout", diagnosticStarted) }
    }

    // Called before native onLayout: native measurement/placement must never consume
    // our enlarged measured height, otherwise footer placement drifts on the next pass.
    fun beforeLayout(scroll: ViewGroup) {
        val diagnosticStarted = ChatScrollDiagnostic.begin()
        try {
        val state = states[scroll] ?: return
        ChatScrollTrace.snapshot("underlay.beforeNative", state.content)
        // A fresh window measurement may coincide with our expanded height.
        // Only restore a measurement whose width and height still belong to us.
        if (state.expandedHeight >= 0 && state.content.measuredHeight == state.expandedHeight &&
            state.content.measuredWidth == state.nativeWidth) {
            ChatScrollDiagnostic.hit("underlay.restoreNative")
            val diagnosticStarted = ChatScrollDiagnostic.begin()
            val previousPhase = if (ChatScrollDiagnostic.isActive()) ChatScrollTrace.enter("underlayResize") else null
            try {
                state.content.measure(
                    View.MeasureSpec.makeMeasureSpec(state.nativeWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(state.nativeHeight, View.MeasureSpec.EXACTLY)
                )
            } finally {
                if (ChatScrollDiagnostic.isActive() || previousPhase != null) ChatScrollTrace.leave(previousPhase)
                ChatScrollDiagnostic.end("underlay.measureLayout", diagnosticStarted)
            }
        }
        state.expandedHeight = -1

        } finally { ChatScrollDiagnostic.end("underlay.beforeLayout", diagnosticStarted) }
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

    private fun View.screenRect(out: Rect): Rect {
        getLocationOnScreen(location)
        out.set(location[0], location[1], location[0] + width, location[1] + height)
        return out
    }

    private fun intersectionRect(a: Rect, b: Rect): Rect = Rect(
        maxOf(a.left, b.left), maxOf(a.top, b.top),
        minOf(a.right, b.right).coerceAtLeast(maxOf(a.left, b.left)),
        minOf(a.bottom, b.bottom).coerceAtLeast(maxOf(a.top, b.top))
    )

    fun restore(scroll: ViewGroup) {
        val state = states.remove(scroll) ?: return
        if (state.expandedHeight >= 0 && state.content.height == state.expandedHeight) {
            ChatScrollDiagnostic.hit("underlay.restoreNative")
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
