package t.mma.hooks.items.homeextension

import android.app.Activity
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import android.util.LruCache
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.utils.HLog
import t.mma.utils.KavaReflector
import java.lang.ref.WeakReference
import java.net.URL
import t.mma.hooks.items.homesidepanel.SidePanelMediaView
import t.mma.hooks.items.homesidepanel.SidePanelImages
import t.mma.hooks.items.homesidepanel.HomeSidePanelSettings
import t.mma.hooks.items.homesidepanel.SidePanelAppearance
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.WeakHashMap
import java.util.concurrent.Executors

/** A native header preserves ListView scrolling and header-position accounting. */
internal class HomeExtensionRuntime(private val context: FeatureContext) {
    private val prefs = HomeExtensionSettings.preferences(context.hostContext())
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val hooks = mutableListOf<XC_MethodHook.Unhook>()
    private val sessions = WeakHashMap<ListView, Session>()
    private val avatars = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    @Volatile private var destroyed = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        main.post { sessions.values.toList().forEach { it.safeRender() } }
    }
    private var changeSubscription: t.mma.hooks.api.conversation.WeChatConversationChangeApi.Subscription? = null
    private val updateRecent = Runnable { if (!destroyed) sessions.values.toList().forEach { it.invalidateContacts() } }
    fun install() {
        changeSubscription = WeChatApis.conversationChanges()?.subscribe {
            main.removeCallbacks(updateRecent)
            main.postDelayed(updateRecent, 600)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        val owner = KavaReflector.loadClass("com.tencent.mm.ui.conversation.ConversationListView", context.hostClassLoader()) ?: return
        KavaReflector.findMethodRecursive(owner, "setAdapter", ListAdapter::class.java)?.let { method ->
            hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val list = param.thisObject as? ListView ?: return
                    if (owner.isInstance(list)) attach(list)
                }
            })
        }
        KavaReflector.findMethodRecursive(owner, "onAttachedToWindow")?.let { method ->
            hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val list = param.thisObject as? ListView ?: return
                    if (owner.isInstance(list)) { attach(list); sessions[list]?.safeRender() }
                }
            })
        }
        val launcher = KavaReflector.loadClass("com.tencent.mm.ui.LauncherUI", context.hostClassLoader())
        if (launcher != null) KavaReflector.findMethodRecursive(launcher, "onResume")?.let { method ->
            hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.thisObject is Activity) main.post { sessions.values.toList().forEach { it.safeRender() } }
                }
            })
        }
    }
    private fun attach(list: ListView) {
        if (destroyed || sessions.containsKey(list)) return
        runCatching {
            val session = Session(list)
            sessions[list] = session
            try { list.addHeaderView(session.root, null, false) } catch (error: Throwable) { sessions.remove(list); throw error }
            main.post { session.safeRender() }
        }.onFailure { HLog.e("[Hchat:HomeExtension] 首页列表头挂载失败", it) }
    }
    fun destroy() {
        destroyed = true
        changeSubscription?.unsubscribe(); changeSubscription = null
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        hooks.forEach { HookRegistry.get().unhook(it) }
        hooks.clear()
        main.removeCallbacksAndMessages(null)
        sessions.toList().forEach { (list, session) -> session.dispose(); list.removeHeaderView(session.root) }
        sessions.clear()
        worker.shutdownNow()
        avatars.evictAll()
    }
    private data class Contact(val id: String, val name: String, val avatar: String, val group: Boolean)
    private inner class Session(list: ListView) {
        private val ctx = list.context
        val root = object : LinearLayout(ctx) {
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                media?.setPlayback(hasWindowFocus && isShown && getGlobalVisibleRect(android.graphics.Rect()))
                if (hasWindowFocus) safeRender()
            }
        }.apply { orientation = LinearLayout.VERTICAL; tag = HomeExtensionSurface.TAG }
        private var week = LocalDate.now()
        private var today = week
        private var media: SidePanelMediaView? = null
        private var loadedConfig = ""
        private var refreshing = false
        private var renderQueued = false
        private var scrollObserver: android.view.ViewTreeObserver? = null
        private var scrollListener: android.view.ViewTreeObserver.OnScrollChangedListener? = null
        fun dispose() { clearMedia() }
        private fun clearMedia() {
            scrollListener?.let { callback -> scrollObserver?.takeIf { it.isAlive }?.removeOnScrollChangedListener(callback) }
            scrollListener = null; scrollObserver = null
            media?.dispose(); media = null
        }
        fun invalidateContacts() { loadedAt = 0L; safeRender() }
        private var generation = 0
        private var loading = false
        private var cache = emptyList<Contact>()
        private var loadedAt = 0L
        private val textForegroundColor: Int get() = if (dark()) Color.WHITE else Color.rgb(35, 39, 45)
        private fun dark() = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        private fun dp(value: Int) = (value * ctx.resources.displayMetrics.density).toInt()
        private fun text(value: String, size: Float = 14f) = TextView(ctx).apply {
            text = value; textSize = size; setTextColor(this@Session.textForegroundColor); gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        private fun card(alphaKey: String) = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                val color = if (dark()) 0x202328 else 0xFFFFFF
                setColor((prefs.getInt(alphaKey, 75).coerceIn(0, 100) * 255 / 100 shl 24) or color)
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(12), dp(5), dp(12), dp(5)) }
        }
        fun safeRender() {
            if (destroyed || renderQueued) return
            renderQueued = true
            main.post {
                renderQueued = false
                if (!destroyed && !refreshing) {
                    refreshing = true
                    try { render() } catch (error: Throwable) { HLog.e("[Hchat:HomeExtension] 刷新失败", error) }
                    finally { refreshing = false }
                }
            }
        }
        private fun render() {
            if (destroyed) return
            val date = LocalDate.now()
            if (today != date) { today = date; week = date }
            generation++
            clearMedia()
            root.removeAllViews()
            val enabled = prefs.getBoolean(HomeExtensionSettings.ENABLE, false)
            val calendar = enabled && prefs.getBoolean(HomeExtensionSettings.CALENDAR, true)
            val recent = enabled && (prefs.getBoolean(HomeExtensionSettings.FAVORITES, false) || prefs.getBoolean(HomeExtensionSettings.RECENT, true))
            val photo = enabled && prefs.getBoolean(HomeExtensionSettings.PHOTO, false)
            root.visibility = if (calendar || recent || photo) View.VISIBLE else View.GONE
            root.layoutParams = AbsListView.LayoutParams(-1, if (calendar || recent || photo) -2 else 0)
            HomeExtensionSettings.order(prefs.getString(HomeExtensionSettings.ORDER, null)).forEach {
                when (it) {
                    "calendar" -> if (calendar) renderCalendar()
                    "contacts" -> if (recent) renderRecent()
                    "photo" -> if (photo) renderPhoto()
                }
            }
            val config = prefs.getBoolean(HomeExtensionSettings.FAVORITES, false).toString() + prefs.getString(HomeExtensionSettings.FAVORITE_IDS, "")
            if (recent && !loading && (config != loadedConfig || System.currentTimeMillis() - loadedAt > 30_000)) loadContacts(config)
        }
        private fun renderCalendar() {
            val panel = card(HomeExtensionSettings.CALENDAR_ALPHA)
            val title = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
            title.addView(text(week.format(DateTimeFormatter.ofPattern("yyyy年 M月")), 18f).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(0, dp(40), 1f))
            title.addView(text(prefs.getString(HomeExtensionSettings.GREETING, HomeExtensionSettings.DEFAULT_GREETING).orEmpty()).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; maxLines = 2 }, LinearLayout.LayoutParams(0, dp(40), 1.5f))
            panel.addView(title)
            val days = LinearLayout(ctx)
            val monday = week.minusDays((week.dayOfWeek.value - 1).toLong())
            repeat(7) { index ->
                val date = monday.plusDays(index.toLong())
                val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
                column.addView(text(listOf("一", "二", "三", "四", "五", "六", "日")[index], 12f), LinearLayout.LayoutParams(-1, dp(24)))
                column.addView(text(date.dayOfMonth.toString(), 19f).apply {
                    contentDescription = date.toString()
                    if (date == today) {
                        setTextColor(Color.WHITE)
                        background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(0xFF07A86A.toInt()) }
                    }
                }, LinearLayout.LayoutParams(dp(36), dp(36)))
                val lunar = android.icu.util.ChineseCalendar().apply { timeInMillis = date.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
                val day = lunar.get(android.icu.util.Calendar.DAY_OF_MONTH)
                val numbers = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
                val label = when(day) { 10 -> "初十"; 20 -> "二十"; 30 -> "三十"; else -> listOf("初", "十", "廿")[(day - 1) / 10] + numbers[(day - 1) % 10] }
                column.addView(text(label, 11f), LinearLayout.LayoutParams(-1, dp(24)))
                days.addView(column, LinearLayout.LayoutParams(0, dp(90), 1f))
            }
            panel.addView(days)
            root.addView(panel)
        }
        private fun renderRecent() {
            val panel = card(HomeExtensionSettings.RECENT_ALPHA)
            val config = prefs.getBoolean(HomeExtensionSettings.FAVORITES, false).toString() + prefs.getString(HomeExtensionSettings.FAVORITE_IDS, "")
            val contacts = if (loadedConfig == config) cache.take(8) else emptyList()
            if (contacts.isEmpty()) panel.addView(text(if (loadedAt == 0L) "正在加载最近会话…" else "暂无最近会话"), LinearLayout.LayoutParams(-1, dp(70)))
            else {
                val row = LinearLayout(ctx)
                val token = generation
                contacts.forEach { contact ->
                    val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(3), dp(5), dp(3), dp(4)) }
                    val avatar = ImageView(ctx).apply {
                        contentDescription = contact.name
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0xFF8BA59D.toInt()) }
                        clipToOutline = true
                        setImageResource(android.R.drawable.sym_def_app_icon)
                    }
                    column.addView(avatar, LinearLayout.LayoutParams(dp(46), dp(46)))
                    column.addView(text(contact.name, 11f).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(dp(66), dp(27)))
                    avatar.isClickable = true
                    avatar.foreground = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x3307A86A), null, android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.WHITE) })
                    avatar.setOnClickListener {
                        val opened = runCatching {
                            val activity = WeChatApis.currentActivity()?.currentActivity() as? Activity
                            if (activity != null) {
                                activity.startActivity(android.content.Intent().setClassName(ctx.packageName, "com.tencent.mm.ui.chatting.ChattingUI").putExtra("Chat_User", contact.id).putExtra("finish_direct", true))
                                true
                            } else WeChatApis.ui()?.openChat(contact.id) == true
                        }.getOrDefault(false)
                        if (!opened) Toast.makeText(ctx, "暂时无法打开此会话", Toast.LENGTH_SHORT).show()
                    }
                    row.addView(column, LinearLayout.LayoutParams(dp(72), -2))
                    if (contact.avatar.isNotBlank()) loadAvatar(contact.avatar, avatar, token)
                }
                panel.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(row) })
            }
            root.addView(panel)
        }
        private fun renderPhoto() {
            val file = SidePanelImages.photoFile(ctx, HomeExtensionSettings.PHOTO_SLOT)
            if (file == null) { root.addView(text("请在首页扩展中添加照片或视频"), LinearLayout.LayoutParams(-1, dp(70))); return }
            val view = SidePanelMediaView(ctx)
            media = view
            val frame = FrameLayout(ctx).apply {
                addView(view, FrameLayout.LayoutParams(-1, -1))
                alpha = prefs.getInt(HomeExtensionSettings.PHOTO_ALPHA, 100).coerceIn(0, 100) / 100f
            }
            val width = prefs.getInt(HomeExtensionSettings.PHOTO_WIDTH, 100).coerceIn(30, 100)
            val holder = LinearLayout(ctx).apply { gravity = Gravity.CENTER; setPadding(dp(12), dp(5), dp(12), dp(5)) }
            holder.addView(frame, LinearLayout.LayoutParams(0, dp(prefs.getInt(HomeExtensionSettings.PHOTO_HEIGHT, 180).coerceIn(80, 500)), width.toFloat()))
            if (width < 100) { holder.weightSum = 100f }
            root.addView(holder)
            view.load(file, HomeSidePanelSettings.preferences(ctx).getLong(SidePanelAppearance.photoRevision(HomeExtensionSettings.PHOTO_SLOT), 0L))
            fun playback() { view.setPlayback(root.isShown && root.windowVisibility == View.VISIBLE && frame.getGlobalVisibleRect(android.graphics.Rect()) && root.hasWindowFocus()) }
            val callback = android.view.ViewTreeObserver.OnScrollChangedListener { playback() }
            scrollObserver = root.viewTreeObserver
            scrollListener = callback
            scrollObserver?.addOnScrollChangedListener(callback)
            frame.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { playback() }
                override fun onViewDetachedFromWindow(v: View) { view.setPlayback(false) }
            })
            frame.post { playback() }
        }
        private fun loadContacts(config: String) {
            loading = true
            worker.execute {
                val result = runCatching {
                    val contacts = WeChatApis.contacts() ?: error("联系人 API 未就绪")
                    val conversations = WeChatApis.conversations() ?: error("会话 API 未就绪")
                    val favorites = prefs.getBoolean(HomeExtensionSettings.FAVORITES, false)
                    val ids = if (favorites) HomeExtensionSettings.favorites(prefs.getString(HomeExtensionSettings.FAVORITE_IDS, "")) else conversations.getRecentConversationUsernames(200)
                    ids.filter { favorites || contacts.isGroup(it) || contacts.isFriend(it) }.take(8).map { id ->
                        val group = contacts.isGroup(id)
                        Contact(id, contacts.getDisplayName(id).orEmpty().ifBlank { id }, contacts.getAvatarUrl(id, false).orEmpty(), group)
                    }
                }
                if (!destroyed) main.post {
                    loading = false
                    if (!destroyed) { cache = result.getOrDefault(emptyList()); loadedConfig = config; loadedAt = System.currentTimeMillis(); safeRender() }
                }
            }
        }
        private fun loadAvatar(url: String, target: ImageView, token: Int) {
            avatars.get(url)?.let { target.setImageBitmap(it); return }
            val ref = WeakReference(target)
            worker.execute {
                val bitmap = runCatching {
                    val address = URL(url)
                    if (address.protocol !in listOf("https", "http")) return@runCatching null
                    val connection = address.openConnection().apply { connectTimeout = 1500; readTimeout = 1500 }
                    connection.getInputStream().use { input ->
                        val bytes = input.readBytesLimited(512 * 1024) ?: return@use null
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        var sample = 1
                        while (bounds.outWidth / sample > 128 || bounds.outHeight / sample > 128) sample *= 2
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                }.getOrNull()
                if (bitmap != null && !destroyed) {
                    avatars.put(url, bitmap)
                    main.post { if (!destroyed && generation == token) ref.get()?.setImageBitmap(bitmap) }
                }
            }
        }
    }
}

private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray? {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) return output.toByteArray()
        if (output.size() + count > limit) return null
        output.write(buffer, 0, count)
    }
}
