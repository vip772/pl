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
        main.post { sessions.values.toList().forEach { it.render() } }
    }
    fun install() {
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
                    if (owner.isInstance(list)) { attach(list); sessions[list]?.render() }
                }
            })
        }
        val launcher = KavaReflector.loadClass("com.tencent.mm.ui.LauncherUI", context.hostClassLoader())
        if (launcher != null) KavaReflector.findMethodRecursive(launcher, "onResume")?.let { method ->
            hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.thisObject is Activity) main.post { sessions.values.toList().forEach { it.render() } }
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
            session.render()
        }.onFailure { HLog.e("[Hchat:HomeExtension] 首页列表头挂载失败", it) }
    }
    fun destroy() {
        destroyed = true
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        hooks.forEach { HookRegistry.get().unhook(it) }
        hooks.clear()
        main.removeCallbacksAndMessages(null)
        sessions.toList().forEach { (list, session) -> list.removeHeaderView(session.root) }
        sessions.clear()
        worker.shutdownNow()
        avatars.evictAll()
    }
    private data class Contact(val id: String, val name: String, val avatar: String, val group: Boolean)
    private inner class Session(list: ListView) {
        private val ctx = list.context
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        private var week = LocalDate.now()
        private var today = week
        private var groups = false
        private var generation = 0
        private var loading = false
        private var cache = emptyList<Contact>()
        private var loadedAt = 0L
        private val foreground: Int get() = if (dark()) Color.WHITE else Color.rgb(35, 39, 45)
        private fun dark() = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        private fun dp(value: Int) = (value * ctx.resources.displayMetrics.density).toInt()
        private fun text(value: String, size: Float = 14f) = TextView(ctx).apply {
            text = value; textSize = size; setTextColor(foreground); gravity = Gravity.CENTER
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
        fun render() {
            if (destroyed) return
            val date = LocalDate.now()
            if (today != date) { today = date; week = date }
            generation++
            root.removeAllViews()
            val enabled = prefs.getBoolean(HomeExtensionSettings.ENABLE, false)
            val calendar = enabled && prefs.getBoolean(HomeExtensionSettings.CALENDAR, true)
            val recent = enabled && prefs.getBoolean(HomeExtensionSettings.RECENT, true)
            root.visibility = if (calendar || recent) View.VISIBLE else View.GONE
            root.layoutParams = AbsListView.LayoutParams(-1, if (calendar || recent) -2 else 0)
            if (calendar) renderCalendar()
            if (recent) {
                renderRecent()
                if (!loading && System.currentTimeMillis() - loadedAt > 30_000) loadContacts()
            }
        }
        private fun renderCalendar() {
            val panel = card(HomeExtensionSettings.CALENDAR_ALPHA)
            val title = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
            title.addView(text(week.format(DateTimeFormatter.ofPattern("yyyy年 M月")), 18f), LinearLayout.LayoutParams(0, dp(36), 1f))
            listOf("‹", "今天", "›").forEachIndexed { i, label ->
                title.addView(text(label, 16f).apply {
                    contentDescription = listOf("上一周", "回到今天", "下一周")[i]
                    setOnClickListener { week = when (i) { 0 -> week.minusWeeks(1); 2 -> week.plusWeeks(1); else -> today }; render() }
                }, LinearLayout.LayoutParams(dp(48), dp(44)))
            }
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
                days.addView(column, LinearLayout.LayoutParams(0, dp(66), 1f))
            }
            panel.addView(days)
            val lunar = android.icu.util.ChineseCalendar().apply { timeInMillis = System.currentTimeMillis() }
            panel.addView(text("农历 " + (lunar.get(android.icu.util.Calendar.MONTH) + 1) + "月" + lunar.get(android.icu.util.Calendar.DAY_OF_MONTH) + "日", 12f))
            panel.addView(text(prefs.getString(HomeExtensionSettings.GREETING, HomeExtensionSettings.DEFAULT_GREETING).orEmpty()).apply {
                setPadding(0, dp(8), 0, dp(2)); maxLines = 3
            })
            root.addView(panel)
        }
        private fun renderRecent() {
            val panel = card(HomeExtensionSettings.RECENT_ALPHA)
            val tabs = LinearLayout(ctx)
            listOf("最近联系人", "最近群聊").forEachIndexed { i, label ->
                tabs.addView(text(label, 15f).apply {
                    if (groups == (i == 1)) setTextColor(0xFF07A86A.toInt())
                    setOnClickListener { groups = i == 1; render() }
                }, LinearLayout.LayoutParams(0, dp(40), 1f))
            }
            panel.addView(tabs)
            val contacts = cache.filter { it.group == groups }.take(12)
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
                    column.setOnClickListener {
                        if (WeChatApis.ui()?.openChat(contact.id) != true) Toast.makeText(ctx, "暂时无法打开此会话", Toast.LENGTH_SHORT).show()
                    }
                    row.addView(column, LinearLayout.LayoutParams(dp(72), -2))
                    if (contact.avatar.isNotBlank()) loadAvatar(contact.avatar, avatar, token)
                }
                panel.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(row) })
            }
            root.addView(panel)
        }
        private fun loadContacts() {
            loading = true
            worker.execute {
                val result = runCatching {
                    val contacts = WeChatApis.contacts() ?: error("联系人 API 未就绪")
                    val conversations = WeChatApis.conversations() ?: error("会话 API 未就绪")
                    var people = 0; var rooms = 0
                    conversations.getRecentConversationUsernames(200).mapNotNull { id ->
                        val group = contacts.isGroup(id)
                        if (group) { if (rooms >= 12) return@mapNotNull null; rooms++ }
                        else { if (people >= 12 || !contacts.isFriend(id)) return@mapNotNull null; people++ }
                        Contact(id, contacts.getDisplayName(id).orEmpty().ifBlank { id }, contacts.getAvatarUrl(id, false).orEmpty(), group)
                    }
                }
                if (!destroyed) main.post {
                    loading = false
                    if (!destroyed) { cache = result.getOrDefault(emptyList()); loadedAt = System.currentTimeMillis(); render() }
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
