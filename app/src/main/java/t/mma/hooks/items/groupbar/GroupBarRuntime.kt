package t.mma.hooks.items.groupbar

import android.database.DataSetObserver
import android.app.Activity
import android.content.SharedPreferences
import t.mma.hooks.items.homeextension.HomeExtensionSurface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.runtime.WeChatDatabaseListenerApi
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.utils.HLog
import t.mma.utils.KavaReflector
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap
import java.util.concurrent.Executors

/** Shared native MVVM data source for both 8.0.78 homepage list implementations. */
internal class GroupBarRuntime(private val context: FeatureContext) {
    private val prefs = GroupBarStore.prefs(context.hostContext())
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val hooks = ArrayList<XC_MethodHook.Unhook>()
    private val sessions = WeakHashMap<View, Session>()
    private val sources = java.util.Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    private var homepageSourceTypes = emptyList<Class<*>>()
    private fun isHomepageSource(value: Any): Boolean = sources.containsKey(value) || homepageSourceTypes.any { it.isInstance(value) }
    private val pending = java.util.Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private var groups = GroupBarStore.load(context.hostContext())
    @Volatile private var enabled = prefs.getBoolean(GroupBarStore.ENABLE, false)
    @Volatile private var ready = false
    private var handoff = 0
    private var selected = "all"
    private var friends = emptySet<String>()
    private var officials = emptySet<String>()
    @Volatile private var filter = GroupBarFilter(null)
    @Volatile private var destroyed = false
    private var loadingContacts = false
    private var contactsLoaded = false
    private var installed = false
    private var dataDirty = false
    private val reloadQueued = java.util.concurrent.atomic.AtomicBoolean(false)
    private var querySubscription: WeChatDatabaseListenerApi.QuerySubscription? = null
    private var changes: t.mma.hooks.api.conversation.WeChatConversationChangeApi.Subscription? = null
    // A query sees one immutable selection, even if the user switches during background loading.
    private val queryScope = ThreadLocal<GroupBarFilter>()
    private val sqlCache = object : LinkedHashMap<Pair<GroupBarFilter, String>, String>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<GroupBarFilter, String>, String>?) = size > 64
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        main.post {
            if (!destroyed && key != GroupBarStore.NEXT) {
                if (key == null || key == GroupBarStore.ENABLE || key == GroupBarStore.GROUPS) dataDirty = true
                main.removeCallbacks(refresh); main.post(refresh)
            }
        }
    }
    private val refresh = Runnable {
        if (!destroyed) {
            val changed = dataDirty; dataDirty = false
            val wasEnabled = enabled
            enabled = prefs.getBoolean(GroupBarStore.ENABLE, false)
            groups = GroupBarStore.load(context.hostContext())
            if (!enabled) { ready = false; handoff++ }
            if (changed) updateFilter()
            if (enabled && !wasEnabled) prepareHandoff()
            if (enabled) loadContacts()
            sessions.values.toList().forEach { it.render() }
            if (changed && (enabled || wasEnabled) && !(enabled && !wasEnabled)) reload()
        }
    }
    private fun updateFilter() {
        if (groups.none { it.enabled && it.id == selected }) selected = groups.firstOrNull { it.enabled }?.id ?: "all"
        filter = GroupBarFilter(if (enabled && ready) groups.firstOrNull { it.enabled && it.id == selected } else null, friends, officials)
        synchronized(sqlCache) { sqlCache.clear() }
    }
    private fun prepareHandoff() {
        ready = false; updateFilter()
        val token = ++handoff
        t.mma.hooks.items.conversationgroup.ConversationGroupRuntime.afterDisabledRestore(context.hostContext()) { success ->
            if (!destroyed && enabled && token == handoff) {
                ready = success; updateFilter()
                if (success) reload() else {
                    prefs.edit().putBoolean(GroupBarStore.ENABLE, false).apply()
                    HLog.e("[Hchat:GroupBar] 未完成聊天分组恢复，已关闭横栏", IllegalStateException("restore failed"))
                }
            }
        }
    }
    private fun select(id: String) {
        selected = id; updateFilter()
        sessions.values.toList().forEach { it.switchGroup() }
    }
    private fun reload() { sessions.values.toList().forEach { it.reload() } }
    fun install(): Boolean {
        try {
            val host = context.hostContext()
            if (host.packageManager.getPackageInfo(host.packageName, 0).versionName != "8.0.78") return true
            val loader = context.hostClassLoader()
            fun type(name: String) = Class.forName(name, false, loader)
            val source = type("jo5.e")
            val row = type("jo5.z")
            val conversation = KavaReflector.findFieldRecursive(row, "d")!!.apply { isAccessible = true }
            val user = KavaReflector.findFieldRecursive(type("com.tencent.mm.storage.k4"), "field_username")!!.apply { isAccessible = true }
            val unread = KavaReflector.findFieldRecursive(type("com.tencent.mm.storage.k4"), "field_unReadCount")!!.apply { isAccessible = true }
            val parent = KavaReflector.findFieldRecursive(type("com.tencent.mm.storage.k4"), "field_parentRef")!!.apply { isAccessible = true }
            val load = source.getDeclaredMethod("g", type("jo5.m0"))
            val membership = source.getDeclaredMethod("l", row)
            load.isAccessible = true; membership.isAccessible = true
            check(load.returnType.name == "jo5.n0" && membership.returnType == Boolean::class.javaPrimitiveType)
            // Constructors identify only the two verified homepage data sources, excluding forward pickers.
            homepageSourceTypes = listOf("jo5.y", "po5.l").map(::type)
            for (sourceType in homepageSourceTypes) for (ctor in sourceType.declaredConstructors) {
                hooks += HookRegistry.get().hook(ctor, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) { sources[param.thisObject] = true }
                })
            }
            hooks += HookRegistry.get().hook(load, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!destroyed && enabled && ready && isHomepageSource(param.thisObject)) queryScope.set(filter)
                }
                override fun afterHookedMethod(param: MethodHookParam) { queryScope.remove() }
            })
            // RecyclerView incremental add/update uses this same native parent-membership gate.
            hooks += HookRegistry.get().hook(membership, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (destroyed || !enabled || !ready || !isHomepageSource(param.thisObject)) return
                    val item = param.args.firstOrNull() ?: return
                    runCatching {
                        val native = conversation.get(item) ?: return
                        param.result = filter.acceptsNativeParent(param.result == true, parent.get(native) as? String) &&
                            filter.accepts(user.get(native) as? String ?: "", unread.getInt(native))
                    }.onFailure { HLog.e("[Hchat:GroupBar] 增量筛选失败，保留原生结果", it) }
                }
            })
            val database = WeChatApis.databaseChanges() ?: error("Database listener unavailable")
            database.install()
            querySubscription = database.subscribeQuery { sql ->
                val snapshot = queryScope.get()
                if (destroyed || snapshot == null) sql else synchronized(sqlCache) {
                    sqlCache.getOrPut(snapshot to sql) { GroupBarSql.rewrite(sql, snapshot.predicate()) }
                }
            }
            fun hookAttach(owner: Class<*>, name: String, vararg args: Class<*>) {
                val method = KavaReflector.findMethodRecursive(owner, name, *args) ?: return
                hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as? View ?: return
                        if (view.javaClass.name !in setOf("com.tencent.mm.ui.conversation.ConversationListView", "com.tencent.mm.ui.conversation.recycler.ConversationRecyclerView")) return
                        if (pending.add(view)) view.post { pending.remove(view); if (!destroyed) attach(view) }
                    }
                })
            }
            val list = type("com.tencent.mm.ui.conversation.ConversationListView")
            hookAttach(list, "setAdapter", ListAdapter::class.java); hookAttach(list, "onAttachedToWindow")
            fun nativeListHook(owner: Class<*>, name: String, vararg args: Class<*>, before: (Session) -> Unit = {}, after: (Session) -> Unit = {}) {
                val method = KavaReflector.findMethodRecursive(owner, name, *args) ?: error("Missing native list method: $name")
                hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        sessions[param.thisObject as? View]?.let { session ->
                            runCatching { before(session) }.onFailure { HLog.e("[Hchat:GroupBar] 原生前置协调失败: $name", it) }
                        }
                    }
                    override fun afterHookedMethod(param: MethodHookParam) {
                        sessions[param.thisObject as? View]?.let { session ->
                            runCatching { after(session) }.onFailure { HLog.e("[Hchat:GroupBar] 原生后置协调失败: $name", it) }
                        }
                    }
                })
            }
            nativeListHook(list, "C", before = { it.beforeNativeOffset() }, after = { it.afterNativeOffset() })
            nativeListHook(list, "o", after = { it.headerState(true) })
            nativeListHook(list, "q", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, after = { it.headerState(false) })
            // Constant-time state reads only; no view search, layout mutation, SQL or reload on scroll.

            nativeListHook(list, "onScroll", android.widget.AbsListView::class.java, Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, after = { it.syncHeaderVisibility() })
            val recycler = type("com.tencent.mm.ui.conversation.recycler.ConversationRecyclerView")
            hookAttach(recycler, "setAdapter", type("androidx.recyclerview.widget.f2")); hookAttach(recycler, "onAttachedToWindow")
            nativeListHook(recycler, "y1", before = { it.beforeNativeOffset() }, after = { it.afterNativeOffset() })
            nativeListHook(recycler, "o", after = { it.headerState(true) })
            nativeListHook(recycler, "q", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, after = { it.headerState(false) })
            nativeListHook(recycler, "u1", after = { it.syncHeaderVisibility() })
            for (owner in listOf(list, recycler)) {
                val dispatch = KavaReflector.findMethodRecursive(owner, "dispatchTouchEvent", android.view.MotionEvent::class.java)
                    ?: error("Missing homepage dispatchTouchEvent")
                hooks += HookRegistry.get().hook(dispatch, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val event = param.args.firstOrNull() as? android.view.MotionEvent ?: return
                        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN)
                            sessions[param.thisObject as? View]?.userTouchStarted()
                    }
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val event = param.args.firstOrNull() as? android.view.MotionEvent ?: return
                        if (event.actionMasked == android.view.MotionEvent.ACTION_UP || event.actionMasked == android.view.MotionEvent.ACTION_CANCEL)
                            sessions[param.thisObject as? View]?.userTouchEnded()
                    }
                })
            }
            val launcher = type("com.tencent.mm.ui.LauncherUI")
            KavaReflector.findMethodRecursive(launcher, "onResume")?.let { method ->
                hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!destroyed) main.post {
                            if (!destroyed) {
                                discoverHomepage(param.thisObject as? Activity)
                                if (enabled) loadContacts()
                            }
                        }
                    }
                })
            }
            changes = WeChatApis.conversationChanges()?.subscribe {
                // Native MVVM owns incremental notifications. Re-evaluate only selected unread/custom rows
                // via its normal reload, debounced per burst, not per scroll/bind.
                if (enabled && ready && !destroyed && filter.group?.id != null && filter.group?.id != "all") {
                    if (reloadQueued.compareAndSet(false, true)) main.postDelayed(reloadChanged, 120)
                }
            }
            // Native MainUI changes title/multiwindow insets even when list bounds do not change.
            val mainUi = type("com.tencent.mm.ui.conversation.MainUI")
            KavaReflector.findMethodRecursive(mainUi, "onResume")?.let { method ->
                hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        main.post { if (!destroyed) discoverHomepage(WeChatApis.currentActivity()?.currentActivity()) }
                    }
                })
            }
            KavaReflector.findMethodRecursive(mainUi, "F0", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)?.let { method ->
                hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!destroyed) main.post {
                            if (!destroyed) sessions.values.toList().forEach { it.nativeInsetChanged() }
                        }
                    }
                })
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            installed = true; updateFilter()
            if (enabled) { prepareHandoff(); loadContacts() }
            // BRIDGE installation can happen after setAdapter/onAttachedToWindow already ran.
            main.post { if (!destroyed) discoverHomepage(WeChatApis.currentActivity()?.currentActivity()) }
            return true
        } catch (error: Throwable) {
            HLog.e("[Hchat:GroupBar] 安装失败", error); destroy(); return false
        }
    }
    private val reloadChanged = Runnable { reloadQueued.set(false); if (!destroyed && enabled && ready) reload() }
    private fun discoverHomepage(activity: Activity?) {
        if (destroyed || activity == null || activity.javaClass.name != "com.tencent.mm.ui.LauncherUI") return
        val root = activity.window?.decorView ?: return
        var found = 0
        fun visit(view: View) {
            if (view.javaClass.name in setOf("com.tencent.mm.ui.conversation.ConversationListView", "com.tencent.mm.ui.conversation.recycler.ConversationRecyclerView")) {
                found++; attach(view)
                return
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
        if (found == 0) HLog.d("[Hchat:GroupBar] 当前首页尚无会话列表，等待原生挂载事件")
    }
    private fun attach(view: View) {
        if (!view.isAttachedToWindow || destroyed) return
        runCatching {
            val parent = view.parent as? RelativeLayout
            if (parent == null) {
                HLog.w("[Hchat:GroupBar] 首页容器不匹配: list=${view.javaClass.name} parent=${view.parent?.javaClass?.name}")
                return
            }
            var adapter: Any = if (view is ListView) view.adapter ?: return else
                KavaReflector.findMethodRecursive(view.javaClass, "getAdapter")?.invoke(view) ?: return
            while (adapter is HeaderViewListAdapter) adapter = adapter.wrappedAdapter ?: return
            val reset = KavaReflector.findMethodRecursive(adapter.javaClass, "S2")
            if (reset == null || reset.returnType != Void.TYPE) {
                HLog.w("[Hchat:GroupBar] 首页适配器无重载入口: ${adapter.javaClass.name}")
                return
            }
            reset.isAccessible = true
            sessions[view]?.let { if (it.adapter === adapter) { it.refreshPosition(); return } else it.dispose() }
            val session = Session(view, parent, adapter, reset)
            sessions[view] = session; session.render(); if (enabled) { session.reload() }
            HLog.i("[Hchat:GroupBar] 横栏已挂载: list=${view.javaClass.name} parent=${parent.javaClass.name} adapter=${adapter.javaClass.name} enabled=$enabled priority=title_first")
        }.onFailure { HLog.e("[Hchat:GroupBar] 挂载失败", it) }
    }
    private fun loadContacts() {
        if (loadingContacts || contactsLoaded || destroyed) return
        loadingContacts = true
        worker.execute {
            val result = runCatching {
                val contacts = WeChatApis.contact().contacts() ?: error("Contacts not ready")
                contacts.getPickerContacts().mapTo(HashSet()) { it.wxId } to contacts.getPickerOfficialAccounts().mapTo(HashSet()) { it.wxId }
            }
            main.post {
                loadingContacts = false
                if (!destroyed) result.onSuccess { (a, b) ->
                    val changed = friends != a || officials != b
                    friends = a; officials = b; contactsLoaded = true
                    if (changed) { updateFilter(); if (enabled) reload() }
                }
            }
        }
    }
    private inner class Session(view: View, parent: RelativeLayout, val adapter: Any, private val reset: Method) {
        private val viewRef = WeakReference(view)
        private val parentRef = WeakReference(parent)
        private var nativeScrollOffset: Int? = null
        private var appliedScrollInset = 0
        private val isNativeList = view is ListView
        private val scrollField = KavaReflector.findFieldRecursive(view.javaClass, if (isNativeList) "s" else "v2")!!.apply { isAccessible = true }
        private val controllerField = KavaReflector.findFieldRecursive(view.javaClass, if (isNativeList) "m" else "Z2")!!.apply { isAccessible = true }
        private val openField = KavaReflector.findFieldRecursive(view.javaClass, if (isNativeList) "x" else "d3")!!.apply { isAccessible = true }
        private val reserveHeight = (44 * view.resources.displayMetrics.density + .5f).toInt()
        private var hasVisibleGroups = false
        private var nativeOffsetUpdating = false
        private var headerOpen = false
        // MainUI places its list below the title using a dynamic top margin.
        private var nativeTopMargin = (view.layoutParams as RelativeLayout.LayoutParams).topMargin
        private var floatingInset = 0
        private var titleAnchor: View? = null
        private val titlePosition = IntArray(2)
        private val parentPosition = IntArray(2)
        private val titleLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!disposed && bar.visibility == View.VISIBLE) safePosition()
        }
        private val positionListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!disposed && bar.visibility == View.VISIBLE) safePosition()
        }
        private val bar = object : HorizontalScrollView(view.context), HomeExtensionSurface.Root {
            private var down: android.view.MotionEvent? = null
            private var vertical = false
            private val slop = android.view.ViewConfiguration.get(view.context).scaledTouchSlop
            private val fromLocation = IntArray(2)
            private val toLocation = IntArray(2)
            private fun forward(event: android.view.MotionEvent): Boolean {
                val list = viewRef.get() ?: return false
                getLocationOnScreen(fromLocation); list.getLocationOnScreen(toLocation)
                val copy = android.view.MotionEvent.obtain(event)
                return try {
                    copy.offsetLocation((fromLocation[0] - toLocation[0]).toFloat(), (fromLocation[1] - toLocation[1]).toFloat())
                    list.dispatchTouchEvent(copy)
                } finally { copy.recycle() }
            }
            override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        down?.recycle(); down = android.view.MotionEvent.obtain(event); vertical = false
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val start = down
                        if (!vertical && start != null && kotlin.math.abs(event.y - start.y) > slop &&
                            kotlin.math.abs(event.y - start.y) > kotlin.math.abs(event.x - start.x)) {
                            val cancel = android.view.MotionEvent.obtain(event)
                            cancel.action = android.view.MotionEvent.ACTION_CANCEL
                            try { super.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
                            vertical = true
                            parent.requestDisallowInterceptTouchEvent(true)
                            forward(start)
                        }
                    }
                }
                val handled = if (vertical) { forward(event); true } else super.dispatchTouchEvent(event)
                if (event.actionMasked == android.view.MotionEvent.ACTION_UP || event.actionMasked == android.view.MotionEvent.ACTION_CANCEL) {
                    down?.recycle(); down = null; vertical = false
                    parent.requestDisallowInterceptTouchEvent(false)
                }
                return handled
            }
            override fun onDetachedFromWindow() {
                down?.recycle(); down = null; vertical = false
                super.onDetachedFromWindow()
            }
        }.apply {
            id = View.generateViewId(); isHorizontalScrollBarEnabled = false; isFillViewport = true
        }
        private val strip = LinearLayout(view.context).apply { gravity = Gravity.CENTER_VERTICAL }
        private val layoutListener = View.OnLayoutChangeListener { _, l, _, r, _, oldL, _, oldR, _ -> if (r-l != oldR-oldL) resize(r-l) }
        private var disposed = false
        private var pendingSwitchReset = false
        private var userInteracting = false
        private var reloadDeferred = false
        private val drainReload = Runnable {
            if (!disposed && !userInteracting && !nativeHeaderOpen() && reloadDeferred) {
                reloadDeferred = false
                reload()
            }
        }
        private val resetAfterData = Runnable {
            if (!disposed && enabled && pendingSwitchReset && !userInteracting && !nativeHeaderOpen()) {
                pendingSwitchReset = false
                viewRef.get()?.let { selectNativeTop(it) }
            }
        }
        private val switchObserver = object : DataSetObserver() {
            override fun onChanged() {
                if (pendingSwitchReset && !userInteracting) {
                    main.removeCallbacks(resetAfterData)
                    main.post(resetAfterData)
                }
            }
        }
        private val detachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                // Removing a sibling while ViewGroup dispatches detach corrupts its child loop.
                main.post { if (!v.isAttachedToWindow) { dispose(); if (sessions[v] === this@Session) sessions.remove(v) } }
            }
        }
        init {
            (adapter as? ListAdapter)?.registerDataSetObserver(switchObserver)
            bar.addView(strip, ViewGroup.LayoutParams(-2, -1))
            parent.addView(bar, RelativeLayout.LayoutParams(-1, dp(44)))
            bar.addOnLayoutChangeListener(layoutListener); view.addOnAttachStateChangeListener(detachListener)
            view.addOnLayoutChangeListener(positionListener)
            // HomeSidePanel verifies hb in Toolbar and ez/ih as the 8.0.78 title hosts.
            val decor = view.rootView
            for (name in listOf("ez", "hb", "ih")) {
                val id = view.resources.getIdentifier(name, "id", view.context.packageName)
                val candidate = if (id != 0) decor.findViewById<View>(id) else null
                if (candidate != null && candidate !== parent && candidate !== view) {
                    titleAnchor = candidate
                    candidate.addOnLayoutChangeListener(titleLayoutListener)
                    break
                }
            }
            parent.addOnLayoutChangeListener(titleLayoutListener)
            HLog.i("[Hchat:GroupBar] 标题锚点=${titleAnchor?.javaClass?.name} nativeMargin=$nativeTopMargin")
        }
        fun refreshPosition() { if (!disposed) { safePosition(); syncHeaderVisibility() } }
        fun nativeInsetChanged() {
            val list = viewRef.get() ?: return
            val lp = list.layoutParams as RelativeLayout.LayoutParams
            nativeTopMargin = lp.topMargin
            if (bar.visibility == View.VISIBLE) positionBar()
        }
        fun userTouchStarted() {
            // Every touch event cancels stale programmatic positioning. We do not consume it,
            // install an OnTouchListener, or change native taskbar/bounce state.
            userInteracting = true
            pendingSwitchReset = false
            main.removeCallbacks(resetAfterData)
        }
        fun userTouchEnded() {
            userInteracting = false
            // Post after native dispatch so onOpenHeader state has been committed first.
            main.removeCallbacks(drainReload); main.post(drainReload)
        }
        fun switchGroup() {
            pendingSwitchReset = true
            userInteracting = false
            // The tab is only interactive on the closed homepage. One native selection, no
            // force-close command and no second posted close competing with a new gesture.
            viewRef.get()?.let { if (!nativeHeaderOpen()) selectNativeTop(it) }
            reload()
        }
        private fun nativeHeaderOpen(): Boolean {
            val list = viewRef.get() ?: return headerOpen
            return headerOpen || openField.getBoolean(list)
        }
        private fun selectNativeTop(list: View) {
            if (list is ListView) list.setSelection(0)
            else KavaReflector.findMethodRecursive(list.javaClass, "setSelectionWithOffset", Int::class.javaPrimitiveType!!)?.invoke(list, 0)
        }
        fun reload() {
            if (disposed) return
            if (userInteracting || nativeHeaderOpen()) { reloadDeferred = true; return }
            reloadDeferred = false
            if (!disposed && viewRef.get()?.isAttachedToWindow == true) runCatching { reset.invoke(adapter) }
                .onFailure { HLog.e("[Hchat:GroupBar] 原生重载失败", it) }
        }
        fun render() {
            val list = viewRef.get() ?: return
            hasVisibleGroups = groups.any { it.enabled }
            val visible = enabled && hasVisibleGroups
            bar.visibility = if (visible) View.VISIBLE else View.GONE
            strip.removeAllViews()
            val floating = prefs.getInt(GroupBarStore.STYLE, 0) == 1
            val alpha = ((100 - prefs.getInt(GroupBarStore.ALPHA, 0).coerceIn(0, 100)) * 255 / 100)
            val dark = list.resources.configuration.uiMode and 0x30 == 0x20
            bar.background = GradientDrawable().apply {
                setColor(Color.argb(alpha, if (dark) 32 else 248, if (dark) 32 else 248, if (dark) 32 else 248))
                cornerRadius = if (floating) dp(22).toFloat() else 0f
            }
            val params = bar.layoutParams as RelativeLayout.LayoutParams
            floatingInset = if (floating) dp(2) else 0
            params.setMargins(if (floating) dp(8) else 0, nativeTopMargin + floatingInset, if (floating) dp(8) else 0, 0)
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            bar.layoutParams = params
            if (visible) { positionBar(); bar.bringToFront() }
            val visibleGroups = groups.filter { it.enabled }
            visibleGroups.forEach { group ->
                val text = TextView(list.context).apply {
                    this.text = group.name; textSize = 14f; gravity = Gravity.CENTER; isSingleLine = true
                    setTextColor(if (group.id == selected) 0xff1677ff.toInt() else if (dark) Color.WHITE else Color.DKGRAY)
                    setOnClickListener {
                        if (selected != group.id) {
                            select(group.id)
                            for (i in 0 until strip.childCount) (strip.getChildAt(i) as TextView).setTextColor(
                                if (visibleGroups.getOrNull(i)?.id == selected) 0xff1677ff.toInt() else if (dark) Color.WHITE else Color.DKGRAY)

                        }
                    }
                    setOnLongClickListener {
                        val menu = PopupMenu(list.context, this)
                        menu.menu.add(0, 1, 0, "向左移动"); menu.menu.add(0, 2, 1, "向右移动")
                        menu.setOnMenuItemClickListener { item -> GroupBarStore.move(list.context, group.id, if (item.itemId == 1) -1 else 1); true }
                        menu.show(); true
                    }
                }
                strip.addView(text, LinearLayout.LayoutParams(1, -1))
            }
            applyNativeOffset(visible)
            syncHeaderVisibility()
            resize(bar.width)
        }
        private fun titleTranslation(view: View): Int {
            var current: View? = view
            var translation = 0f
            while (current != null && current !== parentRef.get()) {
                translation += current.translationY
                current = current.parent as? View
            }
            return translation.toInt()
        }
        private fun safePosition() {
            runCatching { positionBar() }.onFailure { HLog.e("[Hchat:GroupBar] 横栏定位失败", it) }
        }
        private fun positionBar() {
            val list = viewRef.get() ?: return
            val lp = list.layoutParams as RelativeLayout.LayoutParams
            // A later native title/inset update is transferred to the bar once.
            nativeTopMargin = lp.topMargin
            val bp = bar.layoutParams as RelativeLayout.LayoutParams
            val parent = parentRef.get() ?: return
            val anchor = titleAnchor
            val titleBottom = if (anchor != null && anchor.isShown && anchor.height > 0) {
                anchor.getLocationInWindow(titlePosition)
                parent.getLocationInWindow(parentPosition)
                (titlePosition[1] + anchor.height - titleTranslation(anchor) - parentPosition[1] - parent.paddingTop).coerceAtLeast(0)
            } else nativeTopMargin
            val top = titleBottom + floatingInset
            if (bp.topMargin != top) { bp.topMargin = top; bar.layoutParams = bp }
        }
        private fun applyNativeOffset(active: Boolean) {
            val list = viewRef.get() ?: return
            if (nativeOffsetUpdating) return
            val current = scrollField.getInt(list)
            val base = nativeScrollOffset ?: current.also { nativeScrollOffset = it }
            val reserve = if (active) reserveHeight + floatingInset else 0
            if (current != base + reserve) scrollField.setInt(list, base + reserve)
            // C() copies s into TaskBarAnimController.n. Both consumers must agree.
            val controller = controllerField.get(list)
            if (controller != null) KavaReflector.findFieldRecursive(controller.javaClass, "n")?.apply {
                isAccessible = true; setInt(controller, base + reserve)
            }
            appliedScrollInset = reserve
        }
        fun beforeNativeOffset() {
            val list = viewRef.get() ?: return
            val base = nativeScrollOffset
            if (base != null) scrollField.setInt(list, base)
            nativeOffsetUpdating = true
        }
        fun afterNativeOffset() {
            val list = viewRef.get() ?: return
            nativeOffsetUpdating = false
            nativeScrollOffset = scrollField.getInt(list)
            applyNativeOffset(enabled && hasVisibleGroups)
            // Native C/y1 already owns orientation/inset positioning. Never force a second
            // selection here: it can run during a pull gesture or TaskBar transition.
        }
        fun headerState(open: Boolean) {
            headerOpen = open
            if (open) {
                pendingSwitchReset = false
                main.removeCallbacks(resetAfterData)
            }
            syncHeaderVisibility()
            if (!open) {
                safePosition()
                main.removeCallbacks(drainReload); main.post(drainReload)
            }
        }
        fun syncHeaderVisibility() {
            // Positive title translation also occurs during a harmless bounce. Only the
            // verified native open state owns visibility; never infer open from distance.
            val open = nativeHeaderOpen()
            val visibility = if (enabled && hasVisibleGroups && !open) View.VISIBLE else View.INVISIBLE
            if (bar.visibility != visibility) {
                bar.visibility = visibility
                if (visibility == View.VISIBLE) safePosition()
            }
        }
        private fun restoreAvoidance() {
            applyNativeOffset(false)
            nativeScrollOffset = null
            appliedScrollInset = 0
        }
        private fun resize(width: Int) {
            if (width <= 0 || strip.childCount == 0) return
            val cell = width / minOf(6, strip.childCount)
            for (i in 0 until strip.childCount) {
                val child = strip.getChildAt(i)
                if (child.layoutParams.width != cell) child.layoutParams = LinearLayout.LayoutParams(cell, -1)
            }
        }
        fun dispose() {
            if (disposed) return
            disposed = true
            pendingSwitchReset = false
            main.removeCallbacks(resetAfterData)
            main.removeCallbacks(drainReload)
            reloadDeferred = false
            (adapter as? ListAdapter)?.unregisterDataSetObserver(switchObserver)
            viewRef.get()?.let { view ->
                view.removeOnAttachStateChangeListener(detachListener)
                view.removeOnLayoutChangeListener(positionListener)
                restoreAvoidance()
            }
            titleAnchor?.removeOnLayoutChangeListener(titleLayoutListener)
            titleAnchor = null
            parentRef.get()?.removeOnLayoutChangeListener(titleLayoutListener)
            bar.removeOnLayoutChangeListener(layoutListener)
            val parent = parentRef.get()
            main.post { if (parent != null && bar.parent === parent) parent.removeView(bar) }
        }
        private fun dp(value: Int) = (value * bar.resources.displayMetrics.density + .5f).toInt()
    }
    fun destroy() {
        destroyed = true; enabled = false; filter = GroupBarFilter(null)
        if (installed) prefs.unregisterOnSharedPreferenceChangeListener(listener)
        querySubscription?.unsubscribe(); changes?.unsubscribe()
        hooks.forEach { HookRegistry.get().unhook(it) }; hooks.clear()
        main.removeCallbacksAndMessages(null)
        sessions.values.toList().forEach { it.reload(); it.dispose() }; sessions.clear(); sources.clear()
        worker.shutdownNow(); synchronized(sqlCache) { sqlCache.clear() }
    }
}
