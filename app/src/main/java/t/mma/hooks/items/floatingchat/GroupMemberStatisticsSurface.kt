package t.mma.hooks.items.floatingchat
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.TextUtils
import android.view.*
import android.widget.*
import t.mma.hooks.api.core.WeChatApis
import t.mma.utils.HLog
import t.mma.utils.KavaReflector
import java.util.WeakHashMap

/** Owns the statistics label and height reservation, never native title/menu presentation. */
internal class GroupMemberStatisticsSurface {
    private class State(val statistics: TextView, val baseHeight: Int, val originalHeight: Int, val titleRoot: View, val titleHeight: Int) {
        var iconsLoaded = false
        var maleIcon: Drawable? = null
        var femaleIcon: Drawable? = null
        var statisticsWidth = -1
        var statisticsHeight = 0
    }
    private val states = WeakHashMap<ViewGroup, State>()
    fun apply(header: ViewGroup, title: TextView?, text: String?) {
        if (text == null || title == null) { restore(header); return }
        val state = states.getOrPut(header) {
            val label = TextView(header.context).apply {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                setSingleLine(true); ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
                setPadding(dp(header, 10), dp(header, 4), dp(header, 10), dp(header, 4))
                background = GradientDrawable().apply { cornerRadius = dp(header, 24).toFloat() }
                visibility = View.INVISIBLE
            }
            var titleRoot: View = title
            while (titleRoot.parent is View && titleRoot.parent !== header) titleRoot = titleRoot.parent as View
            val titleHeight = titleRoot.layoutParams.height
            val titleParams = titleRoot.layoutParams
            titleParams.height = header.height
            titleRoot.layoutParams = titleParams
            val originalHeight = header.layoutParams.height
            val baseHeight = header.height
            FloatingChatCardVisuals.ownPresentation(label)
            header.addView(label, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            State(label, baseHeight, originalHeight, titleRoot, titleHeight)
        }
        val dark = header.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        applyStatistics(header, state, text, if (dark) Color.WHITE else 0xFF191D21.toInt(), title)
        if (header.getChildAt(header.childCount - 1) !== state.statistics) state.statistics.bringToFront()
    }
    fun restore(header: ViewGroup) {
        val state = states.remove(header) ?: return
        FloatingChatCardVisuals.releasePresentation(state.statistics)
        state.statistics.text = null
        val titleParams = state.titleRoot.layoutParams
        titleParams.height = state.titleHeight
        state.titleRoot.layoutParams = titleParams
        header.removeView(state.statistics)
        val lp = header.layoutParams
        if (lp.height != state.originalHeight) { lp.height = state.originalHeight; header.layoutParams = lp }
    }
    fun label(header: ViewGroup): View? = states[header]?.statistics
    fun clear() { states.keys.toList().forEach(::restore) }
    private fun resourceId(view: View, name: String, type: String) =
        view.resources.getIdentifier(name, type, view.context.packageName)
    private fun dp(view: View, value: Int) = (value * view.resources.displayMetrics.density + 0.5f).toInt()
    private fun applyStatistics(header: ViewGroup, state: State, text: String?, ink: Int, title: TextView) {
        val label = state.statistics
        val visible = text != null
        if (visible && title.height <= 0) {
            if (label.visibility != View.INVISIBLE) label.visibility = View.INVISIBLE
            return
        }
        if (label.visibility != if (visible) View.VISIBLE else View.GONE) label.visibility = if (visible) View.VISIBLE else View.GONE
        var height = state.baseHeight
        if (visible) {
            val textChanged = label.text.toString() != text
            if (textChanged) label.text = statisticsText(header, state, text!!)
            if (label.currentTextColor != ink) label.setTextColor(ink)
            val color = if (ink == Color.WHITE) 0xB3242424.toInt() else 0xB3F7F7F7.toInt()
            val background = label.background as GradientDrawable
            if (background.color?.defaultColor != color) background.setColor(color)
            // The title occupies the whole middle capsule. Use its real measured bottom.
            var titleBottom = title.bottom
            var parent = title.parent as? View
            while (parent != null && parent !== header) {
                titleBottom += parent.top
                parent = parent.parent as? View
            }
            if (parent !== header) { label.visibility = View.INVISIBLE; return }
            val top = titleBottom + dp(header, 3)
            val availableWidth = (header.width - dp(header, 8)).coerceAtLeast(1)
            if (textChanged || state.statisticsWidth != availableWidth || label.isLayoutRequested) {
                label.measure(View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                state.statisticsWidth = availableWidth
                state.statisticsHeight = label.measuredHeight
            }
            val lp = label.layoutParams as FrameLayout.LayoutParams
            if (lp.topMargin != top) { lp.topMargin = top; label.layoutParams = lp }
            height = maxOf(height, top + state.statisticsHeight)
        }
        val lp = header.layoutParams
        val desired = if (visible) height else state.originalHeight
        if (lp.height != desired) { lp.height = desired; header.layoutParams = lp }
    }

    private fun statisticsText(header: ViewGroup, state: State, text: String): CharSequence {
        if (!text.startsWith("男 ")) return text
        if (!state.iconsLoaded) {
            state.iconsLoaded = true
            // 8.0.78 profile UI uses ke5.a.i(Context, rawId), without a color filter.
            try {
                val loader = KavaReflector.loadClass("ke5.a", header.context.classLoader)
                val method = KavaReflector.findDeclaredMethod(loader, "i", Context::class.java, Int::class.javaPrimitiveType!!)
                    ?: throw NoSuchMethodException("ke5.a.i(Context,int):Drawable")
                fun load(name: String): Drawable {
                    val id = resourceId(header, name, "raw")
                    check(id != 0) { "Missing native resource $name" }
                    return (KavaReflector.invokeOrThrow(method, null, header.context, id) as? Drawable)
                        ?.mutate() ?: error("Native icon $name unavailable")
                }
                state.maleIcon = load("ic_sex_male")
                state.femaleIcon = load("ic_sex_female")
            } catch (error: Throwable) {
                HLog.e("[Hchat:群员性别好友统计] 微信8.0.78 ke5.a.i 原生性别图标加载失败", error)
            }
        }
        val result = SpannableString(text)
        val size = dp(header, 14)
        fun insert(label: String, drawable: Drawable?) {
            val index = text.indexOf(label)
            if (index < 0 || drawable == null) return
            drawable.setBounds(0, 0, size, size)
            // Replace the existing separator space, retaining readable plain text.
            result.setSpan(ImageSpan(drawable, ImageSpan.ALIGN_CENTER), index + 1, index + 2,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        insert("男 ", state.maleIcon)
        insert("女 ", state.femaleIcon)
        return result
    }

}
