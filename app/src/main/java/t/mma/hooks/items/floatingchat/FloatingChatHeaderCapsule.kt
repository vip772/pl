package t.mma.hooks.items.floatingchat

import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.*
import android.widget.*
import t.mma.hooks.api.core.WeChatApis
import t.mma.utils.HLog
import java.util.WeakHashMap

/** Presentation only: native children keep parents, IDs and click listeners. */
internal class FloatingChatHeaderCapsule {
    private data class ChildState(val alpha: Float, val accessibility: Int)
    private class State(val row: LinearLayout, val title: TextView,
                        val back: Icon, val more: Icon, val center: LinearLayout,
                        val background: Drawable?, val baseHeight: Int, val originalHeight: Int) {
        val hidden = WeakHashMap<View, ChildState>()
        var backTarget: View? = null
        var moreTarget: View? = null
        var titleTarget: View? = null
        var strokeStyle: Pair<Int, Int>? = null
    }
    private val states = WeakHashMap<ViewGroup, State>()
    private val resourceIds = WeakHashMap<android.content.res.Resources, MutableMap<Triple<String, String, String>, Int>>()
    private fun resourceId(view: View, name: String, type: String): Int {
        val key = Triple(view.context.packageName, type, name)
        return resourceIds.getOrPut(view.resources) { HashMap() }.getOrPut(key) {
            view.resources.getIdentifier(name, type, key.first)
        }
    }
    // APK versionName is stable for this host process. Avoid rebuilding the full
    // runtime/patch fingerprint on every layout. Retry unavailable startup values.
    private var hostVersionName: String? = null
    private fun hostVersionName(): String? {
        hostVersionName?.let { return it }
        return WeChatApis.version()?.versionName()?.takeIf { it.isNotBlank() }?.also {
            hostVersionName = it
        }
    }
    @Volatile private var closed = false

    // Capability is independent of transient native title/menu readiness.
    fun supportsHost(): Boolean = hostVersionName() == "8.0.78"

    // An unresolved version must wait, never install a legacy surface first.
    fun usesLegacyPresentation(): Boolean = hostVersionName()?.let { it != "8.0.78" } == true

    fun apply(header: ViewGroup, transparency: Int = -1, excluded: View? = null): Boolean {
        if (closed) return false
        if (header.width <= 0 || header.height <= 0) return fail(header)
        if (!supportsHost()) return fail(header)
        fun named(name: String): View? {
            val id = resourceId(header, name, "id")
            return if (id == 0) null else header.findViewById<View>(id)
        }
        val nativeBack = named("actionbar_up_indicator") ?: return fail(header)
        val nativeTitle = named("obn") as? TextView ?: return fail(header)
        val menu = named("coy") as? ViewGroup ?: return fail(header)
        val nativeMore = findVerifiedMore(menu, header) ?: return fail(header)
        if (!nativeBack.isClickable || nativeBack.visibility != View.VISIBLE) return fail(header)
        if (nativeTitle.text.isNullOrBlank()) return fail(header)
        val state = states[header] ?: create(header).also { states[header] = it }
        if (state.backTarget !== nativeBack) {
            state.backTarget = nativeBack
            state.back.setOnClickListener { nativeBack.performClick() }
        }
        if (state.moreTarget !== nativeMore) {
            state.moreTarget = nativeMore
            state.more.setOnClickListener { nativeMore.performClick() }
        }
        val titleAction = clickableAncestor(nativeTitle, header)
        if (state.titleTarget !== titleAction) {
            state.titleTarget = titleAction
            state.center.setOnClickListener { titleAction?.performClick() }
        }
        state.center.isClickable = titleAction != null
        val dark = header.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val ink = if (dark) Color.WHITE else 0xFF191D21.toInt()
        state.back.ink = ink; state.more.ink = ink; if (state.title.currentTextColor != ink) state.title.setTextColor(ink)
        val strokeStyle = dp(header, 1) to FloatingChatOpacity.color(if (dark) 0x30FFFFFF else 0x88FFFFFF.toInt(), transparency)
        for (surface in listOf(state.back, state.center, state.more)) {
            val bg = surface.background as GradientDrawable
            val color = FloatingChatOpacity.color(if (dark) 0xE6242424.toInt() else 0xE6F7F7F7.toInt(), transparency)
            val stroke = strokeStyle.second
            if (bg.color?.defaultColor != color) bg.setColor(color)
            if (bg.alpha != 255) bg.alpha = 255
            if (state.strokeStyle != strokeStyle) bg.setStroke(strokeStyle.first, stroke)
        }
        state.strokeStyle = strokeStyle
        val rawTitle = nativeTitle.text.toString()
        // ba.xml stores the trailing count/status text separately in obr.
        val suffix = (named("obr") as? TextView)?.takeIf { it.visibility == View.VISIBLE }?.text?.toString().orEmpty()
        val title = rawTitle + suffix
        if (state.title.text.toString() != title) state.title.text = title
        for (i in 0 until header.childCount) {
            val child = header.getChildAt(i)
            if (child === state.row || child === excluded) continue
            state.hidden.getOrPut(child) { ChildState(child.alpha, child.importantForAccessibility) }
            if (child.alpha != 0f) child.alpha = 0f
            if (child.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        if (header.background != null) header.background = null
        if (header.getChildAt(header.childCount - 1) !== state.row) state.row.bringToFront()

        return true
    }
    private fun fail(header: ViewGroup): Boolean {
        // Native menu/title rebuilding is transient. Keep an installed capsule instead
        // of replacing it with the fallback card; lifecycle/disable owns restoration.
        return states[header]?.let { it.row.parent === header } == true
    }
    private fun findVerifiedMore(menu: ViewGroup, header: ViewGroup): View? {
        val candidates = mutableListOf<View>()
        val fqId = resourceId(header, "fq", "id")
        collectMenuActions(menu, fqId, candidates)
        val settingText = runCatching { header.context.getString(resourceId(header, "cm", "string")) }.getOrNull()
        val settingRaw = resourceId(header, "actionbar_setting_icon", "raw")
        val settingIcon by lazy(LazyThreadSafetyMode.NONE) {
            runCatching { header.context.resources.getDrawable(settingRaw, header.context.theme) }.getOrNull()
        }
        // MMActivityController writes fb.b (the menu icon resource ID) to id/nwr.
        // Drawable constantState is unreliable after WeChat wraps/tints the icon.
        val iconTagId = resourceId(header, "nwr", "id")
        // fg's chatroom branch registers dark_more, not actionbar_setting_icon.
        val moreRaw = resourceId(header, "actionbar_icon_dark_more", "raw")
        val verified = candidates.filter { view ->
            val title = (view.findViewById<TextView>(android.R.id.text1)?.text ?: view.contentDescription)?.toString()
            val icon = (view as? ImageView)?.drawable
                ?: view.findViewById<ImageView>(android.R.id.icon)?.drawable
            val nativeSettingTag = iconTagId != 0 && fqId != 0 && view.id == fqId &&
                view.javaClass.name == "com.tencent.mm.ui.widget.imageview.WeImageView" &&
                ((settingRaw != 0 && view.getTag(iconTagId) == settingRaw) ||
                    (moreRaw != 0 && view.getTag(iconTagId) == moreRaw))
            nativeSettingTag || (settingText != null && title == settingText) ||
                (settingRaw != 0 && icon != null && settingIcon != null && icon.constantState == settingIcon?.constantState)
        }
        if (verified.size == 1) return verified.single()

        return null
    }
    // Only the first three failed resolutions reach here; no per-frame tree logging.

    private fun create(header: ViewGroup): State {
        val ctx = header.context
        val size = minOf(dp(header, 48), header.height - dp(header, 4)).coerceAtLeast(dp(header, 32))
        fun surface() = GradientDrawable().apply { cornerRadius = size / 2f }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            // Consume gaps so invisible native children cannot receive accidental taps.
            isClickable = true
        }
        val back = Icon(ctx, false).apply { background = surface(); contentDescription = "返回" }
        val more = Icon(ctx, true).apply { background = surface(); contentDescription = "更多" }
        val center = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = surface(); setPadding(dp(header, 8), 0, dp(header, 8), 0)
        }
        val title = TextView(ctx).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setSingleLine(true); ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER
        }
        center.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        row.addView(back, LinearLayout.LayoutParams(size, size))
        row.addView(center, LinearLayout.LayoutParams(0, size, 1f).apply { leftMargin = dp(header, 8); rightMargin = dp(header, 8) })
        row.addView(more, LinearLayout.LayoutParams(size, size))
        val original = header.background
        // Claim before any background setter can be intercepted by immersive mode.
        FloatingChatCardVisuals.ownPresentation(header)
        listOf(row, back, more, center).forEach(FloatingChatCardVisuals::ownPresentation)
        val baseHeight = header.height
        val originalHeight = header.layoutParams.height
        header.addView(row, FrameLayout.LayoutParams(-1, baseHeight, Gravity.TOP))
        return State(row, title, back, more, center, original, baseHeight, originalHeight)
    }
    fun titleAnchor(header: ViewGroup): TextView? = states[header]?.title

    private fun releaseSafely(action: () -> Unit) {
        try { action() } catch (error: Throwable) {
            try { HLog.e("[Hchat:悬浮标题栏] 释放胶囊资源失败", error) } catch (_: Throwable) { }
        }
    }
    fun restore(header: ViewGroup) {
        val state = states.remove(header) ?: return
        listOf(header, state.row, state.back, state.more, state.center).forEach { view ->
            releaseSafely { FloatingChatCardVisuals.releasePresentation(view) }
        }
        releaseSafely { header.removeView(state.row) }
        releaseSafely {
            val lp = header.layoutParams
            if (lp.height != state.originalHeight) { lp.height = state.originalHeight; header.layoutParams = lp }
        }
        releaseSafely { header.background = state.background }
        state.hidden.toMap().forEach { (view, old) ->
            releaseSafely { view.alpha = old.alpha }
            releaseSafely { view.importantForAccessibility = old.accessibility }
        }
        state.hidden.clear()
        state.backTarget = null; state.moreTarget = null; state.titleTarget = null
        releaseSafely { state.back.setOnClickListener(null) }
        releaseSafely { state.more.setOnClickListener(null) }
        releaseSafely { state.center.setOnClickListener(null) }
    }
    fun destroy() {
        if (closed) return
        closed = true
        states.keys.toList().forEach { header -> releaseSafely { restore(header) } }
        states.clear()
        hostVersionName = null
        resourceIds.clear()
    }
    private fun collectMenuActions(view: View, fq: Int, output: MutableList<View>) {
        if (view.visibility != View.VISIBLE || !view.isEnabled) return
        // 8.0.78 has two native option-menu renderers. Runtime uses fq inside coy;
        // MMActivityController stores it in U and binds the option click/description.
        val isNativeIcon = fq != 0 && view.id == fq &&
            view.javaClass.name == "com.tencent.mm.ui.widget.imageview.WeImageView"
        val isActionMenu = view.javaClass.name == "com.tencent.mm.ui.widget.WXActionMenuView"
        if ((isNativeIcon || isActionMenu) && view.isClickable && view.hasOnClickListeners()) {
            output += view
            return
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) collectMenuActions(view.getChildAt(i), fq, output)
    }
    private fun clickableAncestor(view: View, boundary: View): View? {
        var current: View? = view
        while (current != null && current !== boundary) {
            if (current.isClickable) return current
            current = current.parent as? View
        }
        return null
    }
    private fun dp(view: View, value: Int) = (value * view.resources.displayMetrics.density + 0.5f).toInt()
    private class Icon(ctx: Context, private val more: Boolean) : View(ctx) {
        var ink = Color.BLACK
            set(value) { if (field != value) { field = value; invalidate() } }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas); paint.color = ink; paint.strokeCap = Paint.Cap.ROUND
            val d = resources.displayMetrics.density; val cx = width / 2f; val cy = height / 2f
            if (more) {
                paint.style = Paint.Style.FILL
                for (i in -1..1) canvas.drawCircle(cx, cy + i * 6 * d, 1.8f * d, paint)
            } else {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.3f * d
                canvas.drawLine(cx + 7*d, cy, cx - 7*d, cy, paint)
                canvas.drawLine(cx - 7*d, cy, cx, cy - 7*d, paint)
                canvas.drawLine(cx - 7*d, cy, cx, cy + 7*d, paint)
            }
        }
    }
}
