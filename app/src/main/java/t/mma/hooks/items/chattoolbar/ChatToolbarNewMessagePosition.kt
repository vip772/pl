package t.mma.hooks.items.chattoolbar

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import java.io.Closeable
import kotlin.math.roundToInt

/** Owned by the attached toolbar, independent of the floating header and native margin updates. */
internal class ChatToolbarNewMessagePosition(
    private val footer: ViewGroup,
    private val toolbar: View,
    private val onError: (Throwable) -> Unit
) : Closeable {
    private var observer: ViewTreeObserver? = null
    private var content: ViewGroup? = null
    private var chipId = 0
    private var chip: View? = null
    private var baseline = 0f
    private var applied = 0f
    private var ownsTranslation = false
    private var failed = false
    private val toolbarPosition = IntArray(2)
    private val chipPosition = IntArray(2)
    private val preDraw = ViewTreeObserver.OnPreDrawListener {
        try { update() } catch (error: Throwable) {
            restore()
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
        content = null
        chipId = 0
        // Resource aliases and container structure were verified only for this host build.
        val host = footer.context
        val info = host.packageManager.getPackageInfo(host.packageName, 0)
        @Suppress("DEPRECATION")
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        if (info.versionName != "8.0.78" || versionCode != 3180L) return
        var ancestor: View? = footer
        repeat(24) {
            val current = ancestor ?: return@repeat
            if (current.javaClass.name == "com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout") {
                val resources = current.resources
                val contentId = resources.getIdentifier("bki", "id", current.context.packageName)
                chipId = resources.getIdentifier("bm2", "id", current.context.packageName)
                content = if (contentId != 0) current.findViewById<ViewGroup>(contentId)
                    ?.takeIf { it.javaClass.name == "com.tencent.mm.pluginsdk.ui.chat.ChattingContent" } else null
                ancestor = null
            } else ancestor = current.parent as? View
        }
        if (content == null || chipId == 0) return
        observer = toolbar.viewTreeObserver.also { it.addOnPreDrawListener(preDraw) }
    }

    private fun update() {
        val parent = content ?: return
        val found = chip?.takeIf { it.parent === parent }
            ?: if (chipId != 0) parent.findViewById<View>(chipId) else null
        if (found !== chip) {
            restore()
            chip = found
        }
        val target = found ?: return
        val params = target.layoutParams as? FrameLayout.LayoutParams
        val bottom = params != null && params.gravity and Gravity.VERTICAL_GRAVITY_MASK == Gravity.BOTTOM
        // bm2 is directly inside ChattingContent; never reposition a similarly named footer View.
        if (target.parent !== parent || !bottom || !target.isShown || !toolbar.isShown ||
            toolbar.height <= 0 || target.height <= 0) {
            restore()
            return
        }
        // Translation does not request layout and therefore follows native margin rewrites every frame.
        if (!ownsTranslation || target.translationY != applied) baseline = target.translationY
        toolbar.getLocationOnScreen(toolbarPosition)
        target.getLocationOnScreen(chipPosition)
        val nativeBottom = chipPosition[1] + target.height - (target.translationY - baseline).roundToInt()
        val horizontal = chipPosition[0] < toolbarPosition[0] + toolbar.width &&
            chipPosition[0] + target.width > toolbarPosition[0]
        val gap = (8 * toolbar.resources.displayMetrics.density).roundToInt()
        // A chip BELOW the toolbar must also move above it; rectangle intersection alone misses it.
        val correction = if (horizontal) (nativeBottom - toolbarPosition[1] + gap).coerceAtLeast(0) else 0
        val next = baseline - correction
        if (target.translationY != next) target.translationY = next
        applied = next
        ownsTranslation = true
    }

    private fun restore() {
        chip?.let { target ->
            if (ownsTranslation && target.translationY == applied && target.translationY != baseline) {
                target.translationY = baseline
            }
        }
        ownsTranslation = false
    }

    override fun close() {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDraw)
        observer = null
        restore()
        chip = null
        content = null
        chipId = 0
    }
}