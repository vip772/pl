package t.mma.hooks.items.homesidepanel
import t.mma.utils.HLog

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
        if (key == HomeSidePanelSettings.KEY_SHOW_PROFILE || key == HomeSidePanelSettings.KEY_ENABLE || key == HomeSidePanelSettings.KEY_SIGNATURE || key == HomeSidePanelSettings.KEY_SHORTCUTS) {
            handler.post { sessions.values.toList().forEach { it.refresh() } }
        }
    }

    private val effectPrefs = t.mma.preferences.HchatStorage.preferences(context.hostContext(),
        t.mma.hooks.items.backgroundbeauty.BackgroundBeautySettings.PREFS_NAME)
    private val effectListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key?.startsWith("avatar_effect_") == true) handler.post { sessions.values.toList().forEach { it.refreshEffect() } }
    }
    fun install() {
        effectPrefs.registerOnSharedPreferenceChangeListener(effectListener)
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
        hook(launcher, "onPause", emptyArray()) { (it.thisObject as? Activity)?.let { activity -> sessions[activity]?.pauseEffect() } }
        hook(launcher, "onResume", emptyArray()) { (it.thisObject as? Activity)?.let { activity -> attach(activity); sessions[activity]?.resumeEffect() } }
        hook(launcher, "onDestroy", emptyArray()) { (it.thisObject as? Activity)?.let(::detach) }
        hook(launcher, "onWindowFocusChanged", arrayOf(Boolean::class.javaPrimitiveType!!)) {
            (it.thisObject as? Activity)?.let { activity ->
                if (it.args.firstOrNull() == true) { attach(activity); sessions[activity]?.resumeEffect() }
                else sessions[activity]?.pauseEffect()
            }
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
        if (!isLauncher(activity) || activity.isFinishing || activity.isDestroyed) return
        sessions[activity]?.let {
            it.recoverFromResume()
            return
        }
        runCatching {
            val session = Session(activity)
            session.attach()
            sessions[activity] = session
            HLog.i("[Hchat:HomeSidePanel] sessionAttached activity=${activity.javaClass.name}")
        }.onFailure { HLog.e("[Hchat:HomeSidePanel] attach failed", it) }
    }

    private fun detach(activity: Activity) { handler.post { sessions.remove(activity)?.detach() } }

    fun destroy() {
        effectPrefs.unregisterOnSharedPreferenceChangeListener(effectListener)
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
        private var avatarEffect: AvatarHeaderEffect? = null
        private var effectTarget: View? = null
        private var effectContent: View? = null
        private val nativeTitles = WeakHashMap<View, android.graphics.drawable.Drawable?>()
        private fun showProfile(): Boolean = prefs.getBoolean(HomeSidePanelSettings.KEY_SHOW_PROFILE, true)
        private fun restoreNativeTitle() {
            if (nativeTitles.isEmpty()) return
            avatarEffect?.stop()
            nativeTitles.entries.toList().forEach { (view, original) ->
                if (view.foreground === avatarEffect) view.foreground = original
            }
            nativeTitles.clear()
        }
        private var effectPaused = false
        fun pauseEffect() { drawer.setActive(false); effectPaused = true; avatarEffect?.stop() }
        fun resumeEffect() { drawer.setActive(true); effectPaused = false; refreshEffect() }
        fun refreshEffect() {
            val root = header
            root ?: return
            val keys = t.mma.hooks.items.backgroundbeauty.BackgroundBeautySettings
            val show = attached && !effectPaused && root.isShown && root.hasWindowFocus() && !chatVisible && isWechatTab() &&
                HomeSidePanelSettings.enabled(activity) && (effectPrefs.getBoolean(keys.KEY_AVATAR_EFFECT, false) ||
                    effectPrefs.getBoolean(keys.KEY_AVATAR_LIGHTNING, false))
            if (!show) {
                avatarEffect?.stop()
                if (root.foreground === avatarEffect) root.foreground = null
                return
            }
            val effect = avatarEffect ?: AvatarHeaderEffect(root).also { avatarEffect = it }
            if (root.foreground !== effect) root.foreground = effect
            effect.update(effectPrefs.getString(keys.KEY_AVATAR_COLOR,
                t.mma.hooks.items.antirecall.DynamicRecallGradient.DEFAULT).orEmpty(),
                effectPrefs.getInt(keys.KEY_AVATAR_SPEED, 2400),
                effectPrefs.getFloat(keys.KEY_AVATAR_WIDTH, 1.4f),
                effectPrefs.getBoolean(keys.KEY_AVATAR_LIGHTNING, false))
        }
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
        // Fixed IDs for this Activity's host resources: never resolve names in global-layout callbacks.
        private val hostResourceIds = HashMap<String, Int>()
        private fun hostResourceId(name: String): Int = hostResourceIds.getOrPut(name) {
            activity.resources.getIdentifier(name, "id", activity.packageName)
        }
        private fun updateTitleSpace(visible: Boolean) {
            if (!visible) { restoreTitles(); return }
            val host = header?.parent as? ViewGroup ?: return
            val titleId = hostResourceId("gp")
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
            val buttonsId = hostResourceId("coz")
            val buttons = if (buttonsId != 0) host.findViewById<View>(buttonsId) else null
            val right = if (buttons != null && buttons.visibility == View.VISIBLE && buttons.width > 0) buttons.left - dp(8) else host.width - dp(14)
            val available = (right - dp(14)).coerceAtLeast(dp(48))
            val textWidth = kotlin.math.ceil(nick.paint.measureText(nick.text.toString()).toDouble()).toInt()
            val statusWidth = status?.let { kotlin.math.ceil(it.paint.measureText(it.text.toString()).toDouble()).toInt() } ?: 0
            val desired = root.paddingLeft + dp(38) + dp(10) + maxOf(textWidth, statusWidth) + root.paddingRight
            val width = minOf(desired, available)
            val lp = root.layoutParams
            if (lp is RelativeLayout.LayoutParams) {
                lp.removeRule(RelativeLayout.CENTER_IN_PARENT)
                lp.addRule(RelativeLayout.CENTER_VERTICAL)
                lp.addRule(RelativeLayout.ALIGN_PARENT_START)
                val left = if (showProfile()) ((host.width - width) / 2).coerceIn(dp(14), maxOf(dp(14), right - width)) else dp(0)
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
        private var retainedAvatar: android.graphics.Bitmap? = null
        private var lastEdgeEnabled: Boolean? = null
        private val avatarRetry = Runnable { if (attached) loadAvatar() }
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
        fun recoverFromResume() {
            if (!attached) return
            resumeEffect()
            refresh()
            decor.postOnAnimation {
                if (attached) {
                    refresh()
                    refreshEffect()
                }
            }
        }
        fun refresh() {
            if (!attached) return
            val enabled = HomeSidePanelSettings.enabled(activity)
            // LauncherUI 的首页标题在不同微信版本中可能由自定义 View/Compose 渲染，
            // 不能依赖查找文本“微信”来决定边缘触摸层是否显示。
            val homeVisible = enabled && !chatVisible

            val stateChanged = lastHomeVisible != homeVisible
            if (stateChanged) drawer.setEnabled(homeVisible)
            val edgeEnabled = homeVisible && !showProfile()
            drawer.setEdgeEnabled(edgeEnabled)
            if (header != null && header?.parent == null) header = null
            val headerVisible = homeVisible && isWechatTab()
            if (headerVisible) ensureHeader()
            val profileVisible = headerVisible
            val targetVisibility = if (profileVisible) View.VISIBLE else View.GONE
            if (header?.visibility != targetVisibility) header?.visibility = targetVisibility
            updateTitleSpace(profileVisible)
            restoreNativeTitle()
            if (profileVisible && effectTarget !== header) {
                avatarEffect?.stop()
                avatarEffect = null
                effectTarget = header
            }
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
            if (Build.VERSION.SDK_INT >= 29 && lastEdgeEnabled != edgeEnabled) {
                decor.setSystemGestureExclusionRects(
                    if (edgeEnabled) listOf(Rect(0, 0, dp(64), decor.height.coerceAtLeast(1))) else emptyList()
                )
            }
            lastEdgeEnabled = edgeEnabled
            lastHomeVisible = homeVisible
            refreshEffect()
        }

        fun setChatVisible(visible: Boolean) {
            chatVisible = visible
            refresh()
            if (visible) drawer.close(false)
        }

        fun detach() {
            attached = false
            handler.removeCallbacks(avatarRetry)
            if (Build.VERSION.SDK_INT >= 29) decor.setSystemGestureExclusionRects(emptyList())
            restoreNativeTitle()
            avatarEffect?.stop()
            avatarEffect = null
            if (decor.viewTreeObserver.isAlive) {
                decor.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
            }
            drawer.detach()
            effectTarget = null
            effectContent = null
            restoreScrollTitles()
            restoreTitles()
            (header?.parent as? ViewGroup)?.removeView(header)
            header = null
            lastHomeVisible = null
        }

        private fun close() { drawer.close(true) }

        private fun ensureHeader() {
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
            avatarEffect?.stop()
            avatarEffect = null
            val old = host.findViewWithTag<View>("hchat_home_avatar_header")
            if (old is ViewGroup) { header = old; collect(old); return }
            val root = LinearLayout(activity).apply {
                tag = "hchat_home_avatar_header"
                minimumHeight = dp(56)
                setPadding(dp(13), dp(9), dp(18), dp(9))
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
            retainedAvatar?.takeUnless { it.isRecycled }?.let(iv::setImageBitmap)
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
            if (loadedAvatarAccount == wxid && retainedAvatar?.isRecycled == false) {
                avatar?.setImageBitmap(retainedAvatar)
                drawer.setAvatar(retainedAvatar)
                avatarLoading.set(false)
                return
            }
            if (loadedAvatarAccount == wxid && (url.isBlank() || avatarUrl == url)) {
                avatarLoading.set(false)
                return
            }
            nextAvatarAttemptAt = now + 1500L
            val started = now
            profileLoader.execute {
                try {
                val digest = MessageDigest.getInstance("SHA-256").digest(wxid.toByteArray()).joinToString("") { "%02x".format(it) }
                val directory = File(t.mma.preferences.HchatStorage.storageDir(activity), "home_profile")
                val file = File(directory, "$digest.png")
                val cached = runCatching { if (file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null }.getOrNull()
                if (cached != null) handler.post {
                    if (attached && WeChatApis.account()?.selfWxId() == wxid) {
                        retainedAvatar = cached
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
                                    retainedAvatar = bitmap
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
                    if (complete && attached && WeChatApis.account()?.selfWxId() == wxid) avatarUrl = url
                }
                } catch (error: Throwable) {
                    HLog.e("[Hchat:HomeSidePanel] 头像加载失败", error)
                } finally {
                    handler.post {
                        avatarLoading.set(false)
                        if (attached && loadedAvatarAccount != wxid) handler.postDelayed(avatarRetry, 3000L)
                    }
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
