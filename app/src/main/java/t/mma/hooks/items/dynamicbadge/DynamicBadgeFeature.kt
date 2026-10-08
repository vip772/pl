package t.mma.hooks.items.dynamicbadge

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import t.mma.dexkit.DexMethodCache
import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.HchatStorage
import t.mma.utils.KavaReflector
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

class DynamicBadgeFeature : BaseFeature() {
    private var runtime: DynamicBadgeRuntime? = null

    override fun featureId(): String = DynamicBadgeSettings.ID
    override fun name(): String = "动态角标"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(DynamicBadgeSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val current = DynamicBadgeRuntime(context)
        runtime = current
        if (!current.install(allowDexSearch = false)) scheduleInstall()
        subscribe(Events.DexReady::class.java) { scheduleInstall() }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }

    private fun scheduleInstall() {
        DexInstallScheduler.schedule(DynamicBadgeSettings.ID, name()) {
            runtime?.install(allowDexSearch = true) == true
        }
    }
}

private class DynamicBadgeRuntime(private val context: FeatureContext) {
    private val host = context.hostContext()
    private val loader = context.hostClassLoader()
    private val prefs = HchatStorage.preferences(host, DynamicBadgeSettings.PREFS_NAME)
    private val methodPrefs = DexMethodCache.prefs(host, "Hchat_dynamic_badge_method_cache")
    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlays = Collections.synchronizedMap(WeakHashMap<View, BadgeOverlay>())
    private val badgeSources = Collections.synchronizedMap(WeakHashMap<View, View>())
    private val modernRows = Collections.synchronizedMap(WeakHashMap<Any, View>())
    @Volatile private var destroyed = false
    @Volatile private var installedMethods = emptySet<Method>()
    private val boundRows = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    @Volatile private var modernHolderInstalled = false
    @Volatile private var modernBindInstalled = false
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        mainHandler.post { refreshAll() }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    @Synchronized
    fun install(allowDexSearch: Boolean): Boolean {
        if (destroyed) return false
        val methods = locateConversationBindMethods(allowDexSearch)
        val legacyInstalled = if (methods.isEmpty()) false else {
            val newMethods = methods - installedMethods
            if (newMethods.isNotEmpty()) {
                val callback = object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (destroyed || param.hasThrowable()) return
                        val row = param.result as? View ?: return
                        if (Looper.myLooper() == Looper.getMainLooper()) {
                            removeOverlaysIn(row)
                            bindRow(row)
                        } else row.post { removeOverlaysIn(row); bindRow(row) }
                    }
                }
                var hooked = 0
                newMethods.forEach { method ->
                    runCatching { HookRegistry.get().hook(method, callback); hooked++ }
                        .onFailure { HLogBridge.error("安装旧版会话行角标动画 Hook 失败", method, it) }
                }
                if (hooked > 0) installedMethods = installedMethods + newMethods
            }
            installedMethods.isNotEmpty()
        }
        val modernInstalled = installModernConversationHooks(allowDexSearch)
        return legacyInstalled || modernInstalled
    }

    fun destroy() {
        destroyed = true
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        mainHandler.removeCallbacksAndMessages(null)
        synchronized(modernRows) { modernRows.clear() }
        synchronized(boundRows) { boundRows.clear() }
        synchronized(badgeSources) { badgeSources.clear() }
        val current = synchronized(overlays) { overlays.values.toList().also { overlays.clear() } }
        current.forEach(BadgeOverlay::detach)
    }

    private fun installModernConversationHooks(allowDexSearch: Boolean): Boolean {
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
                context.dexKitBridge().findMethod(
                    FindMethod().apply { matcher(MethodMatcher().apply { usingStrings(MODERN_BIND_ANCHORS) }) }
                ).asSequence().mapNotNull { data ->
                    runCatching { data.getMethodInstance(loader) }.getOrNull()
                }.filter(::isModernBind).distinctBy(Method::toGenericString).toList()
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
                HookRegistry.get().hook(targetHolder, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (destroyed || param.hasThrowable()) return
                        val holderObject = param.args?.getOrNull(0) ?: return
                        val row = param.args?.getOrNull(1) as? View ?: return
                        modernRows[holderObject] = row
                    }
                })
                true
            }.getOrElse { HLogBridge.error("安装现代会话 Holder 行缓存 Hook 失败", targetHolder, it); false }
        }
        if (!modernBindInstalled) {
            modernBindInstalled = runCatching {
                HookRegistry.get().hook(targetBind, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val holderObject = param.args?.getOrNull(1) ?: return
                        modernRows[holderObject]?.let { row ->
                            removeOverlaysIn(row)
                            boundRows.remove(row)
                        }
                    }
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (destroyed || param.hasThrowable()) return
                        val holderObject = param.args?.getOrNull(1) ?: return
                        val row = modernRows[holderObject] ?: return
                        if (Looper.myLooper() == Looper.getMainLooper()) bindRow(row) else row.post { bindRow(row) }
                    }
                })
                true
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
        val runtimeKey = DexMethodCache.runtimeKey(host, loader)
        val cached = DexMethodCache.loadList(methodPrefs, runtimeKey, loader, CACHE_BIND)
            .filter(::isLegacyConversationBind)
        if (cached.isNotEmpty()) return cached.toSet()
        DexMethodCache.clear(methodPrefs, runtimeKey, CACHE_BIND)
        if (!allowDexSearch) return emptySet()
        val candidates = runCatching {
            context.dexKitBridge().findMethod(
                FindMethod().apply {
                    matcher(MethodMatcher().apply { usingStrings(LEGACY_BIND_ANCHORS) })
                }
            ).asSequence().mapNotNull { data ->
                runCatching { data.getMethodInstance(loader) }.getOrNull()
            }.filter(::isLegacyConversationBind).distinctBy(Method::toGenericString).toList()
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
    private fun bindRow(row: View) {
        if (!isEnabled()) {
            removeOverlaysIn(row)
            return
        }
        val candidates = ArrayList<View>()
        collectViews(row, candidates)
        val badges = candidates.filter(::isBadge)
        val active = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
        active.addAll(badges)
        val stale = synchronized(overlays) {
            overlays.keys.filter { badge -> badgeSources[badge] === row && badge !in active }
        }
        stale.forEach(::removeOverlay)
        boundRows[row] = true
        badges.forEach { badge ->
            val kind = if (badge is TextView && NUMBER_PATTERN.matches(badge.text?.toString()?.trim().orEmpty())) {
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
            overlay.update(kind)
        }
    }

    private fun isEnabled(): Boolean = prefs.getBoolean(
        DynamicBadgeSettings.KEY_ENABLE, DynamicBadgeSettings.DEFAULT_ENABLE
    )

    private fun isBadge(view: View): Boolean {
        if (!view.isShown || view.visibility != View.VISIBLE || view.width <= 0 || view.height <= 0) return false
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
        into += root
        if (root is ViewGroup) for (index in 0 until root.childCount) collectViews(root.getChildAt(index), into)
    }

    private fun removeOverlaysIn(root: View) {
        val targets = synchronized(overlays) { overlays.keys.filter { it === root || badgeSources[it] === root || isDescendantOf(it, root) } }
        targets.forEach(::removeOverlay)
    }

    private fun removeOverlay(view: View) {
        synchronized(overlays) { overlays.remove(view) }?.detach()
        synchronized(badgeSources) { badgeSources.remove(view) }
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
        if (!isEnabled()) {
            val current = synchronized(overlays) { overlays.keys.toList() }
            current.forEach(::removeOverlay)
            return
        }
        val rows = synchronized(boundRows) { boundRows.keys.toList() }
        rows.filter { it.isAttachedToWindow && it.isShown }.forEach { row ->
            if (Looper.myLooper() == Looper.getMainLooper()) bindRow(row) else row.post { bindRow(row) }
        }
    }

    private fun dp(view: View, value: Float) = (value * view.resources.displayMetrics.density + 0.5f).toInt()
    private fun isLight(color: Int) = Color.red(color) > 180 && Color.green(color) > 180 && Color.blue(color) > 180

    private enum class BadgeKind { NUMBER, DOT }
    private companion object {
        const val CACHE_BIND = "conversation_get_view_v2"
        const val CACHE_MODERN_BIND = "modern_conversation_bind_v1"
        const val CACHE_MODERN_HOLDER = "modern_conversation_holder_v1"
        const val CONVERSATION_OWNER = "com.tencent.mm.ui.conversation."
        const val MODERN_STORAGE_PREFIX = "com.tencent.mm.storage."
        const val FRAME_INTERVAL_MS = 16L
        val LEGACY_BIND_ANCHORS = listOf("MicroMsg.ConversationWithCacheAdapter", "MicroMsg.ConversationAdapter")
        val MODERN_BIND_ANCHORS = listOf("[getView] position=", "handleShowTipCnt")
        val NUMBER_PATTERN = Regex("[0-9]+\\+?\\s*")
    }

    private object HLogBridge {
        fun error(message: String, member: Method?, error: Throwable) {
            t.mma.utils.HLog.e("[Hchat:动态角标] $message${member?.let { ": ${it.toGenericString()}" } ?: ""}", error)
        }
    }

    private inner class BadgeOverlay(
        private val hostView: View,
        private val source: View,
        private val preferences: SharedPreferences,
        initialKind: BadgeKind
    ) : View.OnAttachStateChangeListener, View.OnLayoutChangeListener {
        private val drawable = AnimatedBadgeDrawable(hostView, source, preferences, initialKind)
        private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
            updateVisibility()
            true
        }
        init {
            drawable.callback = object : Drawable.Callback {
                override fun invalidateDrawable(who: Drawable) {
                    hostView.postInvalidateOnAnimation()
                }
                override fun scheduleDrawable(who: Drawable, what: Runnable, whenMillis: Long) {
                    hostView.postDelayed(what, (whenMillis - SystemClock.uptimeMillis()).coerceAtLeast(0L))
                }
                override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                    hostView.removeCallbacks(what)
                }
            }
            updateBounds()
            hostView.overlay.add(drawable)
            hostView.addOnAttachStateChangeListener(this)
            source.addOnLayoutChangeListener(this)
            hostView.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            drawable.start()
        }
        fun update(kind: BadgeKind) {
            drawable.setKind(kind)
            updateVisibility()
        }
        private fun updateVisibility() {
            if (hostView.isAttachedToWindow && hostView.isShown && source.isShown && source.visibility == View.VISIBLE) drawable.start()
            else drawable.stop()
        }
        fun detach() {
            drawable.stop()
            hostView.overlay.remove(drawable)
            hostView.removeOnAttachStateChangeListener(this)
            source.removeOnLayoutChangeListener(this)
            if (hostView.viewTreeObserver.isAlive) hostView.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        override fun onViewAttachedToWindow(view: View) = updateVisibility()
        override fun onViewDetachedFromWindow(view: View) = drawable.stop()
        override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
            updateBounds()
        }
        private fun updateBounds() {
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
        private var frame = Runnable { invalidateSelf() }
        @Volatile private var running = false
        @Volatile private var badgeKind = kind
        fun setKind(value: BadgeKind) { badgeKind = value; invalidateSelf() }
        fun start() {
            if (running || !source.isAttachedToWindow || !source.isShown) return
            running = true
            scheduleSelf(frame, SystemClock.uptimeMillis() + FRAME_INTERVAL_MS)
        }
        fun stop() {
            running = false
            unscheduleSelf(frame)
        }
        override fun draw(canvas: Canvas) {
            if (running && hostView.isAttachedToWindow && hostView.isShown && source.isShown) {
                scheduleSelf(frame, SystemClock.uptimeMillis() + FRAME_INTERVAL_MS)
            }
            if (!source.isShown || source.visibility != View.VISIBLE || bounds.isEmpty) return
            val number = badgeKind == BadgeKind.NUMBER
            val start = prefs.getInt(if (number) DynamicBadgeSettings.KEY_NUMBER_START else DynamicBadgeSettings.KEY_DOT_START, if (number) DynamicBadgeSettings.DEFAULT_NUMBER_START else DynamicBadgeSettings.DEFAULT_DOT_START)
            val end = prefs.getInt(if (number) DynamicBadgeSettings.KEY_NUMBER_END else DynamicBadgeSettings.KEY_DOT_END, if (number) DynamicBadgeSettings.DEFAULT_NUMBER_END else DynamicBadgeSettings.DEFAULT_DOT_END)
            val duration = prefs.getInt(if (number) DynamicBadgeSettings.KEY_NUMBER_SPEED else DynamicBadgeSettings.KEY_DOT_SPEED, if (number) DynamicBadgeSettings.DEFAULT_NUMBER_SPEED else DynamicBadgeSettings.DEFAULT_DOT_SPEED).coerceIn(DynamicBadgeSettings.MIN_SPEED, DynamicBadgeSettings.MAX_SPEED)
            val phase = ((SystemClock.uptimeMillis() % duration).toFloat() / duration) * 360f
            val density = source.resources.displayMetrics.density
            val inset = if (number) 1.5f * density else 0f
            rect.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
            if (number) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f * density
                paint.shader = SweepGradient(rect.centerX(), rect.centerY(), intArrayOf(start, end, start), floatArrayOf(0f, 0.5f, 1f)).apply {
                    setLocalMatrix(android.graphics.Matrix().apply { postRotate(phase, rect.centerX(), rect.centerY()) })
                }
                canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint)
            } else {
                val pulse = (0.35f + 0.65f * (0.5f + 0.5f * kotlin.math.cos(phase * Math.PI / 180.0))).toFloat()
                val dotRect = RectF(rect.left - density, rect.top - density, rect.right + density, rect.bottom + density)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
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
