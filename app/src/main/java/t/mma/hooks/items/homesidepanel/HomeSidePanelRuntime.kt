package t.mma.hooks.items.homesidepanel
import t.mma.utils.HLog

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
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
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

internal class HomeSidePanelRuntime(private val context: FeatureContext) {
    private val handler = Handler(Looper.getMainLooper())
    private val sessions = Collections.synchronizedMap(WeakHashMap<Activity, Session>())
    private var diagnosticDispatchCount = 0
    private val hooks = ArrayList<XC_MethodHook.Unhook>()
    private val prefs = HomeSidePanelSettings.preferences(context.hostContext())
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == HomeSidePanelSettings.KEY_ENABLE || key == HomeSidePanelSettings.KEY_SIGNATURE || key == HomeSidePanelSettings.KEY_SHORTCUTS) {
            handler.post { sessions.values.toList().forEach { it.refresh() } }
        }
    }

    fun install() {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        val launcher = KavaReflector.loadClass("com.tencent.mm.ui.LauncherUI", context.hostClassLoader()) ?: return
        hook(launcher, "onCreate", arrayOf(Bundle::class.java)) { (it.thisObject as? Activity)?.let(::attach) }
        hook(launcher, "onResume", emptyArray()) { (it.thisObject as? Activity)?.let(::attach) }
        hook(launcher, "onDestroy", emptyArray()) { (it.thisObject as? Activity)?.let(::detach) }
        hook(launcher, "onWindowFocusChanged", arrayOf(Boolean::class.javaPrimitiveType!!)) {
            if (it.args.firstOrNull() == true) (it.thisObject as? Activity)?.let(::attach)
        }
        val dispatchMethod = KavaReflector.findMethodRecursive(launcher, "dispatchTouchEvent", MotionEvent::class.java)
        HLog.i("[Hchat:HomeSidePanel] DIAG install dispatch=$dispatchMethod")
        dispatchMethod?.let { method ->
            hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    val event = param.args.firstOrNull() as? MotionEvent ?: return
                    if (isLauncher(activity) && sessions[activity] == null) attachNow(activity)
                    val session = sessions[activity]
                    if (event.actionMasked == MotionEvent.ACTION_DOWN && event.x <= 100f && diagnosticDispatchCount++ < 12) {
                        HLog.i("[Hchat:HomeSidePanel] DIAG dispatch down x=${event.x} y=${event.y} session=${session != null}")
                    }
                    if (session?.interceptTouch(event) == true) param.setResult(true)
                }
            })
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
        handler.post { attachNow(activity) }
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
        private var avatar: ImageView? = null
        private var name: TextView? = null
        private var status: TextView? = null
        private var attached = false
        private var chatVisible = false
        private var headerAttempts = 0
        private val avatarLoading = AtomicBoolean(false)
        private var avatarUrl: String? = null
        private var lastHomeVisible: Boolean? = null
        private val originalGestureExclusions = if (Build.VERSION.SDK_INT >= 29) decor.systemGestureExclusionRects.toList() else emptyList()
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
            val currentName = accountName()
            if (name?.text?.toString() != currentName) name?.text = currentName
            if (status?.text?.toString() != "在线") status?.text = "在线"
            if (homeVisible) loadAvatar()
            if (Build.VERSION.SDK_INT >= 29 && stateChanged) {
                decor.setSystemGestureExclusionRects(
                    if (homeVisible) originalGestureExclusions + Rect(0, 0, dp(24), decor.height.coerceAtLeast(1)) else originalGestureExclusions
                )
            }
            lastHomeVisible = homeVisible
        }

        private var diagnosticGestures = 0
        private var diagnosticMoves = 0
        private var diagnosticEdge = false
        fun interceptTouch(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                diagnosticEdge = event.x <= dp(72)
                if (diagnosticEdge) diagnosticGestures++
                diagnosticMoves = 0
            }
            val result = attached && !chatVisible && drawer.interceptEdgeTouch(event)
            if (diagnosticEdge && diagnosticGestures <= 12 && (event.actionMasked != MotionEvent.ACTION_MOVE || diagnosticMoves++ < 3)) {
                HLog.i("[Hchat:HomeSidePanel] DIAG gesture action=${event.actionMasked} x=${event.x} y=${event.y} attached=$attached chat=$chatVisible enabled=${HomeSidePanelSettings.enabled(activity)} density=${activity.resources.displayMetrics.density} edgePx=${dp(72)} consumed=$result")
            }
            return result
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
            if (Build.VERSION.SDK_INT >= 29) decor.systemGestureExclusionRects = originalGestureExclusions
            restoreTitles()
            (header?.parent as? ViewGroup)?.removeView(header)
            header = null
            lastHomeVisible = null
        }

        private fun close() { drawer.close(true) }

        private fun ensureHeader() {
            val host = findActionBarContent(decor)
            if (host == null || !isWechatTab()) {
                if (headerAttempts++ < 8) handler.postDelayed({ if (attached) ensureHeader() }, 300L)
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
                maxWidth = dp(135)
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val st = TextView(activity).apply {
                tag = "hchat_home_avatar_status"
                setTextColor(if (night()) Color.LTGRAY else Color.GRAY)
                textSize = 11.5f
                maxLines = 1
            }
            text.addView(nick); text.addView(st)
            text.setOnClickListener { drawer.toggle() }
            root.addView(text, LinearLayout.LayoutParams(-2, -2))
            val lp: ViewGroup.LayoutParams = if (host is RelativeLayout) {
                RelativeLayout.LayoutParams(-2, -2).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_START)
                    addRule(RelativeLayout.CENTER_VERTICAL)
                    leftMargin = dp(14)
                    marginStart = dp(14)
                }
            } else {
                LinearLayout.LayoutParams(-2, -2).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginStart = dp(14)
                }
            }
            host.addView(root, lp)
            header = root; avatar = iv; name = nick; status = st
            HLog.i("[Hchat:HomeSidePanel] headerAttached tab=0 host=${host.javaClass.name} id=${host.id}")
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

        private fun accountName(): String = WeChatApis.account()?.selfName()?.takeIf { it.isNotBlank() } ?: "我"

        private fun loadAvatar() {
            if (!avatarLoading.compareAndSet(false, true)) return
            val wxid = WeChatApis.account()?.selfWxId().orEmpty()
            val url = runCatching { WeChatApis.contacts()?.getContact(wxid)?.avatarUrl.orEmpty() }.getOrDefault("")
            if (url.isBlank()) { avatarLoading.set(false); return }
            if (avatarUrl == url) { avatarLoading.set(false); return }
            avatarUrl = url
            Thread {
                val bitmap = runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) } }.getOrNull()
                handler.post {
                    avatarLoading.set(false)
                    if (bitmap != null && !bitmap.isRecycled) {
                        avatar?.setImageBitmap(bitmap)
                        drawer.setAvatar(bitmap)
                    }
                }
            }.start()
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
