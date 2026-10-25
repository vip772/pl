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
import java.net.HttpURLConnection
import android.os.SystemClock
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
    private val avatarWorker = Executors.newFixedThreadPool(3)
    // Accessed only on main; repeated requests share a single download.
    private val avatarRequests = mutableMapOf<String, MutableList<(Bitmap?) -> Unit>>()
    private val avatarDisk by lazy { java.io.File(context.hostContext().cacheDir, "hchat_contact_avatars").apply { mkdirs() } }
    private val musicDataKeys = setOf("title", "artist", "url", "cover", "lyric", "source", "source_id", "music_favorites", HomeExtensionSettings.LYRIC_X, HomeExtensionSettings.LYRIC_Y, HomeExtensionSettings.LYRIC_COLOR, HomeExtensionSettings.LYRIC_ALPHA)
    private val hooks = mutableListOf<XC_MethodHook.Unhook>()
    private val sessions = WeakHashMap<ListView, Session>()
    private val avatars = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    @Volatile private var destroyed = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key !in musicDataKeys) main.post { sessions.values.toList().forEach { it.safeRender() } }
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
                override fun afterHookedMethod(param: MethodHookParam) {
                    val list = param.thisObject as? ListView ?: return
                    if (owner.isInstance(list)) scheduleAttach(list)
                }
            })
        }
        KavaReflector.findMethodRecursive(owner, "onAttachedToWindow")?.let { method ->
            hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val list = param.thisObject as? ListView ?: return
                    if (owner.isInstance(list)) scheduleAttach(list)
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
    private val pendingAttach = java.util.Collections.newSetFromMap(WeakHashMap<ListView, Boolean>())
    private fun scheduleAttach(list: ListView) {
        if (destroyed || !pendingAttach.add(list)) return
        list.post {
            pendingAttach.remove(list)
            // Never change header indices while WeChat initializes its own title/task headers.
            if (!destroyed && list.adapter != null && list.isAttachedToWindow) attach(list)
        }
    }
    private fun attach(list: ListView) {
        if (destroyed) return
        runCatching { installRecycleGuard(list) }.onFailure { HLog.e("[Hchat:HomeExtension] 列表复用隔离安装失败", it); return }
        sessions[list]?.let { it.safeRender(); return }
        if (list.adapter == null) return
        runCatching {
            val session = Session(list)
            sessions[list] = session
            try { list.addHeaderView(session.root, null, false) } catch (error: Throwable) { sessions.remove(list); throw error }
            main.post { session.safeRender() }
        }.onFailure { HLog.e("[Hchat:HomeExtension] 首页列表头挂载失败", it) }
    }
    private val guardedAdapters = mutableSetOf<java.lang.reflect.Method>()
    private fun installRecycleGuard(list: ListView) {
        var adapter = list.adapter ?: return
        while (adapter is HeaderViewListAdapter) adapter = adapter.wrappedAdapter ?: return
        val method = KavaReflector.findMethodRecursive(adapter.javaClass, "getView", Integer.TYPE, View::class.java, android.view.ViewGroup::class.java) ?: return
        if (method in guardedAdapters) return
        val unhook = HookRegistry.get().hook(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val recycled = param.args.getOrNull(1) as? View ?: return
                // Module headers must never enter WeChat's conversation ViewHolder binding.
                if (HomeExtensionSurface.contains(recycled)) param.args[1] = null
            }
        })
        hooks += unhook
        guardedAdapters += method
    }
    fun destroy() {
        destroyed = true
        changeSubscription?.unsubscribe(); changeSubscription = null
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        hooks.forEach { HookRegistry.get().unhook(it) }
        hooks.clear()
        guardedAdapters.clear()
        main.removeCallbacksAndMessages(null)
        sessions.toList().forEach { (list, session) -> session.dispose(); list.removeHeaderView(session.root) }
        sessions.clear()
        worker.shutdownNow()
        avatarWorker.shutdownNow()
        avatarRequests.clear()
        avatars.evictAll()
    }
    private fun requestAvatar(url: String, callback: (Bitmap?) -> Unit) {
        avatars.get(url)?.let { callback(it); return }
        avatarRequests[url]?.let { it.add(callback); return }
        avatarRequests[url] = mutableListOf(callback)
        avatarWorker.execute {
            val started = SystemClock.uptimeMillis()
            val result = runCatching {
                val disk = java.io.File(avatarDisk, java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".jpg")
                val cached = if (disk.isFile) BitmapFactory.decodeFile(disk.path) else null
                if (cached != null) return@runCatching cached
                val address = URL(url)
                require(address.protocol == "http" || address.protocol == "https")
                val connection = address.openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 4000; connection.readTimeout = 4000
                    connection.inputStream.use { input ->
                        val bytes = input.readBytesLimited(512 * 1024) ?: error("avatar too large")
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        var sample = 1
                        while (bounds.outWidth / sample > 128 || bounds.outHeight / sample > 128) sample *= 2
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                            ?.also { bitmap ->
                                runCatching {
                                    val temp = java.io.File(disk.path + ".tmp")
                                    try { temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; temp.renameTo(disk) }
                                    finally { temp.delete() }
                                    // Bound disk use, retaining the most recently downloaded avatars.
                                    avatarDisk.listFiles()?.filter { it.extension == "jpg" }?.sortedByDescending { it.lastModified() }?.drop(128)?.forEach { it.delete() }
                                }
                            } ?: error("avatar decode failed")
                    }
                } finally { connection.disconnect() }
            }
            if (!destroyed) main.post {
                val callbacks = avatarRequests.remove(url).orEmpty()
                if (!destroyed) {
                    result.getOrNull()?.let { avatars.put(url, it) }
                    result.exceptionOrNull()?.let { HLog.e("[Hchat:HomeExtension] 头像加载失败，耗时=${SystemClock.uptimeMillis() - started}ms", it) }
                    callbacks.forEach { it(result.getOrNull()) }
                }
            }
        }
    }

    private data class Contact(val id: String, val name: String, val avatar: String, val group: Boolean, val fallbackAvatar: String)
    private inner class Session(list: ListView) {
        private val ctx = list.context
        val root: LinearLayout = object : LinearLayout(ctx), HomeExtensionSurface.Root {
            override fun onWindowVisibilityChanged(visibility: Int) {
                super.onWindowVisibilityChanged(visibility)
                if (visibility != View.VISIBLE) media?.setPlayback(false) else updatePlayback()
            }
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                updatePlayback()
                if (hasWindowFocus) {
                    retryMissingAvatars()
                    if (missingAvatars.isNotEmpty()) loadedAt = 0L
                    safeRender()
                }
            }
        }.apply { orientation = LinearLayout.VERTICAL }
        private var week = LocalDate.now()
        private var today = week
        private var media: SidePanelMediaView? = null
        private var loadedConfig = ""
        private var refreshing = false
        private var renderQueued = false
        private var renderedSignature: String? = null
        private var playbackFrame: View? = null
        private var photoHolder: View? = null
        private var photoSignature: String? = null
        private val mediaVisibleRect = android.graphics.Rect()
        private fun updatePlayback() {
            val frame = playbackFrame
            media?.ensurePlayback(frame != null && frame.isShown && frame.hasWindowFocus() && frame.getGlobalVisibleRect(mediaVisibleRect))
        }
        private var scrollObserver: android.view.ViewTreeObserver? = null
        private var scrollListener: android.view.ViewTreeObserver.OnScrollChangedListener? = null
        fun dispose() { clearMedia() }
        private fun clearMedia() {
            scrollListener?.let { callback -> scrollObserver?.takeIf { it.isAlive }?.removeOnScrollChangedListener(callback) }
            scrollListener = null; scrollObserver = null; playbackFrame = null
            media?.dispose(); media = null
            photoHolder = null; photoSignature = null
        }
        fun invalidateContacts() { loadedAt = 0L; safeRender() }
        private var loading = false
        private var cache = emptyList<Contact>()
        private var contactsPanel: View? = null
        private var contactsGeneration = 0
        private val activeAvatarTargets = mutableSetOf<ImageView>()
        private val missingAvatars = mutableSetOf<String>()
        private val avatarTargets = mutableMapOf<String, Pair<WeakReference<ImageView>, List<String>>>()
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
            setPadding(dp(if(compactCard) 6 else 14), dp(10), dp(if(compactCard) 6 else 14), dp(10))
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
                    val started = SystemClock.uptimeMillis()
                    try { render() } catch (error: Throwable) { HLog.e("[Hchat:HomeExtension] 刷新失败", error) }
                    finally {
                        refreshing = false
                        val elapsed = SystemClock.uptimeMillis() - started
                        if (elapsed > 100) HLog.e("[Hchat:HomeExtension] 首页刷新耗时=${elapsed}ms")
                    }
                }
            }
        }
        private fun render() {
            if (destroyed) return
            val date = LocalDate.now()
            if (today != date) { today = date; week = date }
            val signature = listOf(prefs.all.filterKeys { it !in musicDataKeys }.toSortedMap().toString(), today.toString(),
                (if (prefs.getBoolean(HomeExtensionSettings.WEATHER, false)) System.currentTimeMillis() / (30*60_000L) else 0L).toString(),
                ctx.resources.configuration.uiMode.toString(),
                HomeSidePanelSettings.preferences(ctx).getLong(SidePanelAppearance.photoRevision(HomeExtensionSettings.PHOTO_SLOT), 0L).toString()).joinToString("|")
            if (signature == renderedSignature) {
                updatePlayback()
                refreshContactsIfNeeded()
                return
            }
            contactsGeneration++
            root.removeAllViews()
            contactsPanel = null
            val enabled = prefs.getBoolean(HomeExtensionSettings.ENABLE, false)
            val calendar = enabled && prefs.getBoolean(HomeExtensionSettings.CALENDAR, true)
            val recent = enabled && (prefs.getBoolean(HomeExtensionSettings.FAVORITES, false) || prefs.getBoolean(HomeExtensionSettings.RECENT, true))
            val photo = enabled && prefs.getBoolean(HomeExtensionSettings.PHOTO, false)
            val weather = enabled && prefs.getBoolean(HomeExtensionSettings.WEATHER, false)
            val music = enabled && prefs.getBoolean(HomeExtensionSettings.MUSIC, false)
            if (!photo) clearMedia()
            root.visibility = if (calendar || recent || photo || weather || music) View.VISIBLE else View.GONE
            root.layoutParams = AbsListView.LayoutParams(-1, if (calendar || recent || photo || weather || music) -2 else 0)
            val active = setOfNotNull(if(calendar) "calendar" else null, if(recent) "contacts" else null,
                if(photo) "photo" else null, if(weather) "weather" else null, if(music) "music" else null)
            val order = HomeExtensionSettings.order(prefs.getString(HomeExtensionSettings.ORDER, null))
            val custom = prefs.getBoolean(HomeExtensionSettings.CUSTOM_LAYOUT, false)
            val rows = if(custom) HomeCardLayout.rows(prefs.getString(HomeExtensionSettings.LAYOUT_ROWS, null), order)
                else order.map { listOf(it) }
            rows.map { it.filter { id -> id in active } }.filter { it.isNotEmpty() }.forEach { row ->
                val cards = row.map { id ->
                    compactCard = row.size == 2
                    when(id) {
                        "calendar" -> renderCalendar()
                        "contacts" -> renderRecent()
                        "photo" -> renderPhoto()
                        "weather" -> root.addView(HomeWeather.card(ctx, compactCard))
                        "music" -> root.addView(HomeMusic.card(ctx, compactCard))
                    }
                    root.getChildAt(root.childCount - 1).also { root.removeView(it) }
                }
                if(cards.size == 1) {
                    cards[0].layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                        if(row[0] != "photo") setMargins(dp(12), dp(5), dp(12), dp(5))
                    }
                    root.addView(cards[0])
                }
                else {
                    val line = HomeCardRow(ctx).apply {
                        setPadding(dp(12), 0, dp(12), 0)
                        layoutParams = LinearLayout.LayoutParams(-1, -2)
                    }
                    cards.forEachIndexed { index, view ->
                        line.addView(view, LinearLayout.LayoutParams(0, -2, 1f).apply {
                            setMargins(if(index == 0) 0 else dp(5), dp(5), if(index == 0) dp(5) else 0, dp(5))
                        })
                    }
                    root.addView(line)
                }
            }
            compactCard = false
            renderedSignature = signature
            refreshContactsIfNeeded()
        }
        private fun refreshContactsIfNeeded() {
            if (!prefs.getBoolean(HomeExtensionSettings.ENABLE, false)) return
            if (!prefs.getBoolean(HomeExtensionSettings.FAVORITES, false) && !prefs.getBoolean(HomeExtensionSettings.RECENT, true)) return
            val config = prefs.getBoolean(HomeExtensionSettings.FAVORITES, false).toString() + prefs.getString(HomeExtensionSettings.FAVORITE_IDS, "")
            if (!loading && (config != loadedConfig || System.currentTimeMillis() - loadedAt > 30_000)) loadContacts(config)
        }
        private var compactCard = false
        private fun renderCalendar() {
            val panel = card(HomeExtensionSettings.CALENDAR_ALPHA).apply { clipChildren = false; clipToPadding = false }
            // Decode/cache on the existing worker. Each callback belongs to this
            // panel only: replaced/removed panels must not receive stale results.
            val backgroundRevision = prefs.getLong(CalendarBackground.REVISION, 0L)
            worker.execute {
                val bitmap = CalendarBackground.load(ctx)
                if (bitmap != null) main.post {
                    if (!destroyed && panel.parent != null &&
                        backgroundRevision == prefs.getLong(CalendarBackground.REVISION, 0L)) {
                        panel.background = CalendarBackgroundDrawable(bitmap, dp(18).toFloat(),
                            prefs.getInt(HomeExtensionSettings.CALENDAR_ALPHA, 75).coerceIn(0, 100) * 255 / 100)
                    }
                }
            }
            val title = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL; if(compactCard) orientation = LinearLayout.VERTICAL }
            title.addView(text(week.format(DateTimeFormatter.ofPattern("yyyy年 M月")), if(compactCard) 13f else 18f).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, if(compactCard) LinearLayout.LayoutParams(-1, dp(26)) else LinearLayout.LayoutParams(0, dp(40), 1f))
            title.addView(text(prefs.getString(HomeExtensionSettings.GREETING, HomeExtensionSettings.DEFAULT_GREETING).orEmpty()).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; maxLines = 2 }, if(compactCard) LinearLayout.LayoutParams(-1, dp(32)) else LinearLayout.LayoutParams(0, dp(40), 1.5f))
            panel.addView(title)
            val days = LinearLayout(ctx).apply { clipChildren = false; clipToPadding = false }
            val monday = week.minusDays((week.dayOfWeek.value - 1).toLong())
            repeat(7) { index ->
                val date = monday.plusDays(index.toLong())
                val column = LinearLayout(ctx).apply { clipChildren = false; clipToPadding = false; orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
                column.addView(text(listOf("一", "二", "三", "四", "五", "六", "日")[index], 12f), LinearLayout.LayoutParams(-1, dp(24)))
                val dayView = if (date == today) CalendarTodayView(ctx,
                    prefs.getBoolean(HomeExtensionSettings.CALENDAR_EFFECT, false),
                    prefs.getInt(HomeExtensionSettings.CALENDAR_EFFECT_STYLE, 0).coerceIn(0, 2)
                ).apply {
                    text = date.dayOfMonth.toString(); textSize = if(compactCard) 14f else 19f
                    gravity = Gravity.CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                } else text(date.dayOfMonth.toString(), if(compactCard) 14f else 19f)
                column.addView(dayView.apply {
                    contentDescription = date.toString()
                    if (date == today) {
                        setTextColor(Color.WHITE)
                        background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(0xFF07A86A.toInt()) }
                    }
                }, LinearLayout.LayoutParams(if(compactCard) -1 else dp(36), dp(36)))
                val lunar = android.icu.util.ChineseCalendar().apply { timeInMillis = date.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
                val day = lunar.get(android.icu.util.Calendar.DAY_OF_MONTH)
                val numbers = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
                val label = when(day) { 10 -> "初十"; 20 -> "二十"; 30 -> "三十"; else -> listOf("初", "十", "廿")[(day - 1) / 10] + numbers[(day - 1) % 10] }
                column.addView(text(label, if(compactCard) 9f else 11f), LinearLayout.LayoutParams(-1, dp(24)))
                days.addView(column, LinearLayout.LayoutParams(0, dp(90), 1f))
            }
            panel.addView(days)
            root.addView(panel)
        }
        private fun renderRecent() {
            missingAvatars.clear(); avatarTargets.clear(); activeAvatarTargets.clear()
            val panel = card(HomeExtensionSettings.RECENT_ALPHA)
            val config = prefs.getBoolean(HomeExtensionSettings.FAVORITES, false).toString() + prefs.getString(HomeExtensionSettings.FAVORITE_IDS, "")
            val contacts = if (loadedConfig == config) cache.take(8) else emptyList()
            if (contacts.isEmpty()) panel.addView(text(if (loadedAt == 0L) "正在加载最近会话…" else "暂无最近会话"), LinearLayout.LayoutParams(-1, dp(70)))
            else {
                val orbitEnabled = prefs.getBoolean(HomeExtensionSettings.AVATAR_ORBIT, true)
                val row = if (orbitEnabled) ContactOrbitRow(ctx) else LinearLayout(ctx)
                val token = ++contactsGeneration
                contacts.forEach { contact ->
                    val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(3), dp(5), dp(3), dp(4)) }
                    val avatar = ImageView(ctx).apply {
                        contentDescription = contact.name
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0xFF8BA59D.toInt()) }
                        clipToOutline = true
                        setImageResource(android.R.drawable.sym_def_app_icon)
                    }
                    if (row is ContactOrbitRow) column.addView(row.wrap(avatar), LinearLayout.LayoutParams(dp(row.avatarExtentDp), dp(row.avatarExtentDp)))
                    else column.addView(avatar, LinearLayout.LayoutParams(dp(46), dp(46)))
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
                    row.addView(column, LinearLayout.LayoutParams(dp(if (row is ContactOrbitRow) maxOf(72, row.avatarExtentDp + 6) else 72), -2))
                    val urls = listOf(contact.avatar, contact.fallbackAvatar).filter { it.isNotBlank() }.distinct()
                    avatarTargets[contact.id] = WeakReference(avatar) to urls
                    if (urls.isNotEmpty()) loadAvatar(urls.first(), avatar, token, fallback = urls.getOrNull(1))
                    else missingAvatars += contact.id
                }
                panel.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(row) })
            }
            contactsPanel = panel
            root.addView(panel)
        }
        private fun renderPhoto() {
            val file = SidePanelImages.photoFile(ctx, HomeExtensionSettings.PHOTO_SLOT)
            if (file == null) { clearMedia(); root.addView(text("请在首页扩展中添加照片或视频"), LinearLayout.LayoutParams(-1, dp(70))); return }
            val revision = HomeSidePanelSettings.preferences(ctx).getLong(SidePanelAppearance.photoRevision(HomeExtensionSettings.PHOTO_SLOT), 0L)
            val signature = listOf(compactCard.toString(), file.path, revision.toString(), prefs.getInt(HomeExtensionSettings.PHOTO_WIDTH, 100).toString(),
                prefs.getInt(HomeExtensionSettings.PHOTO_HEIGHT, 180).toString(), prefs.getInt(HomeExtensionSettings.PHOTO_ALPHA, 100).toString()).joinToString("|")
            if (photoSignature == signature && photoHolder != null) {
                (photoHolder?.parent as? android.view.ViewGroup)?.removeView(photoHolder)
                root.addView(photoHolder)
                root.post { updatePlayback() }
                return
            }
            clearMedia()
            val view = SidePanelMediaView(ctx, recoverPlayback = true, adjustablePhoto = true)
            media = view
            val frame = FrameLayout(ctx).apply {
                background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.TRANSPARENT) }
                clipToOutline = true
                addView(view, FrameLayout.LayoutParams(-1, -1))
                alpha = prefs.getInt(HomeExtensionSettings.PHOTO_ALPHA, 100).coerceIn(0, 100) / 100f
            }
            val width = prefs.getInt(HomeExtensionSettings.PHOTO_WIDTH, 100).coerceIn(30, 100)
            val holder = LinearLayout(ctx).apply { gravity = Gravity.CENTER; setPadding(dp(if(compactCard) 0 else 12), dp(if(compactCard) 0 else 5), dp(if(compactCard) 0 else 12), dp(if(compactCard) 0 else 5)) }
            holder.addView(frame, LinearLayout.LayoutParams(0, dp(prefs.getInt(HomeExtensionSettings.PHOTO_HEIGHT, 180).coerceIn(80, 500)), width.toFloat()))
            if (width < 100) { holder.weightSum = 100f }
            photoHolder = holder
            photoSignature = signature
            root.addView(holder)
            view.load(file, HomeSidePanelSettings.preferences(ctx).getLong(SidePanelAppearance.photoRevision(HomeExtensionSettings.PHOTO_SLOT), 0L))
            playbackFrame = frame
            fun playback() { runCatching { updatePlayback() }.onFailure { HLog.e("[Hchat:HomeExtension] 媒体播放状态更新失败", it) } }
            val callback = android.view.ViewTreeObserver.OnScrollChangedListener { playback() }
            scrollObserver = root.viewTreeObserver
            scrollListener = callback
            scrollObserver?.addOnScrollChangedListener(callback)
            frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> playback() }
            frame.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { playback() }
                override fun onViewDetachedFromWindow(v: View) { main.post { if (!frame.isAttachedToWindow) view.setPlayback(false) } }
            })
            frame.post { playback() }
        }
        private fun loadContacts(config: String) {
            loading = true
            worker.execute {
                val result = runCatching {
                    val contacts = WeChatApis.contacts() ?: error("联系人 API 未就绪")
                    val conversations = WeChatApis.conversations() ?: error("会话 API 未就绪")
                    val favorites = config.startsWith("true")
                    val ids = if (favorites) HomeExtensionSettings.favorites(config.removePrefix("true")) else conversations.getRecentConversationUsernames(200)
                    ids.asSequence().filter { favorites || contacts.isGroup(it) || contacts.isFriend(it) }.take(8).map { id ->
                        val group = contacts.isGroup(id)
                        Contact(id, contacts.getDisplayName(id).orEmpty().ifBlank { id }, contacts.getAvatarUrl(id, false).orEmpty(), group, contacts.getAvatarUrl(id, true).orEmpty())
                    }.toList()
                }
                if (!destroyed) main.post {
                    loading = false
                    val currentConfig = prefs.getBoolean(HomeExtensionSettings.FAVORITES, false).toString() + prefs.getString(HomeExtensionSettings.FAVORITE_IDS, "")
                    if (currentConfig != config) { refreshContactsIfNeeded(); return@post }
                    result.onSuccess { next ->
                        val changed = next != cache || loadedConfig != config
                        cache = next; loadedConfig = config; loadedAt = System.currentTimeMillis()
                        if (changed) updateContactsPanel() else retryMissingAvatars()
                    }.onFailure {
                        // Keep the last good list; allow another attempt after a short backoff.
                        loadedAt = System.currentTimeMillis() - 25_000L
                        HLog.e("[Hchat:HomeExtension] 联系人刷新失败，保留缓存", it)
                    }
                }
            }
        }
        private fun updateContactsPanel() {
            val old = contactsPanel ?: return
            val parent = old.parent as? android.view.ViewGroup ?: return
            val index = parent.indexOfChild(old)
            val params = old.layoutParams
            val previousCompact = compactCard
            compactCard = parent is HomeCardRow
            try {
                renderRecent()
                val replacement = contactsPanel ?: return
                root.removeView(replacement)
                parent.removeView(old)
                parent.addView(replacement, index, params)
            } finally { compactCard = previousCompact }
        }
        private fun retryMissingAvatars() {
            for (id in missingAvatars.toList()) {
                val entry = avatarTargets[id] ?: continue
                val view = entry.first.get() ?: continue
                val urls = entry.second
                if (urls.isNotEmpty()) loadAvatar(urls.first(), view, contactsGeneration, fallback = urls.getOrNull(1))
            }
        }
        private fun loadAvatar(url: String, target: ImageView, token: Int, attempt: Int = 0, fallback: String? = null) {
            avatars.get(url)?.let { target.setImageBitmap(it); missingAvatars.removeAll { avatarTargets[it]?.first?.get() === target }; return }
            if (!activeAvatarTargets.add(target)) return
            val id = avatarTargets.entries.firstOrNull { it.value.first.get() === target }?.key
            if (id != null) missingAvatars.add(id)
            val ref = WeakReference(target)
            requestAvatar(url) { bitmap ->
                val view = ref.get()
                if (view != null) activeAvatarTargets.remove(view)
                if (!destroyed && contactsGeneration == token && view != null) {
                    if (bitmap != null) { view.setImageBitmap(bitmap); if (id != null) missingAvatars.remove(id) }
                    else if (fallback != null) loadAvatar(fallback, view, token, attempt)
                    else if (attempt < 2) {
                        activeAvatarTargets.add(view)
                        main.postDelayed({
                            ref.get()?.let {
                                activeAvatarTargets.remove(it)
                                if (!destroyed && contactsGeneration == token) loadAvatar(url, it, token, attempt + 1)
                            }
                        }, (attempt + 1) * 2000L)
                    }
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
