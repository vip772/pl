package t.mma.hooks.items.chattoolbar

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import java.io.Closeable
import kotlin.math.roundToInt

/** Footer-owned positioning; keeps native parents, LayoutParams and click listeners intact. */
internal class ChatToolbarNewMessagePosition(
    private val footer: ViewGroup,
    private val toolbar: View,
    private val onError: (Throwable) -> Unit
) : Closeable {
    private class Chip(val id: Int) {
        var view: View? = null
        var baseline = 0f
        var applied = 0f
        var ownsTranslation = false
    }
    private var observer: ViewTreeObserver? = null
    private var content: ViewGroup? = null
    private var input: View? = null
    private var chips = emptyArray<Chip>()
    private var childCount = -1
    private var failed = false
    private val footerPosition = IntArray(2)
    private val anchorPosition = IntArray(2)
    private val contentPosition = IntArray(2)
    private val chipPosition = IntArray(2)
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        try { update() } catch (error: Throwable) {
            close()
            if (!failed) { failed = true; onError(error) }
        }
        true
    }

    fun attach() {
        if (observer?.isAlive == true) return
        failed = false
        try { attachVerified() } catch (error: Throwable) {
            close()
            if (!failed) { failed = true; onError(error) }
        }
    }

    private fun attachVerified() {
        val host = footer.context
        val info = host.packageManager.getPackageInfo(host.packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        if (info.versionName != "8.0.78" || versionCode != 3180L) return
        var root: View = footer
        var depth = 0
        while (root.javaClass.name != "com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout") {
            if (++depth > 24) return
            root = root.parent as? View ?: return
        }
        fun named(name: String): Int = root.resources.getIdentifier(name, "id", host.packageName)
        val contentId = named("bki")
        val inputId = named("ssr")
        val historyId = named("bm4")
        val quoteId = named("bm2")
        if (contentId == 0 || inputId == 0 || historyId == 0 || quoteId == 0) return
        val parent = root.findViewById<View>(contentId) as? ViewGroup ?: return
        if (parent.javaClass.name != "com.tencent.mm.pluginsdk.ui.chat.ChattingContent" ||
            parent.parent !== footer.parent) return
        input = footer.findViewById<View>(inputId) ?: return
        content = parent
        // hh.a()/S0()/R0(): bm4 is history/new-message; sk.a(): bm2 is quote-message.
        chips = arrayOf(Chip(historyId), Chip(quoteId))
        childCount = -1
        observer = footer.viewTreeObserver.also { it.addOnPreDrawListener(preDraw) }
    }

    private fun update() {
        val parent = content ?: return
        if (parent.parent !== footer.parent || !footer.isShown || footer.height <= 0) {
            chips.forEach(::restore)
            return
        }
        // XML children are stable. Resolve once, or after an actual child replacement.
        if (parent.childCount != childCount || chips.any { it.view != null && it.view?.parent !== parent }) {
            chips.forEach { state ->
                val found = parent.findViewById<View>(state.id)?.takeIf { it.parent === parent }
                if (found !== state.view) { restore(state); state.view = found }
            }
            childCount = parent.childCount
        }
        val anchor = if (toolbar.isShown && toolbar.height > 0) toolbar else input
        if (anchor == null || !anchor.isShown || anchor.height <= 0) {
            chips.forEach(::restore)
            return
        }
        if (chips.none { state ->
                state.view?.let { target ->
                    val params = target.layoutParams as? FrameLayout.LayoutParams
                    target.isShown && target.height > 0 && params != null &&
                        params.gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.BOTTOM
                } == true
            }) {
            chips.forEach(::restore)
            return
        }
        footer.getLocationOnScreen(footerPosition)
        anchor.getLocationOnScreen(anchorPosition)
        parent.getLocationOnScreen(contentPosition)
        val gap = (8 * footer.resources.displayMetrics.density).roundToInt()
        // The enlarged immersive content overlaps its later footer sibling. Stay OUTSIDE
        // that sibling's hit rectangle and INSIDE the original native parent hit bounds.
        val safeBottom = minOf(anchorPosition[1], footerPosition[1], contentPosition[1] + parent.height) - gap
        chips.forEach { state ->
            val target = state.view ?: return@forEach
            val params = target.layoutParams as? FrameLayout.LayoutParams
            if (params == null || params.gravity and Gravity.VERTICAL_GRAVITY_MASK != Gravity.BOTTOM ||
                !target.isShown || target.height <= 0 || target.width <= 0 ||
                safeBottom - target.height < contentPosition[1]) {
                restore(state)
                return@forEach
            }
            if (!state.ownsTranslation || target.translationY != state.applied) state.baseline = target.translationY
            target.getLocationOnScreen(chipPosition)
            val nativeBottom = chipPosition[1] + target.height -
                (target.translationY - state.baseline).roundToInt()
            val next = state.baseline - (nativeBottom - safeBottom).coerceAtLeast(0)
            if (target.translationY != next) target.translationY = next
            state.applied = next
            state.ownsTranslation = true
        }
    }

    private fun restore(state: Chip) {
        state.view?.let { target ->
            if (state.ownsTranslation && target.translationY == state.applied && target.translationY != state.baseline) {
                target.translationY = state.baseline
            }
        }
        state.ownsTranslation = false
    }

    override fun close() {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw)
        observer = null
        chips.forEach(::restore)
        chips = emptyArray()
        content = null
        input = null
        childCount = -1
    }
}
