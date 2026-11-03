package t.mma.hooks.items.dynamicbadge

import android.app.Activity
import t.mma.hooks.api.core.WeChatApis
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import t.mma.dexkit.DexMethodCache
import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.OwnedHandler
import t.mma.hooks.core.OwnedHooks
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.preferences.HchatStorage
import t.mma.utils.KavaReflector
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

class DynamicBadgeFeature : BaseFeature() {
    private var runtime: DynamicBadgeRuntime? = null
    private var owner: PreferenceRuntimeOwner? = null

    override fun featureId(): String = DynamicBadgeSettings.ID
    override fun name(): String = "动态角标"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(DynamicBadgeSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), DynamicBadgeSettings.PREFS_NAME)
        val next = PreferenceRuntimeOwner(
            prefs, setOf(DynamicBadgeSettings.KEY_ENABLE),
            enabled = { prefs.getBoolean(DynamicBadgeSettings.KEY_ENABLE, DynamicBadgeSettings.DEFAULT_ENABLE) },
            create = { scope ->
                val current = DynamicBadgeRuntime(context)
                runtime = current
                scope.register {
                    try { current.destroy() } finally { if (runtime === current) runtime = null }
                }
                current.start()
            },
            onError = { error ->
                try { t.mma.utils.HLog.e("[Hchat:动态角标] 生命周期失败", error) } catch (_: Throwable) { }
            }
        )
        owner = next
        next.start()
        subscribe(Events.DexReady::class.java) { runtime?.scheduleInstall() }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        val current = owner
        owner = null
        try { current?.close() } finally { runtime = null }
    }
}
private class DynamicBadgeRuntime(private val context: FeatureContext) {
    private val host = context.hostContext()
    private val loader = context.hostClassLoader()
    private val prefs = HchatStorage.preferences(host, DynamicBadgeSettings.PREFS_NAME)
    private val methodPrefs = DexMethodCache.prefs(host, "Hchat_dynamic_badge_method_cache")
    private val mainHandler = OwnedHandler({ HLogBridge.error("主线程任务失败", null, it) })
    private val overlays = Collections.synchronizedMap(WeakHashMap<View, BadgeOverlay>())
    private val badgeSources = Collections.synchronizedMap(WeakHashMap<View, View>())
    private val modernRows = Collections.synchronizedMap(WeakHashMap<Any, View>())
    @Volatile private var destroyed = false
    private fun newHooks() = OwnedHooks { HLogBridge.error("Hook 所有权失败", null, it) }
    private var hooks = newHooks()
    private var installFailed = false
    @Volatile private var hookGeneration = 0
    private var installation: DexInstallScheduler.Installation? = null
    private var listening = false
    private val rowTasks = mutableMapOf<View, MutableSet<RowTask>>()
    @Volatile private var installedMethods = emptySet<Method>()
    private val boundRows = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val pendingRowBindings = Collections.synchronizedMap(WeakHashMap<View, View.OnAttachStateChangeListener>())
    @Volatile private var modernHolderInstalled = false
    @Volatile private var modernBindInstalled = false
    private var resumeHookInstalled = false
    private val recoveryHosts = WeakHashMap<View, BadgeListRecovery>()
    private val queuedRows = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val colorCache = java.util.concurrent.ConcurrentHashMap<Boolean, Pair<Int, Int>>()
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (!destroyed) {
            colorCache.clear()
            mainHandler.post { if (!destroyed) refreshAll() }
        }
    }
    @Synchronized fun start() {
        if (destroyed || listening) return
        listening = true
        try {
            prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
            if (!install(allowDexSearch = false)) scheduleInstall()
        } catch (error: Throwable) {
            destroy()
            throw error
        }
    }
    @Synchronized fun scheduleInstall() {
        if (destroyed) return
        val ticket = installation ?: DexInstallScheduler.installation(
            DynamicBadgeSettings.ID, "动态角标"
        ).also { installation = it }
        ticket.schedule { synchronized(this) { destroyed || install(allowDexSearch = true) } }
    }

    @Synchronized
    fun install(allowDexSearch: Boolean): Boolean {
        if (destroyed) return false
        installFailed = false
        val epoch = hookGeneration
        try {
        val methods = locateConversationBindMethods(allowDexSearch)
        val legacyInstalled = if (methods.isEmpty()) false else {
            val newMethods = methods - installedMethods
            if (newMethods.isNotEmpty()) {
                val callback = object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (destroyed || epoch != hookGeneration || param.hasThrowable()) return
                        val row = param.result as? View ?: return
                        if (Looper.myLooper() == Looper.getMainLooper()) {
                            scheduleBindRow(row)
                        } else postRow(row) { removeOverlaysIn(row); scheduleBindRow(row) }
                    }
                }
                val hookedMethods = newMethods.filter { method ->
                    runCatching { hook(method, callback) != null }
                        .getOrElse { HLogBridge.error("安装旧版会话行角标动画 Hook 失败", method, it); false }
                }
                if (hookedMethods.isNotEmpty()) installedMethods = installedMethods + hookedMethods
            }
            installedMethods.isNotEmpty()
        }
        val modernInstalled = installModernConversationHooks(allowDexSearch)
        val installed = legacyInstalled || modernInstalled
        if (destroyed || installFailed) { rollbackHooks(); return false }
        if (!installed && allowDexSearch) {
            t.mma.utils.HLog.e("[Hchat:动态角标] 会话绑定 Hook 未安装，动态角标未生效")
        }
        if (installed) {
            installVisibleRowRecovery()
            if (destroyed || installFailed) { rollbackHooks(); return false }
            mainHandler.post { recoverVisibleRows() }
            // The Activity API or its window can itself be unavailable at hook install.
            listOf(100L, 300L, 1000L, 3000L).forEach { delay ->
                mainHandler.postDelayed({ recoverVisibleRows() }, delay)
            }
        }
        return installed
        } catch (error: Throwable) {
            rollbackHooks()
            HLogBridge.error("安装失败，已回滚独占Hook", null, error)
            return false
        }
    }
    private fun hook(method: Method, callback: XC_MethodHook): XC_MethodHook.Unhook? {
        val handle = hooks.hook(method, callback)
        if (handle == null) installFailed = true
        return handle
    }
    private fun rollbackHooks() {
        hookGeneration++
        release { hooks.close() }
        if (!destroyed) hooks = newHooks()
        installedMethods = emptySet()
        modernHolderInstalled = false
        modernBindInstalled = false
        resumeHookInstalled = false
    }
    private fun installVisibleRowRecovery() {
        val epoch = hookGeneration
        if (resumeHookInstalled) return
        val resume = Activity::class.java.getDeclaredMethod("onResume")
        val handle = hook(resume, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                if (destroyed || epoch != hookGeneration || !activity.javaClass.name.startsWith("com.tencent.mm.")) return
                activity.window?.decorView?.let { decor -> postRow(decor) { recoverVisibleRows(activity) } }
            }
        })
        resumeHookInstalled = handle != null
    }
    private fun recoverVisibleRows(activity: Activity? = WeChatApis.currentActivity()?.currentActivity() as? Activity) {
        if (destroyed || !isEnabled()) return
        val decor = activity?.window?.decorView ?: return
        recoveryHosts.keys.filter { it !== decor && !it.isAttachedToWindow }.toList().forEach {
            recoveryHosts.remove(it)?.dispose()
        }
        if (recoveryHosts[decor] == null) {
            recoveryHosts[decor] = BadgeListRecovery(decor, onError = { HLogBridge.error("列表恢复释放失败", null, it) }) { repairVisibleRows(decor) }
        }
        repairVisibleRows(decor)
    }
    private fun repairVisibleRows(decor: View) {
        if (destroyed || !isEnabled()) return
        fun repairRow(row: View) {
            val views = ArrayList<View>()
            collectViews(row, views)
            val badges = views.filter { it.id == NUMBER_BADGE_ID || it.id == DOT_BADGE_ID }
            val missing = badges.any { badge ->
                val key = if (badge.id == NUMBER_BADGE_ID) DynamicBadgeSettings.KEY_NUMBER_ENABLE else DynamicBadgeSettings.KEY_DOT_ENABLE
                prefs.getBoolean(key, true) && overlays[badge]?.isCurrentHost(row) != true
            }
            if (missing || (badges.isEmpty() && boundRows[row] != true)) scheduleBindRow(row)
        }
        fun visit(view: View) {
            val group = view as? ViewGroup ?: return
            // Only scan visible children of native conversation lists, never arbitrary badges.
            if (view.javaClass.name == "com.tencent.mm.ui.conversation.ConversationListView") {
                for (i in 0 until group.childCount) repairRow(group.getChildAt(i))
                return
            }
            for (i in 0 until group.childCount) visit(group.getChildAt(i))
        }
        visit(decor)
    }
    private fun holderRow(holder: Any?): View? {
        holder ?: return null
        modernRows[holder]?.let { return it }
        var clazz: Class<*>? = holder.javaClass
        val candidates = linkedSetOf<View>()
        while (clazz != null && clazz != Any::class.java) {
            KavaReflector.declaredFields(clazz).filter { View::class.java.isAssignableFrom(it.type) }
                .mapNotNull { KavaReflector.readField(it, holder) as? View }.forEach { view ->
                    var node = view
                    repeat(16) {
                        val parent = node.parent as? View ?: return@repeat
                        if (parent.javaClass.name == "com.tencent.mm.ui.conversation.ConversationListView") {
                            candidates.add(node)
                            return@forEach
                        }
                        node = parent
                    }
                }
            clazz = clazz.superclass
        }
        return candidates.singleOrNull()?.also { modernRows[holder] = it }
    }
    private fun release(action: () -> Unit) {
        try { action() } catch (error: Throwable) {
            try { HLogBridge.error("释放独占资源失败", null, error) } catch (_: Throwable) { }
        }
    }
    @Synchronized fun destroy() {
        if (destroyed) return
        destroyed = true
        hookGeneration++
        val ticket = installation
        installation = null
        release { ticket?.close() }
        release { hooks.close() }
        if (listening) {
            listening = false
            release { prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener) }
        }
        release { mainHandler.close() }
        val tasks = synchronized(rowTasks) { rowTasks.toMap().also {
            rowTasks.values.forEach { callbacks -> callbacks.forEach { it.action = null } }
            rowTasks.clear()
        } }
        tasks.forEach { (row, callbacks) -> callbacks.forEach { task -> release { row.removeCallbacks(task) } } }
        val recoveries = recoveryHosts.values.toList()
        recoveryHosts.clear()
        recoveries.forEach { release { it.dispose() } }
        queuedRows.clear()
        synchronized(modernRows) { modernRows.clear() }
        synchronized(boundRows) { boundRows.clear() }
        val pending = synchronized(pendingRowBindings) {
            pendingRowBindings.toMap().also { pendingRowBindings.clear() }
        }
        pending.forEach { (row, listener) -> release { row.removeOnAttachStateChangeListener(listener) } }
        synchronized(badgeSources) { badgeSources.clear() }
        val current = synchronized(overlays) { overlays.values.toList().also { overlays.clear() } }
        current.forEach { release { it.detach() } }
        colorCache.clear()
        installedMethods = emptySet()
        modernHolderInstalled = false
        modernBindInstalled = false
        resumeHookInstalled = false
    }
    private fun installModernConversationHooks(allowDexSearch: Boolean): Boolean {
        val epoch = hookGeneration
        if (modernHolderInstalled && modernBindInstalled) return true
        val runtimeKey = DexMethodCache.runtimeKey(host, loader)
        val cachedBind = DexMethodCache.load(methodPrefs, runtimeKey, loader, CACHE_MODERN_BIND)
            ?.takeIf(::isModernBind)
        val cachedHolder = DexMethodCache.load(methodPrefs, runtimeKey, loader, CACHE_MODERN_HOLDER)
            ?.takeIf { cachedBind != null && isModernHolder(it, cachedBind.parameterTypes[1]) }
        var bind = cachedBind
        var holder = cachedHolder
        if (bind == null || holder == null) {
            DexMethodCache.clear(methodPrefs, runtimeKey, CACHE_MODERN_BIND)
            DexMethodCache.clear(methodPrefs, runtimeKey, CACHE_MODERN_HOLDER)
            if (!allowDexSearch) return false
            val candidates = runCatching {
                context.dexKitManager().withCurrentBridge { bridge ->
                    bridge.findMethod(
                    FindMethod().apply { matcher(MethodMatcher().apply { usingStrings(MODERN_BIND_ANCHORS) }) }
                ).asSequence().mapNotNull { data ->
                    runCatching { data.getMethodInstance(loader) }.getOrNull()
                }.filter(::isModernBind).distinctBy(Method::toGenericString).toList()
                }
            }.getOrElse {
                HLogBridge.error("DexKit 查找现代会话绑定入口失败", null, it)
                emptyList()
            }
            bind = candidates.singleOrNull() ?: return false
            holder = KavaReflector.declaredMethods(bind!!.declaringClass).singleOrNull {
                isModernHolder(it, bind!!.parameterTypes[1])
            } ?: return false
            DexMethodCache.save(methodPrefs, runtimeKey, CACHE_MODERN_BIND, bind!!)
            DexMethodCache.save(methodPrefs, runtimeKey, CACHE_MODERN_HOLDER, holder!!)
        }
        val targetBind = bind ?: return false
        val targetHolder = holder ?: return false
        if (!modernHolderInstalled) {
            modernHolderInstalled = runCatching {
                hook(targetHolder, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (destroyed || epoch != hookGeneration || param.hasThrowable()) return
                        val holderObject = param.args?.getOrNull(0) ?: return
                        val row = param.args?.getOrNull(1) as? View ?: return
                        modernRows[holderObject] = row
                        scheduleBindRow(row)
                    }
                }) != null
            }.getOrElse { HLogBridge.error("安装现代会话 Holder 行缓存 Hook 失败", targetHolder, it); false }
        }
        if (!modernBindInstalled) {
            modernBindInstalled = runCatching {
                hook(targetBind, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (destroyed || epoch != hookGeneration) return
                        val holderObjects = param.args?.drop(1)?.take(2).orEmpty()
                        holderObjects.forEach { holderObject ->
                            modernRows[holderObject]?.let { row ->
                                boundRows.remove(row)
                            }
                        }
                    }
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (destroyed || epoch != hookGeneration || param.hasThrowable()) return
                        val holderObjects = param.args?.drop(1)?.take(2).orEmpty()
                        val row = holderObjects.asSequence().mapNotNull { holderObject ->
                            holderRow(holderObject)
                        }.firstOrNull() ?: return
                        if (Looper.myLooper() == Looper.getMainLooper()) scheduleBindRow(row) else postRow(row) { scheduleBindRow(row) }
                    }
                }) != null
            }.getOrElse { HLogBridge.error("安装现代会话绑定角标 Hook 失败", targetBind, it); false }
        }
        return modernHolderInstalled && modernBindInstalled
    }

    private fun isModernBind(method: Method): Boolean {
        val types = method.parameterTypes
        val ownerPackage = method.declaringClass.name.substringBeforeLast('.', "")
        return !Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers) &&
            method.returnType == Void.TYPE && types.size == 4 &&
            types[0] == Int::class.javaPrimitiveType &&
            types[1].name.substringBeforeLast('.', "") == ownerPackage &&
            types[2].name.substringBeforeLast('.', "") == ownerPackage &&
            types[3].name.startsWith(MODERN_STORAGE_PREFIX)
    }

    private fun isModernHolder(method: Method, holderClass: Class<*>): Boolean {
        return !Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers) &&
            method.returnType == Void.TYPE && method.parameterTypes.contentEquals(arrayOf(holderClass, View::class.java))
    }

    private fun locateConversationBindMethods(allowDexSearch: Boolean): Set<Method> {
        val exact = runCatching {
            loader.loadClass(EXACT_CONVERSATION_ADAPTER).declaredMethods.filter(::isLegacyConversationBind).toSet()
        }.getOrDefault(emptySet())
        if (exact.isNotEmpty()) return exact
        val runtimeKey = DexMethodCache.runtimeKey(host, loader)
        val cached = DexMethodCache.loadList(methodPrefs, runtimeKey, loader, CACHE_BIND)
            .filter(::isLegacyConversationBind)
        if (cached.isNotEmpty()) return cached.toSet()
        DexMethodCache.clear(methodPrefs, runtimeKey, CACHE_BIND)
        if (!allowDexSearch) return emptySet()
        val candidates = runCatching {
            context.dexKitManager().withCurrentBridge { bridge ->
                bridge.findMethod(
                FindMethod().apply {
                    matcher(MethodMatcher().apply { usingStrings(LEGACY_BIND_ANCHORS) })
                }
            ).asSequence().mapNotNull { data ->
                runCatching { data.getMethodInstance(loader) }.getOrNull()
            }.filter(::isLegacyConversationBind).distinctBy(Method::toGenericString).toList()
            }
        }.getOrElse {
            HLogBridge.error("DexKit 查找会话行角标动画入口失败", null, it)
            emptyList()
        }
        if (candidates.isNotEmpty()) DexMethodCache.saveList(methodPrefs, runtimeKey, CACHE_BIND, candidates)
        return candidates.toSet()
    }

    private fun isLegacyConversationBind(method: Method): Boolean {
        val types = method.parameterTypes
        return method.name == "getView" && method.returnType != Void.TYPE &&
            View::class.java.isAssignableFrom(method.returnType) &&
            types.size == 3 && types[0] == Int::class.javaPrimitiveType &&
            types[1] == View::class.java && types[2] == ViewGroup::class.java &&
            method.declaringClass.name.startsWith(CONVERSATION_OWNER) &&
            !Modifier.isStatic(method.modifiers) && !Modifier.isAbstract(method.modifiers)
    }
    private class RowTask(
        private val runtime: DynamicBadgeRuntime,
        val row: View,
        var action: (() -> Unit)?
    ) : Runnable {
        override fun run() {
            val next = synchronized(runtime.rowTasks) {
                runtime.rowTasks[row]?.let { tasks ->
                    tasks.remove(this); if (tasks.isEmpty()) runtime.rowTasks.remove(row)
                }
                action.also { action = null }
            }
            if (!runtime.destroyed) next?.invoke()
        }
    }
    private fun postRow(row: View, animation: Boolean = false, action: () -> Unit) {
        val task = RowTask(this, row, action)
        synchronized(rowTasks) {
            if (destroyed) { task.action = null; return }
            rowTasks.getOrPut(row) { linkedSetOf() }.add(task)
            try {
                if (animation) row.postOnAnimation(task)
                else if (!row.post(task)) {
                    task.action = null
                    rowTasks[row]?.let { tasks -> tasks.remove(task); if (tasks.isEmpty()) rowTasks.remove(row) }
                }
            } catch (error: Throwable) {
                task.action = null
                rowTasks[row]?.let { tasks -> tasks.remove(task); if (tasks.isEmpty()) rowTasks.remove(row) }
                throw error
            }
        }
    }
    private fun scheduleBindRow(row: View) {
        if (destroyed) return
        if (!isEnabled()) {
            removeOverlaysIn(row)
            return
        }
        if (row.isAttachedToWindow) {
            if (!queuedRows.add(row)) return
            postRow(row, animation = true) {
                queuedRows.remove(row)
                bindRow(row)
            }
            return
        }
        synchronized(pendingRowBindings) {
            if (pendingRowBindings.containsKey(row)) return
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    if (destroyed) return
                    view.removeOnAttachStateChangeListener(this)
                    synchronized(pendingRowBindings) { pendingRowBindings.remove(view) }
                    scheduleBindRow(view)
                }
                override fun onViewDetachedFromWindow(view: View) = Unit
            }
            pendingRowBindings[row] = listener
            row.addOnAttachStateChangeListener(listener)
        }
    }

    private fun bindRow(row: View) {
        if (destroyed || !row.isAttachedToWindow || DIAGNOSTIC_ONLY) return
        if (!isEnabled()) {
            removeOverlaysIn(row)
            return
        }
        val candidates = ArrayList<View>()
        collectViews(row, candidates)
        // Manage both verified controls even when binding temporarily hides them.
        val knownBadges = candidates.filter { it.id == NUMBER_BADGE_ID || it.id == DOT_BADGE_ID }
        val badges = if (knownBadges.isNotEmpty()) knownBadges else candidates.filter(::isBadge)
        if (badges.isEmpty()) {
            if (row.isShown) {
                postRow(row, animation = true) {
                    if (!destroyed && row.isAttachedToWindow && row.isShown) {
                        val retry = ArrayList<View>()
                        collectViews(row, retry)
                        if (retry.any(::isBadge)) bindRow(row)
                    }
                }
            }
            return
        }
        val active = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
        active.addAll(badges)
        boundRows[row] = true
        val stale = synchronized(overlays) {
            overlays.keys.filter { badge -> badgeSources[badge] === row && badge !in active }
        }
        stale.forEach(::removeOverlay)
        badges.forEach { badge ->
            val kind = if (badge.id == NUMBER_BADGE_ID || (badge is TextView && NUMBER_PATTERN.matches(badge.text?.toString()?.trim().orEmpty()))) {
                BadgeKind.NUMBER
            } else {
                BadgeKind.DOT
            }
            val enabledKey = when (kind) {
                BadgeKind.NUMBER -> DynamicBadgeSettings.KEY_NUMBER_ENABLE
                BadgeKind.DOT -> DynamicBadgeSettings.KEY_DOT_ENABLE
            }
            if (!prefs.getBoolean(enabledKey, true)) {
                removeOverlay(badge)
                return@forEach
            }
            val overlay = synchronized(overlays) { overlays[badge] } ?: BadgeOverlay(row, badge, prefs, kind).also {
                synchronized(overlays) { overlays[badge] = it }
                synchronized(badgeSources) { badgeSources[badge] = row }
            }
            if (!overlay.isCurrentHost(row)) {
                removeOverlay(badge)
                val replacement = BadgeOverlay(row, badge, prefs, kind)
                overlays[badge] = replacement
                badgeSources[badge] = row
                replacement.update(kind)
            } else overlay.update(kind)
        }
    }

    private fun isEnabled(): Boolean = prefs.getBoolean(
        DynamicBadgeSettings.KEY_ENABLE, DynamicBadgeSettings.DEFAULT_ENABLE
    )

    private fun isBadge(view: View): Boolean {
        if (view is t.mma.hooks.items.homeextension.CalendarTodayView) return false
        if (view.visibility != View.VISIBLE || view.width <= 0 || view.height <= 0) return false
        if (view.id == NUMBER_BADGE_ID) return true
        if (view.id == DOT_BADGE_ID) return true
        val textView = view as? TextView
        val text = textView?.text?.toString()?.trim().orEmpty()
        val number = textView != null && NUMBER_PATTERN.matches(text) &&
            view.width in dp(view, 6f)..dp(view, 72f) && view.height in dp(view, 6f)..dp(view, 36f) &&
            isLight(textView.currentTextColor) && view.background != null
        val dot = text.isEmpty() && view.background != null &&
            view.width in dp(view, 4f)..dp(view, 24f) && view.height in dp(view, 4f)..dp(view, 24f) &&
            view.width <= view.height * 2 && view.height <= view.width * 2
        return number || dot
    }

    private fun collectViews(root: View, into: MutableList<View>) {
        if (root is t.mma.hooks.items.homeextension.CalendarTodayView) return
        into += root
        if (root is ViewGroup) for (index in 0 until root.childCount) collectViews(root.getChildAt(index), into)
    }

    private fun removeOverlaysIn(root: View) {
        val targets = synchronized(overlays) { overlays.keys.filter { it === root || badgeSources[it] === root || isDescendantOf(it, root) } }
        targets.forEach(::removeOverlay)
    }

    private fun removeOverlay(view: View) {
        val overlay = synchronized(overlays) { overlays.remove(view) }
        synchronized(badgeSources) { badgeSources.remove(view) }
        release { overlay?.detach() }
    }

    private fun isDescendantOf(view: View, ancestor: View): Boolean {
        var parent = view.parent
        while (parent is View) {
            if (parent === ancestor) return true
            parent = parent.parent
        }
        return false
    }

    private fun refreshAll() {
        if (destroyed) return
        if (!isEnabled()) {
            val current = synchronized(overlays) { overlays.keys.toList() }
            current.forEach(::removeOverlay)
            return
        }
        recoverVisibleRows()
        val rows = synchronized(boundRows) { boundRows.keys.toList() }
        rows.forEach { row ->
            if (Looper.myLooper() == Looper.getMainLooper()) scheduleBindRow(row) else postRow(row) { scheduleBindRow(row) }
        }
    }

    private fun dp(view: View, value: Float) = (value * view.resources.displayMetrics.density + 0.5f).toInt()
    private fun isLight(color: Int) = Color.red(color) > 180 && Color.green(color) > 180 && Color.blue(color) > 180

    private enum class BadgeKind { NUMBER, DOT }

    private fun parseGradient(value: String?, number: Boolean): Pair<Int, Int> {
        fun parseColor(part: String): Int? {
            val normalized = part.trim().removePrefix("#")
            if (normalized.length != 6 && normalized.length != 8) return null
            val parsed = normalized.toLongOrNull(16)?.toInt() ?: return null
            return if (normalized.length == 6) (0xFF000000L or parsed.toLong()).toInt() else parsed
        }
        val legacyStartKey = if (number) DynamicBadgeSettings.KEY_NUMBER_START else DynamicBadgeSettings.KEY_DOT_START
        val legacyEndKey = if (number) DynamicBadgeSettings.KEY_NUMBER_END else DynamicBadgeSettings.KEY_DOT_END
        val legacyStart = prefs.getInt(legacyStartKey, Int.MIN_VALUE)
        val legacyEnd = prefs.getInt(legacyEndKey, Int.MIN_VALUE)
        val colorKey = if (number) DynamicBadgeSettings.KEY_NUMBER_COLOR else DynamicBadgeSettings.KEY_DOT_COLOR
        var effectiveValue = value
        if (value.isNullOrBlank() && (legacyStart != Int.MIN_VALUE || legacyEnd != Int.MIN_VALUE)) {
            effectiveValue = "#%06X,#%06X".format(
                (legacyStart.takeUnless { it == Int.MIN_VALUE } ?: legacyEnd) and 0xFFFFFF,
                (legacyEnd.takeUnless { it == Int.MIN_VALUE } ?: legacyStart) and 0xFFFFFF
            )
            prefs.edit().putString(colorKey, effectiveValue)
                .remove(legacyStartKey).remove(legacyEndKey).apply()
        }
        val parts = effectiveValue.orEmpty().split(',').mapNotNull(::parseColor)
        return when (parts.size) {
            0 -> {
                val defaults = (if (number) DynamicBadgeSettings.DEFAULT_NUMBER_COLOR else DynamicBadgeSettings.DEFAULT_DOT_COLOR)
                    .split(',').mapNotNull(::parseColor)
                defaults[0] to defaults[1]
            }
            1 -> parts[0] to parts[0]
            else -> parts[0] to parts[1]
        }
    }

    private companion object {
        const val CACHE_BIND = "conversation_get_view_v2"
        const val CACHE_MODERN_BIND = "modern_conversation_bind_v1"
        const val CACHE_MODERN_HOLDER = "modern_conversation_holder_v1"
        const val CONVERSATION_OWNER = "com.tencent.mm.ui.conversation."
        const val EXACT_CONVERSATION_ADAPTER = "com.tencent.mm.ui.conversation.o2"
        const val MODERN_STORAGE_PREFIX = "com.tencent.mm.storage."
        const val FRAME_INTERVAL_MS = 16L
        const val DIAGNOSTIC_ONLY = false
        const val NUMBER_BADGE_ID = 2131389131
        const val DOT_BADGE_ID = 2131363591
        val LEGACY_BIND_ANCHORS = listOf("MicroMsg.ConversationWithCacheAdapter", "MicroMsg.ConversationAdapter")
        val MODERN_BIND_ANCHORS = listOf("[getView] position=", "handleShowTipCnt")
        val NUMBER_PATTERN = Regex("[0-9]+\\+?\\s*")
    }

    private object HLogBridge {
        fun error(message: String, member: Method?, error: Throwable) {
            try { t.mma.utils.HLog.e("[Hchat:动态角标] $message${member?.let { ": ${it.toGenericString()}" } ?: ""}", error) } catch (_: Throwable) { }
        }
    }

    private inner class BadgeOverlay(
        private val rowView: View,
        private val source: View,
        private val preferences: SharedPreferences,
        initialKind: BadgeKind
    ) : View.OnAttachStateChangeListener, View.OnLayoutChangeListener {
        private val hostView: View = (rowView.parent as? View) ?: rowView
        private val drawable = AnimatedBadgeDrawable(hostView, source, preferences, initialKind)
        private var disposed = false
        private val drawableTasks = mutableMapOf<Runnable, Runnable>()
        fun isCurrentHost(row: View): Boolean = rowView === row && hostView === ((row.parent as? View) ?: row)
        private val windowObserver = BadgeWindowObserver(hostView, onError = { HLogBridge.error("窗口观察释放失败", null, it) }) {
            updateBounds()
            updateVisibility()
        }
        init {
            try {
            drawable.callback = object : Drawable.Callback {
                override fun invalidateDrawable(who: Drawable) {
                    if (!disposed && !destroyed) hostView.postInvalidateOnAnimation()
                }
                override fun scheduleDrawable(who: Drawable, what: Runnable, whenMillis: Long) {
                    synchronized(drawableTasks) {
                        if (disposed || destroyed) return
                        drawableTasks.remove(what)?.let { hostView.removeCallbacks(it) }
                        lateinit var callback: Runnable
                        callback = Runnable {
                            synchronized(drawableTasks) { if (drawableTasks[what] === callback) drawableTasks.remove(what) }
                            if (!disposed && !destroyed) what.run()
                        }
                        drawableTasks[what] = callback
                        try {
                            if (!hostView.postDelayed(callback, (whenMillis - SystemClock.uptimeMillis()).coerceAtLeast(0L))) {
                                drawableTasks.remove(what)
                            }
                        } catch (error: Throwable) { drawableTasks.remove(what); throw error }
                    }
                }
                override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                    synchronized(drawableTasks) { drawableTasks.remove(what) }?.let { hostView.removeCallbacks(it) }
                }
            }
            updateBounds()
            hostView.overlay.add(drawable)
            hostView.addOnAttachStateChangeListener(this)
            source.addOnAttachStateChangeListener(this)
            source.addOnLayoutChangeListener(this)
            windowObserver.attach()
            drawable.start()
            } catch (error: Throwable) {
                detach()
                throw error
            }
        }
        fun update(kind: BadgeKind) {
            if (disposed || destroyed) return
            windowObserver.attach()
            drawable.setKind(kind)
            updateVisibility()
        }
        private fun updateVisibility() {
            if (!disposed && rowView.isAttachedToWindow && source.isAttachedToWindow && hostView.isAttachedToWindow && hostView.isShown && source.isShown && source.visibility == View.VISIBLE) drawable.start()
            else drawable.stop()
        }
        fun detach() {
            if (disposed) return
            disposed = true
            release { windowObserver.dispose() }
            release { drawable.dispose() }
            release { drawable.callback = null }
            val tasks = synchronized(drawableTasks) { drawableTasks.values.toList().also { drawableTasks.clear() } }
            tasks.forEach { release { hostView.removeCallbacks(it) } }
            release { hostView.overlay.remove(drawable) }
            release { hostView.removeOnAttachStateChangeListener(this) }
            release { source.removeOnAttachStateChangeListener(this) }
            release { source.removeOnLayoutChangeListener(this) }
        }
        override fun onViewAttachedToWindow(view: View) {
            if (disposed || destroyed) return
            windowObserver.attach()
            updateBounds()
            updateVisibility()
            hostView.postInvalidateOnAnimation()
        }
        override fun onViewDetachedFromWindow(view: View) {
            release { drawable.stop() }
            if (view === hostView) release { windowObserver.detach() }
            if (view === source) removeOverlay(source)
        }
        override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
            updateBounds()
        }
        private fun updateBounds() {
            if (disposed || destroyed) return
            val location = IntArray(2)
            source.getLocationOnScreen(location)
            val hostLocation = IntArray(2)
            hostView.getLocationOnScreen(hostLocation)
            val left = location[0] - hostLocation[0]
            val top = location[1] - hostLocation[1]
            drawable.setBounds(left, top, left + source.width, top + source.height)
        }
    }

    private inner class AnimatedBadgeDrawable(
        private val hostView: View,
        private val source: View,
        private val prefs: SharedPreferences,
        kind: BadgeKind
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()
        private val selectableEffect = SelectableBorderEffect()
        private val motionMatrix = android.graphics.Matrix()
        private var motionShader: android.graphics.SweepGradient? = null
        private var shaderStart = 0
        private var shaderEnd = 0
        private var shaderX = Float.NaN
        private var shaderY = Float.NaN
        private val frame = object : Runnable {
            override fun run() {
                if (!running || disposed || destroyed) return
                if (!hostView.isAttachedToWindow || !source.isAttachedToWindow || !source.isShown) {
                    stop()
                    return
                }
                invalidateSelf()
                hostView.postInvalidateOnAnimation()
                if (running && !disposed && !destroyed) hostView.postOnAnimation(this)
            }
        }
        @Volatile private var disposed = false
        @Volatile private var running = false
        @Volatile private var badgeKind = kind
        fun setKind(value: BadgeKind) { badgeKind = value; invalidateSelf() }
        fun start() {
            if (disposed || destroyed || running || !hostView.isAttachedToWindow || !source.isAttachedToWindow || !source.isShown) return
            running = true
            invalidateSelf()
            hostView.postInvalidateOnAnimation()
            hostView.postOnAnimation(frame)
        }
        fun stop() {
            running = false
            hostView.removeCallbacks(frame)
        }
        fun dispose() {
            if (disposed) return
            disposed = true
            try { stop() } finally { callback = null }
        }
        override fun draw(canvas: Canvas) {
            if (disposed || destroyed || !source.isShown || source.visibility != View.VISIBLE || bounds.isEmpty) return
            val number = badgeKind == BadgeKind.NUMBER
            val colors = colorCache[number] ?: parseGradient(
                prefs.getString(
                    if (number) DynamicBadgeSettings.KEY_NUMBER_COLOR else DynamicBadgeSettings.KEY_DOT_COLOR,
                    if (number) DynamicBadgeSettings.DEFAULT_NUMBER_COLOR else DynamicBadgeSettings.DEFAULT_DOT_COLOR
                ),
                number
            ).also { colorCache[number] = it }
            val start = colors.first
            val end = colors.second
            val duration = prefs.getInt(if (number) DynamicBadgeSettings.KEY_NUMBER_SPEED else DynamicBadgeSettings.KEY_DOT_SPEED, if (number) DynamicBadgeSettings.DEFAULT_NUMBER_SPEED else DynamicBadgeSettings.DEFAULT_DOT_SPEED).coerceIn(DynamicBadgeSettings.MIN_SPEED, DynamicBadgeSettings.MAX_SPEED)
            val phase = ((SystemClock.uptimeMillis() % duration).toFloat() / duration) * 360f
            val density = source.resources.displayMetrics.density
            val strokeDp = prefs.getInt(
                if (number) DynamicBadgeSettings.KEY_NUMBER_STROKE_WIDTH else DynamicBadgeSettings.KEY_DOT_STROKE_WIDTH,
                if (number) DynamicBadgeSettings.DEFAULT_NUMBER_STROKE_WIDTH else DynamicBadgeSettings.DEFAULT_DOT_STROKE_WIDTH
            ).coerceIn(DynamicBadgeSettings.MIN_STROKE_WIDTH, DynamicBadgeSettings.MAX_STROKE_WIDTH)
            val stroke = strokeDp * density
            val gap = 2f * density
            rect.set(bounds.left - gap - stroke / 2f, bounds.top - gap - stroke / 2f, bounds.right + gap + stroke / 2f, bounds.bottom + gap + stroke / 2f)
            val motionStyle = prefs.getInt(DynamicBadgeSettings.KEY_STYLE, 0).coerceIn(0, 2)
            if (motionStyle != 0) {
                if (motionShader == null || shaderStart != start || shaderEnd != end || shaderX != rect.centerX() || shaderY != rect.centerY()) {
                    shaderStart = start; shaderEnd = end; shaderX = rect.centerX(); shaderY = rect.centerY()
                    motionShader = android.graphics.SweepGradient(shaderX, shaderY, intArrayOf(start, end, start), floatArrayOf(0f, .5f, 1f))
                }
                motionMatrix.setRotate(phase, rect.centerX(), rect.centerY()); motionShader?.setLocalMatrix(motionMatrix)
                paint.shader = motionShader; paint.strokeWidth = stroke; paint.alpha = 255
                selectableEffect.draw(canvas, paint, rect, rect.height() / 2f, motionStyle, phase / 360f, density, SystemClock.uptimeMillis(), duration)
            } else if (number) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = stroke
                RotatingGradientStroke.draw(canvas, paint, rect, rect.height() / 2f, start, end, phase)
            } else {
                val pulse = (0.35f + 0.65f * (0.5f + 0.5f * kotlin.math.cos(phase * Math.PI / 180.0))).toFloat()
                val dotRect = RectF(rect.left - density, rect.top - density, rect.right + density, rect.bottom + density)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = stroke
                paint.alpha = (pulse * 255).toInt()
                paint.shader = android.graphics.LinearGradient(dotRect.left, dotRect.top, dotRect.right, dotRect.bottom, start, end, android.graphics.Shader.TileMode.CLAMP)
                canvas.drawOval(dotRect, paint)
            }
            paint.shader = null
            paint.alpha = 255
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}
