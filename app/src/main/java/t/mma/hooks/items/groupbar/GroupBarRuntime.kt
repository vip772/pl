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
            val previousMembership = groups.associate { it.id to (it.enabled to it.members) }
            groups = GroupBarStore.load(context.hostContext())
            val membershipChanged = previousMembership != groups.associate { it.id to (it.enabled to it.members) }
            if (!enabled) { ready = false; handoff++ }
            if (changed) updateFilter()
            if (enabled && !wasEnabled) prepareHandoff()
            if (enabled) loadContacts()
            sessions.values.toList().forEach { it.render() }
            if (changed && (membershipChanged || enabled != wasEnabled) && (enabled || wasEnabled) && !(enabled && !wasEnabled)) reload()
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
        sessions.values.toList().forEach { it.beginTrace("switch:$selected->$id") }
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
            // C/y1 invokes TaskBarContainer.q before its own selection. Its animation
            // consumers must see the same reserved offset, not the temporary native base.
            val taskBarResize = KavaReflector.findMethodRecursive(type("com.tencent.mm.plugin.taskbar.ui.TaskBarContainer"), "q", Int::class.javaPrimitiveType!!)
                ?: error("Missing TaskBarContainer.q")
            hooks += HookRegistry.get().hook(taskBarResize, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    sessions.values.forEach { it.beforeTaskBarResize(param.thisObject) }
                }
            })
            nativeListHook(list, "C", before = { it.beforeNativeOffset() }, after = { it.afterNativeOffset() })
            nativeListHook(list, "setSelection", Int::class.javaPrimitiveType!!, before = { it.beforeNativeSelection() })
            // Diagnostic build: observe native selection without rewriting its index.
            for ((name, args) in listOf(
                "setSelection" to arrayOf(Int::class.javaPrimitiveType!!),
                "setSelectionFromTop" to arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
                "smoothScrollBy" to arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
            )) {
                val method = KavaReflector.findMethodRecursive(list, name, *args) ?: continue
                hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        sessions[param.thisObject as? View]?.traceCall(name, param.args)
                    }
                })
            }
            // Observation only: never alter arguments, return values or layout.
            for ((name, signature) in listOf(
                "h" to emptyArray<Class<*>>(),
                "i" to arrayOf(Boolean::class.javaPrimitiveType!!),
                "g" to arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!),
                "onLayout" to arrayOf(Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
            )) {
                val method = KavaReflector.findMethodRecursive(list, name, *signature) ?: continue
                hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        runCatching { sessions[param.thisObject as? View]?.traceLayout("before:$name", param.args) }
                    }
                    override fun afterHookedMethod(param: MethodHookParam) {
                        runCatching { sessions[param.thisObject as? View]?.traceLayout("after:$name", param.args) }
                    }
                })
            }
            nativeListHook(list, "o", after = { it.headerState(true) })
            nativeListHook(list, "q", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, after = { it.headerState(false) })
            // Constant-time state reads only; no view search, layout mutation, SQL or reload on scroll.

            nativeListHook(list, "onScroll", android.widget.AbsListView::class.java, Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, after = { it.syncHeaderVisibility() })
            val recycler = type("com.tencent.mm.ui.conversation.recycler.ConversationRecyclerView")
            hookAttach(recycler, "setAdapter", type("androidx.recyclerview.widget.f2")); hookAttach(recycler, "onAttachedToWindow")
            nativeListHook(recycler, "y1", before = { it.beforeNativeOffset() }, after = { it.afterNativeOffset() })
            nativeListHook(recycler, "setSelectionWithOffset", Int::class.javaPrimitiveType!!, before = { it.beforeNativeSelection() })
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
            KavaReflector.findMethodRecursive(Activity::class.java, "onResume")?.let { method ->
                hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        if (activity.javaClass.name == "com.tencent.mm.ui.LauncherUI") main.post { watchHomepage(activity) }
                    }
                })
            }
            val launcher = type("com.tencent.mm.ui.LauncherUI")
            KavaReflector.findMethodRecursive(launcher, "onResume")?.let { method ->
                hooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (!destroyed) main.post {
                            if (!destroyed) {
                                watchHomepage(param.thisObject as? Activity)
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
                        if (!destroyed) {
                            bootAttempts = 0
                            main.removeCallbacks(bootstrapHomepage)
                            main.post(bootstrapHomepage)
                        }
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
            bootAttempts = 0
            main.post(bootstrapHomepage)
            return true
        } catch (error: Throwable) {
            HLog.e("[Hchat:GroupBar] 安装失败", error); destroy(); return false
        }
    }
    private val reloadChanged = Runnable { reloadQueued.set(false); if (!destroyed && enabled && ready) reload() }
    private var bootAttempts = 0
    private val bootstrapHomepage = object : Runnable {
        override fun run() {
            if (destroyed) return
            val activity = WeChatApis.currentActivity()?.currentActivity()?.takeIf {
                it.javaClass.name == "com.tencent.mm.ui.LauncherUI" && !it.isDestroyed && !it.isFinishing
            } ?: runCatching {
                val launcher = context.hostClassLoader().loadClass("com.tencent.mm.ui.LauncherUI")
                launcher.getDeclaredMethod("getInstance").invoke(null) as? Activity
            }.getOrNull()
            watchHomepage(activity)
            if (sessions.isEmpty() && ++bootAttempts < 40) main.postDelayed(this, 250)
        }
    }
    private fun watchHomepage(activity: Activity?) {
        if (destroyed || activity == null || activity.isFinishing || activity.isDestroyed ||
            activity.javaClass.name != "com.tencent.mm.ui.LauncherUI") return
        if (sessions.keys.any { it.rootView === activity.window?.decorView }) return
        discoverHomepage(activity)
    }
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
            if (!sessions.containsKey(view)) {
                val root = view.rootView
                val titleExists = listOf("ez", "hb", "ih").any { name ->
                    val id = view.resources.getIdentifier(name, "id", view.context.packageName)
                    id != 0 && root.findViewById<View>(id) != null
                }
                if (!titleExists) return
            }
            sessions[view]?.let { if (it.adapter === adapter) { it.refreshPosition(); return } else it.dispose() }
            val session = Session(view, parent, adapter, reset)
            sessions[view] = session; session.render(); if (enabled && ready) session.reload()
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
                    if (changed) { updateFilter(); if (enabled && selected in setOf("friends", "official")) reload() }
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
        private var offsetAppliedDuringUpdate = false
        private var initialAlignmentPending = true
        private var sortingTab = false
        private var headerOpen = false
        // MainUI places its list below the title using a dynamic top margin.
        private var nativeTopMargin = (view.layoutParams as RelativeLayout.LayoutParams).topMargin
        private var floatingInset = 0
        private var titleAnchor: View? = null
        private var stableTitleTop: Int? = null
        private var positionDirty = true
        private var titleChain = emptyList<View>()
        private val preDrawListener = android.view.ViewTreeObserver.OnPreDrawListener {
            if (!disposed) { syncHeaderVisibility(); traceState("frame") }
            true
        }
        // Bounded diagnostic window: no idle logging or view-tree search.
        private var traceUntil = 0L
        private var traceCount = 0
        private var traceStructuralCount = 0
        private var traceSequence = 0
        private var traceLast = ""
        private var traceCalls = 0
        private val traceEnd = Runnable { traceState("end"); traceUntil = 0L }
        fun beginTrace(reason: String) {
            traceUntil = android.os.SystemClock.uptimeMillis() + 1800
            traceCount = 0; traceStructuralCount = 0; traceCalls = 0; traceLayoutCount = 0; traceSequence++; traceLast = ""
            HLog.i("[Hchat:GroupBarDiag] begin=$traceSequence reason=$reason")
            main.removeCallbacks(traceEnd); main.postDelayed(traceEnd, 1700)
            traceState("begin")
        }
        fun traceCall(name: String, args: Array<Any?>) {
            if (android.os.SystemClock.uptimeMillis() > traceUntil || traceCalls >= 12) return
            traceCalls++
            val origin = Throwable().stackTrace.take(18).joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
            HLog.i("[Hchat:GroupBarDiag] seq=$traceSequence call=$name args=${args.joinToString()} origin=$origin")
            traceState("call:$name")
        }
        private var traceLayoutCount = 0
        fun traceLayout(phase: String, args: Array<Any?>) {
            if (traceUntil == 0L || android.os.SystemClock.uptimeMillis() > traceUntil || traceLayoutCount >= 80) return
            traceLayoutCount++
            HLog.i("[Hchat:GroupBarDiag] seq=$traceSequence t=${android.os.SystemClock.uptimeMillis()} layout=$phase args=${args.joinToString()}")
            traceState(phase)
        }
        private fun diagnosticView(view: View?): String {
            if (view == null) return "absent"
            val rect = android.graphics.Rect()
            val visible = view.getGlobalVisibleRect(rect)
            return "${System.identityHashCode(view)}:${view.top}:${view.bottom}:${view.height}:${view.measuredHeight}:${view.layoutParams?.height}:${view.visibility}:${view.alpha}:${view.translationY}:shown=${view.isShown}:global=$visible,$rect:layout=${view.isLayoutRequested}"
        }
        private fun traceState(phase: String) {
            if (traceUntil == 0L || android.os.SystemClock.uptimeMillis() > traceUntil) return
            val frameSample = phase == "frame" || phase == "begin" || phase == "end"
            if (frameSample && traceCount >= 60 || !frameSample && traceStructuralCount >= 80) return
            val list = viewRef.get() ?: return
            val lv = list as? ListView
            val child = if (lv != null && lv.childCount > 0) lv.getChildAt(0) else null
            val task = KavaReflector.findFieldRecursive(list.javaClass, if (lv != null) "i" else "Y2")?.apply { isAccessible = true }?.get(list) as? View
            val controller = controllerField.get(list)
            val offset = if (controller != null) KavaReflector.findFieldRecursive(controller.javaClass, "n")?.apply { isAccessible = true }?.getInt(controller) else null
            val rows = if (lv != null) (0 until minOf(4, lv.childCount)).joinToString(";") { i ->
                val v = lv.getChildAt(i)
                "${lv.firstVisiblePosition+i}:${System.identityHashCode(v)}:${v.top}:${v.bottom}:${v.visibility}:${v.alpha}"
            } else "recycler"
            // Header count is an adapter offset, not a conversation child index.
            val conversationPosition = lv?.headerViewsCount ?: -1
            val conversationIndex = if (lv != null) conversationPosition - lv.firstVisiblePosition else -1
            val conversation = if (lv != null && conversationIndex in 0 until lv.childCount) lv.getChildAt(conversationIndex) else null
            val conversationParent = conversation?.parent as? View
            val footer = if (lv != null) KavaReflector.findFieldRecursive(list.javaClass, "T")?.apply { isAccessible = true }?.get(list) as? View else null
            val evidence = "conversationPos=$conversationPosition conversationIndex=$conversationIndex conversation=${diagnosticView(conversation)} conversationParent=${diagnosticView(conversationParent)} footer=${diagnosticView(footer)} listHeight=${list.height} listScrollY=${list.scrollY}"
            val state = "$evidence rows=$rows group=$selected count=${lv?.count} headers=${lv?.headerViewsCount} first=${lv?.firstVisiblePosition} top=${child?.top} bottom=${child?.bottom} child=${child?.let { System.identityHashCode(it) }} scroll=${scrollField.getInt(list)} base=$nativeScrollOffset reserve=$appliedScrollInset controller=$offset open=${nativeHeaderOpen()} task=${task?.top}/${task?.bottom}/${task?.visibility}/${task?.translationY} bar=${bar.visibility}/${bar.top} title=${titleAnchor?.visibility}/${titleAnchor?.alpha} pending=$pendingSwitchReset deferred=$reloadDeferred touch=$userInteracting"
            if (phase == "frame" && state == traceLast) return
            traceLast = state
            if (frameSample) traceCount++ else traceStructuralCount++
            HLog.i("[Hchat:GroupBarDiag] seq=$traceSequence t=${android.os.SystemClock.uptimeMillis()} phase=$phase $state")
        }
        private val titlePosition = IntArray(2)
        private val parentPosition = IntArray(2)
        private val titleLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!disposed) { positionDirty = true; if (bar.visibility == View.VISIBLE) safePosition() }
        }
        private val positionListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!disposed) { positionDirty = true; if (bar.visibility == View.VISIBLE) safePosition() }
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
                        if (!sortingTab && !vertical && start != null && kotlin.math.abs(event.y - start.y) > slop &&
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
            visibility = View.INVISIBLE
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
                traceState("dataChanged")
                if (pendingSwitchReset && !userInteracting) {
                    main.removeCallbacks(resetAfterData)
                    // DataSetObservable notifies before ListView's next layout. Commit
                    // selection synchronously so a short result never draws its task header.
                    resetAfterData.run()
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
                    val chain = ArrayList<View>()
                    var node: View? = candidate
                    while (node != null) { chain += node; node = node.parent as? View }
                    titleChain = chain
                    candidate.addOnLayoutChangeListener(titleLayoutListener)
                    break
                }
            }
            parent.addOnLayoutChangeListener(titleLayoutListener)
            parent.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            HLog.i("[Hchat:GroupBar] 标题锚点=${titleAnchor?.javaClass?.name} nativeMargin=$nativeTopMargin")
        }
        fun refreshPosition() { if (!disposed) { safePosition(); syncHeaderVisibility() } }
        fun nativeInsetChanged() {
            positionDirty = true
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
            // Commit positioning once, with the NEW data; never force-close a header.
            // The old result must not be repositioned before the new result exists.
            reload()
        }
        private fun nativeHeaderOpen(): Boolean {
            val list = viewRef.get() ?: return headerOpen
            return headerOpen || openField.getBoolean(list)
        }
        private fun selectNativeTop(list: View) {
            if (list is ListView) {
                val first = KavaReflector.findMethodRecursive(list.javaClass, "getFirstHeaderVisible")?.invoke(list) as? Int ?: return
                list.setSelectionFromTop(first, scrollField.getInt(list) - list.paddingTop)
            }
            else KavaReflector.findMethodRecursive(list.javaClass, "setSelectionWithOffset", Int::class.javaPrimitiveType!!)?.invoke(list, 0)
        }
        fun reload() {
            if (disposed || !enabled || !ready) return
            if (userInteracting || nativeHeaderOpen()) { reloadDeferred = true; return }
            reloadDeferred = false
            traceState("reload")
            if (!disposed && viewRef.get()?.isAttachedToWindow == true) runCatching { reset.invoke(adapter) }
                .onFailure { HLog.e("[Hchat:GroupBar] 原生重载失败", it) }
        }
        fun render() {
            val list = viewRef.get() ?: return
            hasVisibleGroups = groups.any { it.enabled }
            val visible = enabled && hasVisibleGroups
            if (!visible) bar.visibility = View.GONE
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
                    var longPressed = false
                    var dragged = false
                    var downRawX = 0f
                    var targetIndex = -1
                    setOnLongClickListener {
                        longPressed = true; sortingTab = true
                        bar.requestDisallowInterceptTouchEvent(true)
                        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        true
                    }
                    setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                downRawX = event.rawX; longPressed = false; dragged = false
                                targetIndex = visibleGroups.indexOfFirst { it.id == group.id }
                                false
                            }
                            android.view.MotionEvent.ACTION_MOVE -> {
                                if (!longPressed) false else {
                                    val delta = event.rawX - downRawX
                                    if (kotlin.math.abs(delta) > android.view.ViewConfiguration.get(context).scaledTouchSlop) dragged = true
                                    if (dragged) {
                                        translationX = delta
                                        this.alpha = 0.7f
                                        val cell = width.coerceAtLeast(1)
                                        targetIndex = ((left + cell / 2f + delta) / cell).toInt().coerceIn(0, visibleGroups.lastIndex)
                                    }
                                    true
                                }
                            }
                            android.view.MotionEvent.ACTION_UP -> {
                                if (!longPressed) false else {
                                    translationX = 0f; this.alpha = 1f
                                    parent?.requestDisallowInterceptTouchEvent(false)
                                    if (dragged) {
                                        val targetId = visibleGroups.getOrNull(targetIndex)?.id
                                        val ordered = groups.toMutableList()
                                        val from = ordered.indexOfFirst { it.id == group.id }
                                        val to = ordered.indexOfFirst { it.id == targetId }
                                        if (from >= 0 && to >= 0 && from != to) {
                                            ordered.add(to, ordered.removeAt(from))
                                            GroupBarStore.save(list.context, ordered)
                                        }
                                    }
                                    longPressed = false; sortingTab = false
                                    true
                                }
                            }
                            android.view.MotionEvent.ACTION_CANCEL -> {
                                translationX = 0f; this.alpha = 1f; longPressed = false; sortingTab = false
                                parent?.requestDisallowInterceptTouchEvent(false)
                                false
                            }
                            else -> longPressed
                        }
                    }
                }
                strip.addView(text, LinearLayout.LayoutParams(1, -1))
            }
            val previousInset = appliedScrollInset
            applyNativeOffset(visible)
            val insetDelta = appliedScrollInset - previousInset
            if (insetDelta != 0 && !userInteracting && !nativeHeaderOpen()) {
                if (list is ListView && list.childCount > 0) {
                    val first = list.firstVisiblePosition
                    val firstTop = list.getChildAt(0).top
                    // ListView's native method uses this top verbatim (no second s addition).
                    list.setSelectionFromTop(first, firstTop - list.paddingTop + insetDelta)
                    initialAlignmentPending = false
                } else initialAlignmentPending = true
            }
            syncHeaderVisibility()
            resize(bar.width)
        }
        private fun titleOpaque(): Boolean = titleChain.isNotEmpty() && titleChain.all {
            it.visibility == View.VISIBLE && it.alpha >= 0.99f && it.scaleY >= 0.99f
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
            // A hidden title is a TaskBar transition, never a new zero-height anchor.
            // Retain the last measured closed-home coordinate until native geometry is valid.
            val titleBottom = if (anchor != null && anchor.isShown && anchor.height > 0 && !nativeHeaderOpen()) {
                anchor.getLocationInWindow(titlePosition)
                parent.getLocationInWindow(parentPosition)
                val measured = (titlePosition[1] + anchor.height - titleTranslation(anchor) - parentPosition[1] - parent.paddingTop).coerceAtLeast(0)
                if (measured > 0) stableTitleTop = measured
                stableTitleTop ?: return
            } else stableTitleTop ?: return
            val top = titleBottom + floatingInset
            if (bp.topMargin != top) { bp.topMargin = top; bar.layoutParams = bp }
            positionDirty = false
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
            // C/y1 first snapshots the PREVIOUS final offset, then overwrites s/v2
            // with a fresh base. Retain that final value for its equality comparison.
            nativeOffsetUpdating = true
            offsetAppliedDuringUpdate = false
        }
        fun beforeTaskBarResize(taskBar: Any) {
            if (!nativeOffsetUpdating) return
            val list = viewRef.get() ?: return
            // This field holds the actual native TaskBar; ignore unrelated containers.
            if (KavaReflector.findFieldRecursive(list.javaClass, if (list is ListView) "i" else "Y2")?.apply { isAccessible = true }?.get(list) === taskBar) {
                beforeNativeSelection()
            }
        }
        fun beforeNativeSelection() {
            if (!nativeOffsetUpdating || offsetAppliedDuringUpdate) return
            val list = viewRef.get() ?: return
            nativeScrollOffset = scrollField.getInt(list)
            nativeOffsetUpdating = false
            applyNativeOffset(enabled && hasVisibleGroups)
            nativeOffsetUpdating = true
            offsetAppliedDuringUpdate = true
        }
        fun afterNativeOffset() {
            val list = viewRef.get() ?: return
            nativeOffsetUpdating = false
            if (!offsetAppliedDuringUpdate) nativeScrollOffset = scrollField.getInt(list)
            applyNativeOffset(enabled && hasVisibleGroups)
            offsetAppliedDuringUpdate = false
            // Native C/y1 already owns orientation/inset positioning. Never force a second
            // selection here: it can run during a pull gesture or TaskBar transition.
        }
        fun headerState(open: Boolean) {
            beginTrace("header:$open")
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
            val titleReady = titleAnchor?.let { it.isShown && it.height > 0 } == true && titleOpaque()
            val visible = enabled && hasVisibleGroups && !open && titleReady
            if (visible && initialAlignmentPending && !userInteracting) {
                viewRef.get()?.let { list ->
                    initialAlignmentPending = false
                    selectNativeTop(list)
                }
            }
            if (visible && (positionDirty || bar.visibility != View.VISIBLE)) safePosition()
            val visibility = if (visible && stableTitleTop != null) View.VISIBLE else View.INVISIBLE
            if (bar.visibility != visibility) {
                bar.visibility = visibility
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
            main.removeCallbacks(traceEnd)
            traceUntil = 0L
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
            titleChain = emptyList()
            parentRef.get()?.let { host ->
                host.removeOnLayoutChangeListener(titleLayoutListener)
                if (host.viewTreeObserver.isAlive) host.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
            }
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
