package t.mma.hooks.items.floatingchat

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import java.util.WeakHashMap
import t.mma.utils.HLog

/** Own only native input backgrounds, never icon drawables or the detached emoji panel. */
internal object FloatingFooterInputSurfaces {
    private class Surface(val view: View) {
        val background: Drawable? = view.background
        val foreground: Drawable? = view.foreground
        val tint: ColorStateList? = view.backgroundTintList
        val tintMode: PorterDuff.Mode? = view.backgroundTintMode
        val marker = ColorDrawable(Color.TRANSPARENT)
        fun clear() {
            val padding = intArrayOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
            val minWidth = view.minimumWidth
            val minHeight = view.minimumHeight
            if (view.background !== marker) view.background = marker
            if (view.backgroundTintList != null) view.backgroundTintList = null
            if (view.foreground != null) view.foreground = null
            if (view.minimumWidth != minWidth) view.minimumWidth = minWidth
            if (view.minimumHeight != minHeight) view.minimumHeight = minHeight
            if (view.paddingLeft != padding[0] || view.paddingTop != padding[1] || view.paddingRight != padding[2] || view.paddingBottom != padding[3]) {
                view.setPadding(padding[0], padding[1], padding[2], padding[3])
            }
        }
        fun restore() {
            FloatingChatCardVisuals.releasePresentation(view)
            val padding = intArrayOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
            val minWidth = view.minimumWidth
            val minHeight = view.minimumHeight
            view.background = background
            view.setPadding(padding[0], padding[1], padding[2], padding[3])
            view.minimumWidth = minWidth
            view.minimumHeight = minHeight
            view.foreground = foreground
            view.backgroundTintList = tint
            view.backgroundTintMode = tintMode
        }
    }
    private val surfaces = WeakHashMap<View, MutableList<Surface>>()
    private var reports = 0
    fun apply(footer: View) {
        val group = footer as? ViewGroup ?: return
        val tracked = surfaces.getOrPut(footer) { ArrayList() }
        val queue = ArrayDeque<View>()
        for (i in 0 until group.childCount) queue.add(group.getChildAt(i))
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 120) {
            val view = queue.removeFirst()
            if (view.javaClass.name == "com.tencent.mm.pluginsdk.ui.chat.ChatFooterBottom") continue
            val input = view is EditText || view is Button
            val container = view is ViewGroup
            if (input || container) {
                val surface = tracked.firstOrNull { it.view === view } ?: Surface(view).also {
                    tracked.add(it)
                    FloatingChatCardVisuals.ownPresentation(view)
                    report(view, "capture")
                }
                if (view.background !== surface.marker || view.backgroundTintList != null || view.foreground != null) {
                    report(view, "clear")
                    surface.clear()
                }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) queue.add(view.getChildAt(i))
        }
    }
    private fun report(view: View, phase: String) {
        if (reports >= 24) return
        reports++
        val id = runCatching { view.resources.getResourceEntryName(view.id) }.getOrDefault("unknown")
        HLog.i("[Hchat:悬浮输入框] FOOTER_INPUT_SURFACE phase=$phase class=${view.javaClass.name} id=$id background=${view.background?.javaClass?.name} tint=${view.backgroundTintList != null} foreground=${view.foreground?.javaClass?.name} visibility=${view.visibility}")
    }
    fun restore(footer: View) { surfaces.remove(footer)?.forEach { it.restore() } }
}
