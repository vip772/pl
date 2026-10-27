package t.mma.hooks.items.floatingchat

import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.*
import android.widget.*
import t.mma.hooks.api.core.WeChatApis
import t.mma.utils.HLog
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.WeakHashMap
import java.util.concurrent.Executors

/** Presentation only: native children keep parents, IDs and click listeners. */
internal class FloatingChatHeaderCapsule {
    private data class ChildState(val alpha: Float, val accessibility: Int)
    private class State(val row: LinearLayout, val avatar: ImageView, val title: TextView,
                        val back: Icon, val more: Icon, val center: LinearLayout,
                        val background: Drawable?) {
        val hidden = WeakHashMap<View, ChildState>()
        var backTarget: View? = null
        var moreTarget: View? = null
        var titleTarget: View? = null
        var talker = ""
        var generation = 0
        var nextAttempt = 0L
        var loading = false
        var avatarAttempts = 0
        var strokeStyle: Pair<Int, Int>? = null
        var focusListener: ViewTreeObserver.OnWindowFocusChangeListener? = null
    }
    private val states = WeakHashMap<ViewGroup, State>()
    private val resourceIds = WeakHashMap<android.content.res.Resources, MutableMap<Triple<String, String, String>, Int>>()
    private fun resourceId(view: View, name: String, type: String): Int {
        val key = Triple(view.context.packageName, type, name)
        return resourceIds.getOrPut(view.resources) { HashMap() }.getOrPut(key) {
            view.resources.getIdentifier(name, type, key.first)
        }
    }
    private val main = Handler(Looper.getMainLooper())
    private val worker by lazy { Executors.newSingleThreadExecutor() }
    private var workerStarted = false
    private val cache = object : android.util.LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
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

    fun apply(header: ViewGroup, transparency: Int = -1): Boolean {
        if (closed) return false
        if (header.width <= 0 || header.height <= 0) return fail(header)
        val version = hostVersionName()
        if (version != "8.0.78") return fail(header)
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
        val talker = WeChatApis.chatPage()?.currentTalker().orEmpty()
        if (talker != state.talker) {
            state.talker = talker; state.generation++; state.nextAttempt = 0L; state.loading = false; state.avatarAttempts = 0
            state.avatar.setImageDrawable(null)
        }
        val rawTitle = nativeTitle.text.toString()
        // ba.xml stores the trailing count/status text separately in obr.
        val suffix = (named("obr") as? TextView)?.takeIf { it.visibility == View.VISIBLE }?.text?.toString().orEmpty()
        val title = rawTitle + suffix
        if (state.title.text.toString() != title) state.title.text = title
        if (state.avatar.contentDescription != title) state.avatar.contentDescription = title
        if (talker.isNotBlank()) loadAvatar(header, state, talker)
        for (i in 0 until header.childCount) {
            val child = header.getChildAt(i)
            if (child === state.row) continue
            state.hidden.getOrPut(child) { ChildState(child.alpha, child.importantForAccessibility) }
            if (child.alpha != 0f) child.alpha = 0f
            if (child.importantForAccessibility != View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        if (header.background != null) header.background = null
        if (header.getChildAt(header.childCount - 1) !== state.row) state.row.bringToFront()

        return true
    }
    private fun fail(header: ViewGroup): Boolean {

        restore(header); return false
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
        val center = CenteredTitleContent(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = surface(); setPadding(dp(header, 8), 0, dp(header, 8), 0)
        }
        val avatar = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x44808080) }
            clipToOutline = true
        }
        val title = TextView(ctx).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setSingleLine(true); ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER_VERTICAL
        }
        center.addView(avatar, LinearLayout.LayoutParams(size - dp(header, 8), size - dp(header, 8)))
        center.addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, -1).apply { leftMargin = dp(header, 8) })
        row.addView(back, LinearLayout.LayoutParams(size, size))
        row.addView(center, LinearLayout.LayoutParams(0, size, 1f).apply { leftMargin = dp(header, 8); rightMargin = dp(header, 8) })
        row.addView(more, LinearLayout.LayoutParams(size, size))
        val original = header.background
        // Claim before any background setter can be intercepted by immersive mode.
        FloatingChatCardVisuals.ownPresentation(header)
        listOf(row, back, more, center, avatar).forEach(FloatingChatCardVisuals::ownPresentation)
        header.addView(row, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER_VERTICAL))
        val state = State(row, avatar, title, back, more, center, original)
        val weakHeader = WeakReference(header)
        val weakState = WeakReference(state)
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            val view = weakHeader.get(); val target = weakState.get()
            if (focused && !closed && view != null && target != null && states[view] === target &&
                !target.loading && target.avatar.drawable == null && target.talker.isNotBlank()) {
                target.avatarAttempts = 0; target.nextAttempt = 0L
                loadAvatar(view, target, target.talker)
            }
        }
        state.focusListener = focus
        header.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        return state
    }
    private fun loadAvatar(header: ViewGroup, state: State, talker: String) {
        cache.get(talker)?.let {
            if (!it.isRecycled) {
                if ((state.avatar.drawable as? BitmapDrawable)?.bitmap !== it) state.avatar.setImageBitmap(it)
                return
            }
        }
        val now = SystemClock.elapsedRealtime()
        if (state.loading || now < state.nextAttempt) return
        if (state.avatarAttempts >= 3) return
        state.avatarAttempts++
        state.loading = true; state.nextAttempt = now + 2_000
        val token = state.generation
        val weakHeader = WeakReference(header); val weakState = WeakReference(state)
        workerStarted = true
        worker.execute {
            var bitmap: Bitmap? = null
            try {
                val contacts = WeChatApis.contact().contacts()
                val urls = listOf(contacts?.getAvatarUrl(talker, false), contacts?.getAvatarUrl(talker, true))
                    .filterNotNull().filter { it.isNotBlank() }.distinct()

                for (url in urls) {
                    if (closed || Thread.currentThread().isInterrupted) break
                    try { bitmap = download(url) } catch (e: Exception) {
                        if (!closed) HLog.e("[Hchat:悬浮标题栏] 头像源加载失败", e)
                    }
                    if (bitmap != null) break
                }
            } catch (e: Throwable) { if (!closed) HLog.e("[Hchat:悬浮标题栏] 头像加载失败", e) }
            val result = bitmap
            if (!closed) main.post {
                val view = weakHeader.get(); val target = weakState.get()
                if (closed || view == null || target == null || states[view] !== target ||
                    target.generation != token || target.talker != talker) return@post
                target.loading = false
                if (result != null) {
                    cache.put(talker, result); target.avatar.setImageBitmap(result)
                } else {
                    // Retry independently of layout events, with the same generation guard.

                    main.postDelayed({
                        val retryView = weakHeader.get(); val retryState = weakState.get()
                        if (!closed && retryView != null && retryState != null &&
                            retryView.isAttachedToWindow && states[retryView] === retryState &&
                            retryState.generation == token && retryState.talker == talker) {
                            loadAvatar(retryView, retryState, talker)
                        }
                    }, 2_000)
                }
            }
        }
    }
    private fun download(address: String): Bitmap? {
        val url = URL(address)
        if (url.protocol !in listOf("http", "https")) return null
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 4000; conn.readTimeout = 4000
            if (conn.responseCode !in 200..299) {

                return null
            }
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (!closed && !Thread.currentThread().isInterrupted) {
                    val count = input.read(buffer); if (count < 0) break
                    if (out.size() + count > 2 * 1024 * 1024) return null
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } finally { conn.disconnect() }
    }
    fun restore(header: ViewGroup) {
        val state = states.remove(header) ?: return
        state.generation++
        state.focusListener?.let { if (header.viewTreeObserver.isAlive) header.viewTreeObserver.removeOnWindowFocusChangeListener(it) }
        listOf(header, state.row, state.back, state.more, state.center, state.avatar).forEach(FloatingChatCardVisuals::releasePresentation)
        header.removeView(state.row); header.background = state.background
        state.hidden.forEach { (view, old) -> view.alpha = old.alpha; view.importantForAccessibility = old.accessibility }
        state.hidden.clear()
    }
    fun destroy() {
        closed = true; states.keys.toList().forEach(::restore)
        if (workerStarted) worker.shutdownNow()
        main.removeCallbacksAndMessages(null); cache.evictAll()
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
    /** Both children wrap their content; gravity centers the combined avatar/title block. */
    private class CenteredTitleContent(ctx: Context) : LinearLayout(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (childCount == 2 && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                val avatar = getChildAt(0)
                val title = getChildAt(1) as TextView
                val titleParams = title.layoutParams as MarginLayoutParams
                val available = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight -
                    avatar.layoutParams.width.coerceAtLeast(0) - titleParams.leftMargin - titleParams.rightMargin).coerceAtLeast(0)
                if (title.maxWidth != available) title.maxWidth = available
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
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
