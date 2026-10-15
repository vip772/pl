package t.mma.hooks.items.homesidepanel
import t.mma.utils.HLog

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.ui.WeChatChatPageApi
import t.mma.hooks.api.ui.WeChatLifecycleApi
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.utils.KavaReflector
import java.util.Collections
import java.util.WeakHashMap
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

internal class HomeSidePanelRuntime(private val context: FeatureContext) {
    private val profileLoader = Executors.newSingleThreadExecutor { task -> Thread(task, "Hchat-HomeProfile").apply { isDaemon = true } }
    private val installedAt = android.os.SystemClock.uptimeMillis()
    private val handler = Handler(Looper.getMainLooper())
    private val sessions = Collections.synchronizedMap(WeakHashMap<Activity, Session>())
    private val hooks = ArrayList<XC_MethodHook.Unhook>()
    private val scrollTitleSessions = WeakHashMap<View, Session>()
    private var restoringScrollTitles = false
    private val prefs = HomeSidePanelSettings.preferences(context.hostContext())
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == HomeSidePanelSettings.KEY_ENABLE || key == HomeSidePanelSettings.KEY_SIGNATURE || key == HomeSidePanelSettings.KEY_SHORTCUTS) {
            handler.post { sessions.values.toList().forEach { it.refresh() } }
        }
    }

    fun install() {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        val launcher = KavaReflector.loadClass("com.tencent.mm.ui.LauncherUI", context.hostClassLoader()) ?: return
        KavaReflector.findMethodRecursive(View::class.java, "setVisibility", Int::class.javaPrimitiveType!!)?.let { method ->
            hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (restoringScrollTitles) return
                    val view = param.thisObject as? View ?: return
                    val session = scrollTitleSessions[view] ?: return
                    val requested = param.args.firstOrNull() as? Int ?: return
                    session.recordScrollTitleVisibility(view, requested)
                    if (session.hideScrollTitle()) param.args[0] = View.GONE
                }
            })
        }
        hook(launcher, "onCreate", arrayOf(Bundle::class.java)) { (it.thisObject as? Activity)?.let(::attach) }
        hook(launcher, "onResume", emptyArray()) { (it.thisObject as? Activity)?.let(::attach) }
        hook(launcher, "onDestroy", emptyArray()) { (it.thisObject as? Activity)?.let(::detach) }
        hook(launcher, "onWindowFocusChanged", arrayOf(Boolean::class.javaPrimitiveType!!)) {
            if (it.args.firstOrNull() == true) (it.thisObject as? Activity)?.let(::attach)
        }
    }
    private fun hook(owner: Class<*>, name: String, params: Array<Class<*>>, action: (XC_MethodHook.MethodHookParam) -> Unit) {
        KavaReflector.findMethodRecursive(owner, name, *params)?.let { method ->
            runCatching {
                hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) = action(param)
                })
            }.onFailure { HLog.e("[Hchat:HomeSidePanel] hook $name failed", it) }
        }
    }

    fun onActivityEvent(event: WeChatLifecycleApi.ActivityEvent) {
        if (event.isResume()) attach(event.activity)
        if (event.isDestroy()) detach(event.activity)
    }

    fun onChatPageChanged(event: WeChatChatPageApi.ChatPageEvent) {
        val activity = WeChatApis.currentActivity()?.currentActivity() ?: return
        handler.post { sessions[activity]?.setChatVisible(event.isEnter()) }
    }

    fun attach(activity: Activity) {
        if (!isLauncher(activity)) return
        if (Looper.myLooper() == Looper.getMainLooper()) attachNow(activity)
        else handler.post { attachNow(activity) }
    }

    private fun attachNow(activity: Activity) {
        if (!isLauncher(activity) || activity.isFinishing || activity.isDestroyed || sessions[activity] != null) return
        runCatching {
            val session = Session(activity)
            session.attach()
            sessions[activity] = session
            HLog.i("[Hchat:HomeSidePanel] sessionAttached activity=${activity.javaClass.name}")
        }.onFailure { HLog.e("[Hchat:HomeSidePanel] attach failed", it) }
    }

    private fun detach(activity: Activity) { handler.post { sessions.remove(activity)?.detach() } }

    fun destroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        handler.post { sessions.values.toList().forEach { it.detach() }; sessions.clear() }
        hooks.forEach { runCatching { HookRegistry.get().unhook(it) } }
        hooks.clear()
    }

    private fun isLauncher(activity: Activity): Boolean = activity.javaClass.name.endsWith(".LauncherUI") || activity.javaClass.name == "com.tencent.mm.ui.LauncherUI"

    private inner class Session(private val activity: Activity) {
        private val decor = activity.window.decorView as ViewGroup
        private val drawer = HomeSidePanelDrawer(activity) { close() }
        private var header: ViewGroup? = null
        private val replacedTitles = WeakHashMap<View, Int>()
        private val scrollTitles = WeakHashMap<View, Int>()
        fun hideScrollTitle(): Boolean = attached && !chatVisible && isWechatTab() && HomeSidePanelSettings.enabled(activity)
        fun recordScrollTitleVisibility(view: View, requested: Int) { scrollTitles[view] = requested }
        private fun restoreScrollTitles() {
            restoringScrollTitles = true
            try {
                scrollTitles.entries.toList().forEach { (view, requested) ->
                    scrollTitleSessions.remove(view)
                    view.visibility = requested
                }
                scrollTitles.clear()
            } finally { restoringScrollTitles = false }
        }
        private fun restoreTitles() {
            replacedTitles.entries.toList().forEach { (view, visibility) -> view.visibility = visibility }
            replacedTitles.clear()
        }
        private fun updateTitleSpace(visible: Boolean) {
            if (!visible) { restoreTitles(); return }
            val host = header?.parent as? ViewGroup ?: return
            val titleId = activity.resources.getIdentifier("gp", "id", activity.packageName)
            if (titleId == 0) return
            val title = host.findViewById<View>(titleId) ?: return
            if (!replacedTitles.containsKey(title)) replacedTitles[title] = title.visibility
            if (title.visibility != View.INVISIBLE) title.visibility = View.INVISIBLE
        }
        private fun updateScrollableTitle(visible: Boolean) {
            if (!HomeSidePanelSettings.enabled(activity)) { restoreScrollTitles(); return }
            if (!visible) return
            fun walk(view: View, inTaskBar: Boolean, depth: Int) {
                if (depth > 24) return
                val task = inTaskBar || view.javaClass.name == "com.tencent.mm.plugin.taskbar.ui.TaskBarContainer"
                // 8078 cxw.xml：1620是重复文字及搜索/加号控件行，限定TaskBar子树。
                if (task && view.id == 0x7f0a1620 && view is RelativeLayout) {
                    if (!scrollTitles.containsKey(view)) {
                        scrollTitles[view] = view.visibility
                        scrollTitleSessions[view] = this@Session
                        HLog.i("[Hchat:HomeSidePanel] scrollTitleRegistered id=${view.id} class=${view.javaClass.name}")
                    }
                    if (view.visibility != View.GONE) {
                        restoringScrollTitles = true
                        try { view.visibility = View.GONE } finally { restoringScrollTitles = false }
                    }
                }
                if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i), task, depth + 1)
            }
            walk(decor, false, 0)
        }
        private fun sizeHeader() {
            val root = header ?: return
            val host = root.parent as? ViewGroup ?: return
            val nick = name ?: return
            if (host.width <= 0) return
            val buttonsId = activity.resources.getIdentifier("coz", "id", activity.packageName)
            val buttons = if (buttonsId != 0) host.findViewById<View>(buttonsId) else null
            val right = if (buttons != null && buttons.visibility == View.VISIBLE && buttons.width > 0) buttons.left - dp(8) else host.width - dp(14)
            val available = (right - dp(14)).coerceAtLeast(dp(48))
            val textWidth = kotlin.math.ceil(nick.paint.measureText(nick.text.toString()).toDouble()).toInt()
            val statusWidth = status?.let { kotlin.math.ceil(it.paint.measureText(it.text.toString()).toDouble()).toInt() } ?: 0
            val desired = dp(48) + maxOf(textWidth, statusWidth) + dp(2)
            val width = minOf(desired, available)
            val lp = root.layoutParams
            if (lp is RelativeLayout.LayoutParams) {
                lp.removeRule(RelativeLayout.CENTER_IN_PARENT)
                lp.addRule(RelativeLayout.CENTER_VERTICAL)
                lp.addRule(RelativeLayout.ALIGN_PARENT_START)
                val left = ((host.width - width) / 2).coerceIn(dp(14), maxOf(dp(14), right - width))
                if (lp.width != width || lp.marginStart != left) {
                    lp.width = width
                    lp.marginStart = left
                    root.layoutParams = lp
                }
            }
        }
        private var avatar: ImageView? = null
        private var name: TextView? = null
        private var status: TextView? = null
        private var attached = false
        private var chatVisible = false
        private var headerAttempts = 0
        private val avatarLoading = AtomicBoolean(false)
        private var avatarUrl: String? = null
        private var loadedAvatarAccount: String? = null
        private var nextAvatarAttemptAt = 0L
        private var headerRetryPending = false
        private var lastHomeVisible: Boolean? = null
        private var profileRefreshAt = 0L
        private var titleScanAt = 0L
        private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (attached) refresh()
        }

        fun attach() {
            if (attached) return
            attached = true
            decor.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
            drawer.attachTo(decor)
            refresh()
        }

        fun refresh() {
            if (!attached) return
            val enabled = HomeSidePanelSettings.enabled(activity)
            // LauncherUI 的首页标题在不同微信版本中可能由自定义 View/Compose 渲染，
            // 不能依赖查找文本“微信”来决定边缘触摸层是否显示。
            val homeVisible = enabled && !chatVisible

            val stateChanged = lastHomeVisible != homeVisible
            if (stateChanged) drawer.setEnabled(homeVisible)
            if (header != null && header?.parent == null) header = null
            val headerVisible = homeVisible && isWechatTab()
            if (headerVisible) ensureHeader()
            val targetVisibility = if (headerVisible) View.VISIBLE else View.GONE
            if (header?.visibility != targetVisibility) header?.visibility = targetVisibility
            updateTitleSpace(headerVisible)
            val now = android.os.SystemClock.uptimeMillis()
            if (now >= profileRefreshAt) {
                profileRefreshAt = now + 1500L
                val currentName = accountName()
                if (name?.text?.toString() != currentName) name?.text = currentName
                if (homeVisible) loadAvatar()
            }
            if (status?.text?.toString() != "在线") status?.text = "在线"
            if (stateChanged || now >= titleScanAt) {
                titleScanAt = now + 1000L
                updateScrollableTitle(headerVisible)
            }
            if (headerVisible) sizeHeader()
            lastHomeVisible = homeVisible
        }

        fun setChatVisible(visible: Boolean) {
            chatVisible = visible
            refresh()
            if (visible) drawer.close(false)
        }

        fun detach() {
            attached = false
            if (decor.viewTreeObserver.isAlive) {
                decor.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
            }
            drawer.detach()
            restoreScrollTitles()
            restoreTitles()
            (header?.parent as? ViewGroup)?.removeView(header)
            header = null
            lastHomeVisible = null
        }

        private fun close() { drawer.close(true) }

        private fun ensureHeader() {
            if (header?.isAttachedToWindow == true) return
            val host = findActionBarContent(decor)
            if (host == null || !isWechatTab()) {
                if (!headerRetryPending && headerAttempts++ < 8) {
                    headerRetryPending = true
                    decor.postOnAnimation {
                        headerRetryPending = false
                        if (attached) refresh()
                    }
                }
                return
            }
            headerAttempts = 0
            if (header?.parent === host) return
            restoreTitles()
            (header?.parent as? ViewGroup)?.removeView(header)
            header = null
            val old = host.findViewWithTag<View>("hchat_home_avatar_header")
            if (old is ViewGroup) { header = old; collect(old); return }
            val root = LinearLayout(activity).apply {
                tag = "hchat_home_avatar_header"
                minimumHeight = dp(44)
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                setOnClickListener { drawer.toggle() }
            }
            val iv = ImageView(activity).apply {
                tag = "hchat_home_avatar"
                setImageDrawable(GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(210, 210, 210)) })
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
            }
            root.addView(iv, LinearLayout.LayoutParams(dp(38), dp(38)))
            val text = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
            val nick = TextView(activity).apply {
                tag = "hchat_home_avatar_name"
                setTextColor(if (night()) Color.WHITE else Color.rgb(30, 30, 30))
                textSize = 14f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val st = TextView(activity).apply {
                tag = "hchat_home_avatar_status"
                setTextColor(Color.rgb(7, 193, 96))
                textSize = 11.5f
                maxLines = 1
            }
            text.addView(nick); text.addView(st)
            text.setOnClickListener { drawer.toggle() }
            root.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
            val lp: ViewGroup.LayoutParams = if (host is RelativeLayout) {
                RelativeLayout.LayoutParams(-2, -2).apply {
                    addRule(RelativeLayout.CENTER_IN_PARENT)
                }
            } else {
                LinearLayout.LayoutParams(-2, -2).apply {
                    gravity = Gravity.CENTER
                }
            }
            host.addView(root, lp)
            header = root; avatar = iv; name = nick; status = st
            HLog.i("[Hchat:HomeSidePanel] headerAttached tab=0 host=${host.javaClass.name} id=${host.id} startupMs=${android.os.SystemClock.uptimeMillis() - installedAt}")
            root.post {
                if (attached && header === root) {
                    val rect = Rect()
                    val visible = root.getGlobalVisibleRect(rect)
                    HLog.i("[Hchat:HomeSidePanel] headerLayout shown=${root.isShown} visible=$visible rect=$rect size=${root.width}x${root.height}")
                }
            }
        }

        private fun collect(root: View) {
            avatar = root.findViewWithTag("hchat_home_avatar")
            name = root.findViewWithTag("hchat_home_avatar_name")
            status = root.findViewWithTag("hchat_home_avatar_status")
        }

        private fun accountName(): String {
            val account = WeChatApis.account()
            val wxid = account?.selfWxId().orEmpty()
            val current = account?.selfName()?.takeIf { it.isNotBlank() }
            if (wxid.isBlank()) return current ?: "我"
            val key = "profile-name:$wxid"
            if (current != null) {
                if (prefs.getString(key, null) != current) prefs.edit().putString(key, current).apply()
                return current
            }
            return prefs.getString(key, null)?.takeIf { it.isNotBlank() } ?: "我"
        }
        private fun loadAvatar() {
            val now = android.os.SystemClock.uptimeMillis()
            if (now < nextAvatarAttemptAt || !avatarLoading.compareAndSet(false, true)) return
            val wxid = WeChatApis.account()?.selfWxId().orEmpty()
            if (wxid.isBlank()) { avatarLoading.set(false); return }
            val url = runCatching { WeChatApis.contacts()?.getContact(wxid)?.avatarUrl.orEmpty() }.getOrDefault("")
            if (loadedAvatarAccount == wxid && (url.isBlank() || avatarUrl == url)) {
                avatarLoading.set(false)
                return
            }
            nextAvatarAttemptAt = now + 1500L
            val started = now
            profileLoader.execute {
                val digest = MessageDigest.getInstance("SHA-256").digest(wxid.toByteArray()).joinToString("") { "%02x".format(it) }
                val directory = File(t.mma.preferences.HchatStorage.storageDir(activity), "home_profile")
                val file = File(directory, "$digest.png")
                val cached = runCatching { if (file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null }.getOrNull()
                if (cached != null) handler.post {
                    if (attached && WeChatApis.account()?.selfWxId() == wxid) {
                        avatar?.setImageBitmap(cached)
                        drawer.setAvatar(cached)
                        loadedAvatarAccount = wxid
                        HLog.i("[Hchat:HomeSidePanel] avatarCacheMs=${android.os.SystemClock.uptimeMillis() - started}")
                    }
                }
                val savedUrl = prefs.getString("profile-url:$wxid", null)
                val checkedAt = prefs.getLong("profile-checked:$wxid", 0L)
                val stale = System.currentTimeMillis() - checkedAt > 6L * 60L * 60L * 1000L
                var refreshed = false
                if (url.isNotBlank() && (cached == null || savedUrl != url || stale)) {
                    try {
                        val connection = URL(url).openConnection().apply { connectTimeout = 4000; readTimeout = 4000 }
                        val bitmap = connection.getInputStream().use { BitmapFactory.decodeStream(it) }
                        if (bitmap != null) {
                            directory.mkdirs()
                            val temporary = File(directory, "$digest.tmp")
                            FileOutputStream(temporary).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                            java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                            prefs.edit().putString("profile-url:$wxid", url)
                                .putLong("profile-checked:$wxid", System.currentTimeMillis()).apply()
                            refreshed = true
                            handler.post {
                                if (attached && WeChatApis.account()?.selfWxId() == wxid) {
                                    avatar?.setImageBitmap(bitmap)
                                    drawer.setAvatar(bitmap)
                                    loadedAvatarAccount = wxid
                                    avatarUrl = url
                                }
                            }
                        }
                    } catch (error: Throwable) {
                        HLog.e("[Hchat:HomeSidePanel] 头像刷新失败，保留本地缓存", error)
                    }
                }
                val complete = (cached != null && savedUrl == url) || refreshed
                handler.post {
                    avatarLoading.set(false)
                    if (complete && attached && WeChatApis.account()?.selfWxId() == wxid) avatarUrl = url
                }
            }
        }
        private fun findActionBarContent(root: ViewGroup): ViewGroup? {
            val pkg = root.context.packageName
            // 8078运行树确认hb为Toolbar内全宽自定义标题，gp为文字标题，coz为右侧按钮。
            val customId = root.context.resources.getIdentifier("hb", "id", pkg)
            if (customId != 0) {
                val custom = root.findViewById<View>(customId)
                if (custom is RelativeLayout && custom.parent?.javaClass?.name?.contains("Toolbar") == true) return custom
            }
            val id = root.context.resources.getIdentifier("ih", "id", pkg)
            if (id != 0) root.findViewById<ViewGroup>(id)?.let { return it }
            val toolbarId = root.context.resources.getIdentifier("ez", "id", pkg)
            if (toolbarId != 0) root.findViewById<ViewGroup>(toolbarId)?.let { tb ->
                for (i in 0 until tb.childCount) if (tb.getChildAt(i) is ViewGroup) return tb.getChildAt(i) as ViewGroup
                return tb
            }
            var found: ViewGroup? = null
            fun walk(v: View, depth: Int) {
                if (found != null || depth > 10) return
                if (v is ViewGroup && v.javaClass.name.contains("Toolbar")) { found = v; return }
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1)
            }
            walk(root, 0); return found
        }

        private fun isWechatTab(): Boolean {
            val home = KavaReflector.readField(activity, "i") ?: return false
            val tabs = KavaReflector.readField(home, "t") ?: return false
            return (KavaReflector.readField(tabs, "e") as? Int) == 0
        }
                private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density + .5f).toInt()
        private fun night(): Boolean = (activity.resources.configuration.uiMode and 0x30) == 0x20
    }
}
