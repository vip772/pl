package t.mma.hooks.items.backgroundbeauty
import t.mma.utils.HLog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ImageView
import android.widget.ScrollView
import de.robv.android.xposed.XC_MethodHook
import t.mma.dexkit.DexMethodCache
import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.HchatStorage
import t.mma.utils.KavaReflector
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.ref.WeakReference
import java.lang.reflect.Member
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.max

class BackgroundBeautyFeature : BaseFeature() {
    private var runtime: BackgroundBeautyRuntime? = null

    override fun featureId(): String = ID

    override fun name(): String = "沉浸式背景"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(BackgroundBeautySettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        runtime = BackgroundBeautyRuntime(context)
        runtime?.install()
        WeChatApis.chatPage()?.let { api ->
            trackSubscription(api.subscribe { event -> runtime?.onChatPageChanged(event.isEnter()) })
        }
        subscribe(Events.DexReady::class.java) {
            DexInstallScheduler.schedule("$ID:chat_background", name()) {
                runtime?.installChatBackgroundComponentHook(allowDexSearch = true) == true
            }
        }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }

    companion object {
        const val ID = "background_beauty"
    }
}

private class BackgroundBeautyTransparentDrawable : Drawable() {
    override fun draw(canvas: Canvas) = Unit

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Drawable opacity is not used by the background renderer")
    override fun getOpacity(): Int = PixelFormat.TRANSPARENT
}

private class BackgroundBeautyFeedbackDrawable() : Drawable(), Drawable.Callback {
    private val ripple = android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(0x40808080), null, ColorDrawable(Color.WHITE)
    ).apply { callback = this@BackgroundBeautyFeedbackDrawable }
    override fun draw(canvas: Canvas) { ripple.bounds = bounds; ripple.draw(canvas) }
    override fun isStateful(): Boolean = true
    private var touchPressed = false
    private var hostState = intArrayOf()
    fun setTouchPressed(pressed: Boolean) {
        touchPressed = pressed
        applyRippleState()
    }
    private fun applyRippleState() {
        ripple.state = if (touchPressed) hostState.filter { it != android.R.attr.state_pressed }.toIntArray() + intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed)
            else hostState.filter { it != android.R.attr.state_pressed }.toIntArray()
        invalidateSelf()
    }
    override fun onStateChange(state: IntArray): Boolean {
        hostState = state
        ripple.state = if (touchPressed) state + intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed) else state
        invalidateSelf()
        return true
    }
    override fun onBoundsChange(bounds: Rect) { ripple.bounds = bounds }
    override fun setHotspot(x: Float, y: Float) { ripple.setHotspot(x, y) }
    override fun setHotspotBounds(left: Int, top: Int, right: Int, bottom: Int) { ripple.setHotspotBounds(left, top, right, bottom) }
    override fun jumpToCurrentState() { ripple.jumpToCurrentState() }
    override fun setAlpha(alpha: Int) { ripple.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { ripple.colorFilter = colorFilter }
    @Deprecated("Drawable opacity is not used by the background renderer")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun invalidateDrawable(who: Drawable) = invalidateSelf()
    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = scheduleSelf(what, `when`)
    override fun unscheduleDrawable(who: Drawable, what: Runnable) = unscheduleSelf(what)
}
private class ScreenAlignedBitmapDrawable(
    bitmap: Bitmap,
    anchor: View,
    opacity: Float,
    private val baseColor: Int? = null
) : Drawable() {
    private val bitmapRef = WeakReference(bitmap)
    private val anchorRef = WeakReference(anchor)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
        alpha = (BackgroundBeautySettings.opacity(opacity) * 255f).toInt().coerceIn(0, 255)
    }
    private val rootLocation = IntArray(2)
    private val anchorLocation = IntArray(2)
    private val destination = RectF()

    init {
        updateDestination()
    }

    private fun updateDestination() {
        val bitmap = bitmapRef.get() ?: return
        if (bitmap.isRecycled) return
        val anchor = anchorRef.get() ?: return
        val root = anchor.rootView ?: anchor
        val rootWidth = root.width.takeIf { it > 0 } ?: bounds.width()
        val rootHeight = root.height.takeIf { it > 0 } ?: bounds.height()
        if (rootWidth <= 0 || rootHeight <= 0) return
        runCatching {
            root.getLocationOnScreen(rootLocation)
            anchor.getLocationOnScreen(anchorLocation)
        }.getOrElse {
            rootLocation[0] = 0
            rootLocation[1] = 0
            anchorLocation[0] = 0
            anchorLocation[1] = 0
        }
        val scale = max(
            rootWidth.toFloat() / bitmap.width.toFloat(),
            rootHeight.toFloat() / bitmap.height.toFloat()
        )
        val drawnWidth = bitmap.width * scale
        val drawnHeight = bitmap.height * scale
        val offsetX = anchorLocation[0] - rootLocation[0]
        val offsetY = anchorLocation[1] - rootLocation[1]
        destination.set(
            (rootWidth - drawnWidth) / 2f - offsetX,
            (rootHeight - drawnHeight) / 2f - offsetY,
            (rootWidth + drawnWidth) / 2f - offsetX,
            (rootHeight + drawnHeight) / 2f - offsetY
        )
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        updateDestination()
    }

    private var drawDiagnostics = 0
    override fun draw(canvas: Canvas) {
        val bitmap = bitmapRef.get()
        if (drawDiagnostics++ < 8) HLog.i("[Hchat:沉浸式背景] DIAG imageDraw bitmap=${bitmap != null} recycled=${bitmap?.isRecycled} alpha=${paint.alpha} destination=$destination clip=${canvas.clipBounds} hardware=${canvas.isHardwareAccelerated}")
        baseColor?.let { canvas.drawColor(it) }
        if (bitmap == null || bitmap.isRecycled || destination.isEmpty) return
        canvas.drawBitmap(bitmap, null, destination, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Drawable opacity is not used by the background renderer")
    override fun getOpacity(): Int = if (baseColor != null) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT
}

private class BackgroundBeautyRuntime(
    private val context: FeatureContext
) {
    private companion object {
        const val TAG = "[Hchat:沉浸式背景]"
        const val CHAT_ACTIVITY = "com.tencent.mm.ui.chatting.ChattingUI"
        const val LAUNCHER_ACTIVITY = "com.tencent.mm.ui.LauncherUI"
        const val MAIN_TAB_CLASS = "com.tencent.mm.ui.MainTabUI"
        const val BOTTOM_TAB_CLASS = "com.tencent.mm.ui.LauncherUIBottomTabView"
        const val CHATTING_IMAGE_BG_VIEW = "com.tencent.mm.ui.chatting.ChattingImageBGView"
        const val NESTED_BOUNCE_VIEW = "com.tencent.mm.ui.widget.pulldown.NestedBounceView"
        const val BOUNCE_VIEW = "com.tencent.mm.ui.widget.pulldown.WeUIBounceViewV2"
        const val APP_BRAND_DESKTOP_CONTAINER =
            "com.tencent.mm.plugin.appbrand.widget.desktop.AppBrandDesktopContainerView"
        const val CHAT_FOOTER_CUSTOM = "com.tencent.mm.ui.chatting.ChatFooterCustom"
        const val MAIN_FRAGMENT = "com.tencent.mm.ui.conversation.MainUI"
        const val CONTACTS_FRAGMENT = "com.tencent.mm.ui.contact.address.MvvmAddressUIFragment"
        const val DISCOVER_FRAGMENT = "com.tencent.mm.ui.FindMoreFriendsUI"
        const val ME_FRAGMENT = "com.tencent.mm.ui.MoreTabUI"
        const val METHOD_CACHE_PREFS = "Hchat_background_beauty_method_cache"
        const val CACHE_CHAT_BACKGROUND_COMPONENT = "chat_background_component_init"
        const val CHAT_BACKGROUND_COMPONENT_TAG = "MicroMsg.ChattingUI.ChattingBackgroundComponent"
        const val CHAT_BACKGROUND_COMPONENT_INIT = "initBackground:"
        const val MAX_INITIAL_REFRESHES = 2
        const val MAX_LAUNCHER_BITMAPS = 4
        const val MAX_SCAN_NODES = 1200
        const val TAB_WECHAT = 0
        const val TAB_CONTACTS = 1
        const val TAB_DISCOVER = 2
        const val TAB_ME = 3
        const val IMMERSIVE_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    private data class LoadRequest(
        val slot: BackgroundBeautySettings.Slot,
        val revision: Int
    )

    private data class LoadedBitmap(
        val revision: Int,
        val bitmap: Bitmap
    )

    private class SurfaceSnapshot(view: View) {
        var originalBackground: Drawable? = view.background
        var originalTintList: ColorStateList? = view.backgroundTintList
        var originalTintMode: PorterDuff.Mode? = view.backgroundTintMode
        var originalForeground: Drawable? = view.foreground
        var originalPaddingLeft: Int = view.paddingLeft
        var originalPaddingTop: Int = view.paddingTop
        var originalPaddingRight: Int = view.paddingRight
        var originalPaddingBottom: Int = view.paddingBottom
        var originalMinimumWidth: Int = view.minimumWidth
        var originalMinimumHeight: Int = view.minimumHeight
        var appliedBackground: Drawable? = null
        var appliedForeground: Drawable? = null
        var appliedBitmap: Bitmap? = null
        var appliedOpacity: Float? = null
        var slot: BackgroundBeautySettings.Slot? = null
    }

    private class NativeImageSnapshot(view: ImageView) {
        var originalDrawable: Drawable? = view.drawable
        val originalAlpha: Float = view.alpha
        val originalScaleType: ImageView.ScaleType = view.scaleType
        var originalTintList: ColorStateList? = view.imageTintList
        var appliedDrawable: Drawable? = null
        var appliedAlpha: Float? = null
        var appliedScaleType: ImageView.ScaleType? = null
        var appliedBitmap: Bitmap? = null
        var appliedOpacity: Float? = null
        var slot: BackgroundBeautySettings.Slot? = null
    }

    private class HostState(val activity: Activity) {
        val surfaces = IdentityHashMap<View, SurfaceSnapshot>()
        val nativeImages = IdentityHashMap<ImageView, NativeImageSnapshot>()
        val loadedBitmaps = LinkedHashMap<BackgroundBeautySettings.Slot, LoadedBitmap>(4, 0.75f, true)
        val originalSystemUiVisibility = activity.window.decorView.systemUiVisibility
        var originalStatusBarColor = activity.window.statusBarColor
        var originalNavigationBarColor = activity.window.navigationBarColor
        val originalNavigationBarDividerColor = if (android.os.Build.VERSION.SDK_INT >= 28) {
            activity.window.navigationBarDividerColor
        } else null
        val originalNavigationBarContrast = if (android.os.Build.VERSION.SDK_INT >= 29) {
            activity.window.isNavigationBarContrastEnforced
        } else null
        var systemBarsApplied = false
        var lastLoggedSlot: BackgroundBeautySettings.Slot? = null
    }

    private val prefs = HchatStorage.preferences(
        context.hostContext(),
        BackgroundBeautySettings.PREFS_NAME
    )
    private val methodPrefs = DexMethodCache.prefs(context.hostContext(), METHOD_CACHE_PREFS)
    private val main = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Hchat-BackgroundBeauty").apply { isDaemon = true }
    }
    private val observedActivities = Collections.synchronizedMap(WeakHashMap<Activity, Boolean>())
    private val states = Collections.synchronizedMap(WeakHashMap<Activity, HostState>())
    private val pendingLoads = Collections.synchronizedMap(WeakHashMap<Activity, LoadRequest>())
    private val pendingRefreshes = Collections.synchronizedMap(WeakHashMap<Activity, Runnable>())
    private val fragmentRoots = Collections.synchronizedMap(
        WeakHashMap<Any, WeakReference<View>>()
    )
    private val nativeChatImages = Collections.synchronizedMap(
        WeakHashMap<Activity, WeakReference<ImageView>>()
    )
    private val launcherTabIndices = Collections.synchronizedMap(WeakHashMap<Activity, Int>())
    private val layoutListeners = Collections.synchronizedMap(
        WeakHashMap<View, View.OnLayoutChangeListener>()
    )
    private val hookedMembers = ConcurrentHashMap.newKeySet<Member>()
    private val ownedHooks = java.util.concurrent.CopyOnWriteArrayList<XC_MethodHook.Unhook>()
    private var surfaceMutationDepth = 0
    private val surfaceOwners = WeakHashMap<View, HostState>()
    private val diagnosticCounts = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
    private val diagnosedLists = WeakHashMap<View, Boolean>()
    private val listAdapters = WeakHashMap<View, Any>()
    private val bounceColors = WeakHashMap<View, MutableMap<java.lang.reflect.Method, Any?>>()
    private var restoringBounce = false
    private val homeLists = WeakHashMap<View, BackgroundBeautySettings.Slot>()
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == BackgroundBeautySettings.KEY_ENABLE ||
            key == BackgroundBeautySettings.KEY_REVISION ||
            BackgroundBeautySettings.Slot.values().any { slot ->
                key == BackgroundBeautySettings.opacityKey(slot)
            }
        ) {
            main.post(::refreshAll)
        }
    }

    @Volatile
    private var destroyed = false

    fun install(): Boolean {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        val installed = installActivityHooks() or
            installTabHooks() or
            installFragmentHooks() or
            installSurfaceHooks() or installBackgroundWriteHook()
        installChatBackgroundComponentHook(allowDexSearch = false)
        if (!installed) HLog.e("$TAG 未安装任何页面 Hook")
        main.post(::refreshAll)
        return installed
    }

    fun destroy() {
        destroyed = true
        ownedHooks.toList().forEach { runCatching { HookRegistry.get().unhook(it) } }
        ownedHooks.clear()
        hookedMembers.clear()
        restoreBounceColors(null)
        homeLists.clear()
        listAdapters.clear()
        surfaceOwners.clear()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        main.removeCallbacksAndMessages(null)
        loader.shutdownNow()
        val activeStates = synchronized(states) { states.values.toList().also { states.clear() } }
        activeStates.forEach(::restoreState)
        synchronized(layoutListeners) {
            layoutListeners.forEach { (view, listener) -> view.removeOnLayoutChangeListener(listener) }
            layoutListeners.clear()
        }
        observedActivities.clear()
        pendingLoads.clear()
        pendingRefreshes.clear()
        fragmentRoots.clear()
        nativeChatImages.clear()
        launcherTabIndices.clear()
    }

    private fun installActivityHooks(): Boolean {
        var installed = false
        listOf(CHAT_ACTIVITY, LAUNCHER_ACTIVITY).forEach { className ->
            val clazz = KavaReflector.loadClass(className, context.hostClassLoader())
                ?: return@forEach
            KavaReflector.findMethodRecursive(clazz, "onCreate", Bundle::class.java)?.let { method ->
                installed = hookOnce(method, after = { param ->
                    (param.thisObject as? Activity)?.let { scheduleRefresh(it, MAX_INITIAL_REFRESHES) }
                }) || installed
            }
            KavaReflector.findMethodRecursive(clazz, "onResume")?.let { method ->
                installed = hookOnce(method, after = { param ->
                    (param.thisObject as? Activity)?.let { scheduleRefresh(it, MAX_INITIAL_REFRESHES) }
                }) || installed
            }
            KavaReflector.findMethodRecursive(
                clazz,
                "onWindowFocusChanged",
                Boolean::class.javaPrimitiveType!!
            )?.let { method ->
                installed = hookOnce(method, after = { param ->
                    if (param.args.firstOrNull() == true) {
                        (param.thisObject as? Activity)?.let { scheduleRefresh(it, 1) }
                    }
                }) || installed
            }
            KavaReflector.findMethodRecursive(clazz, "onDestroy")?.let { method ->
                installed = hookOnce(method, before = { param ->
                    (param.thisObject as? Activity)?.let(::forgetActivity)
                }) || installed
            }
        }
        return installed
    }

    private fun installTabHooks(): Boolean {
        var installed = false
        val bottomTab = KavaReflector.loadClass(BOTTOM_TAB_CLASS, context.hostClassLoader())
        KavaReflector.findMethodRecursive(
            bottomTab,
            "setTo",
            Int::class.javaPrimitiveType!!
        )?.let { method ->
            installed = hookOnce(method, after = { param ->
                val index = param.args.firstOrNull() as? Int ?: return@hookOnce
                findActivity(param.thisObject)?.let { activity ->
                    synchronized(launcherTabIndices) { launcherTabIndices[activity] = index }
                    scheduleRefresh(activity)
                }
            }) || installed
        }
        val mainTab = KavaReflector.loadClass(MAIN_TAB_CLASS, context.hostClassLoader())
        KavaReflector.declaredMethods(mainTab)
            .filter { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.contentEquals(arrayOf(Integer.TYPE)) &&
                    method.name in setOf("a", "c")
            }
            .forEach { method ->
                installed = hookOnce(method, after = { param ->
                    val index = param.args.firstOrNull() as? Int ?: return@hookOnce
                    findActivity(param.thisObject)?.let { activity ->
                        synchronized(launcherTabIndices) { launcherTabIndices[activity] = index }
                        scheduleRefresh(activity)
                    }
                }) || installed
            }
        val tabsAdapter = KavaReflector.loadClass(
            "${MAIN_TAB_CLASS}\$TabsAdapter",
            context.hostClassLoader()
        )
        KavaReflector.findMethodRecursive(
            tabsAdapter,
            "onPageSelected",
            Int::class.javaPrimitiveType!!
        )?.let { method ->
            installed = hookOnce(method, after = { param ->
                val index = param.args.firstOrNull() as? Int ?: return@hookOnce
                findActivity(param.thisObject)?.let { activity ->
                    synchronized(launcherTabIndices) { launcherTabIndices[activity] = index }
                    scheduleRefresh(activity)
                }
            }) || installed
        }
        return installed
    }

    private fun installFragmentHooks(): Boolean {
        var installed = false
        listOf(MAIN_FRAGMENT, CONTACTS_FRAGMENT, DISCOVER_FRAGMENT, ME_FRAGMENT).forEach { className ->
            val clazz = KavaReflector.loadClass(className, context.hostClassLoader())
                ?: return@forEach
            KavaReflector.findMethodRecursive(clazz, "getLayoutView")?.let { method ->
                installed = hookOnce(method, after = { param ->
                    rememberFragmentRoot(param.thisObject, param.result as? View)
                }) || installed
            }
            KavaReflector.declaredMethods(clazz).firstOrNull { method ->
                method.name == "onViewCreated" &&
                    method.parameterTypes.contentEquals(
                        arrayOf(View::class.java, Bundle::class.java)
                    )
            }?.let { method ->
                installed = hookOnce(method, after = { param ->
                    rememberFragmentRoot(param.thisObject, param.args.firstOrNull() as? View)
                }) || installed
            }
            listOf("onResume", "onActivityCreated").forEach { name ->
                val parameters = if (name == "onActivityCreated") arrayOf(Bundle::class.java) else emptyArray()
                KavaReflector.findMethodRecursive(clazz, name, *parameters)?.let { method ->
                    installed = hookOnce(method, after = { param ->
                        rememberFragmentRoot(param.thisObject, null)
                    }) || installed
                }
            }
        }
        return installed
    }

    private fun installSurfaceHooks(): Boolean {
        var installed = false
        val chatImageClass = KavaReflector.loadClass(CHATTING_IMAGE_BG_VIEW, context.hostClassLoader())
        KavaReflector.declaredConstructors(chatImageClass).forEach { constructor ->
            installed = hookOnce(constructor, after = { param ->
                (param.thisObject as? ImageView)?.let(::rememberNativeChatImage)
            }) || installed
        }
        listOf(APP_BRAND_DESKTOP_CONTAINER, CHAT_FOOTER_CUSTOM).forEach { className ->
            val clazz = KavaReflector.loadClass(className, context.hostClassLoader())
            KavaReflector.declaredConstructors(clazz).forEach { constructor ->
                installed = hookOnce(constructor, after = { param ->
                    (param.thisObject as? View)?.let { view ->
                        view.post {
                            findActivity(view)?.let {
                                scheduleRefresh(it, MAX_INITIAL_REFRESHES)
                            }
                        }
                    }
                }) || installed
            }
        }
        listOf(BOUNCE_VIEW, NESTED_BOUNCE_VIEW).forEach { bounceName ->
        val bounceClass = KavaReflector.loadClass(bounceName, context.hostClassLoader())
        listOf(
            "setStart2EndBgColorByActionBar",
            "setEnd2StartBgColorByNavigationBar",
            "setStart2EndBgColor",
            "setEnd2StartBgColor",
            "setBgColor"
        ).forEach { name ->
            KavaReflector.findMethodRecursive(
                bounceClass,
                name,
                Int::class.javaPrimitiveType!!
            )?.let { method ->
                installed = hookOnce(method, before = { param ->
                    val view = param.thisObject as? View ?: return@hookOnce
                    val activity = findActivity(view) ?: return@hookOnce
                    if (!restoringBounce && surfaceMutationDepth == 0 && hasConfiguredBackground(activity)) {
                        if (diagnosticCounts.computeIfAbsent("bounce:$name") { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet() <= 4) {
                            HLog.i("$TAG DIAG bounce class=${view.javaClass.name} method=$name input=${param.args[0]}")
                            // No delayed tree traversal in a bounce animation.
                        }
                        recordBounceArgument(view, method, param.args[0])
                        param.args[0] = Color.TRANSPARENT
                    }
                }) || installed
            }
        }
        listOf("setStart2EndBg", "setEnd2StartBg", "setBg", "setMStart2EndBg", "setMEnd2StartBg",
            "setMStart2EndBgByActionBar", "setMEnd2StartBgByNavigationBar").forEach { name ->
            KavaReflector.findMethodRecursive(bounceClass, name, Drawable::class.java)?.let { method ->
                installed = hookOnce(method, before = { param ->
                    val view = param.thisObject as? View ?: return@hookOnce
                    val activity = findActivity(view) ?: return@hookOnce
                    if (!restoringBounce && surfaceMutationDepth == 0 && hasConfiguredBackground(activity)) {
                        recordBounceArgument(view, method, param.args[0])
                        param.args[0] = ColorDrawable(Color.TRANSPARENT)
                        if (diagnosticCounts.computeIfAbsent("bounce:$name") { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet() <= 4) {
                            HLog.i("$TAG DIAG bounce drawable class=${view.javaClass.name} method=$name")
                        }
                    }
                }) || installed
            }
        }
        }
        return installed
    }
    private fun recordBounceArgument(view: View, method: java.lang.reflect.Method, argument: Any?) {
        val properties = when (method.name) {
            "setStart2EndBgColor", "setStart2EndBg", "setMStart2EndBg" -> listOf("MStart2EndBg")
            "setEnd2StartBgColor", "setEnd2StartBg", "setMEnd2StartBg" -> listOf("MEnd2StartBg")
            "setStart2EndBgColorByActionBar", "setMStart2EndBgByActionBar" -> listOf("MStart2EndBgByActionBar")
            "setEnd2StartBgColorByNavigationBar", "setMEnd2StartBgByNavigationBar" -> listOf("MEnd2StartBgByNavigationBar")
            "setBg", "setBgColor" -> listOf("MStart2EndBg", "MEnd2StartBg")
            else -> emptyList()
        }
        val setters = properties.mapNotNull { property ->
            KavaReflector.findMethodRecursive(view.javaClass, "set$property", Drawable::class.java)
        }
        val values = bounceColors.getOrPut(view) { LinkedHashMap() }
        if (setters.size == properties.size && setters.isNotEmpty()) {
            setters.forEach { setter -> values[setter] = if (argument is Int) ColorDrawable(argument) else argument }
        } else values[method] = argument
    }
    private fun restoreBounceColors(activity: Activity?) {
        wechatDiagnosticListeners.entries.filter { activity == null || it.key === activity }.toList().forEach { (owner, listener) ->
            val observer = owner.window.decorView.viewTreeObserver
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
            wechatDiagnosticListeners.remove(owner)
            wechatDiagnosticFrames.remove(owner)
        }
        pendingPageRoots.keys.removeAll { activity == null || findActivity(it) === activity }
        preparedRoots.entries.removeAll { activity == null || it.value.first.activity === activity }
        listSelectors.entries.filter { activity == null || findActivity(it.key) === activity }.toList().forEach { (list, selector) ->
            if (list.selector is BackgroundBeautyFeedbackDrawable) list.selector = selector
            listSelectors.remove(list)
        }
        listPreDraws.entries.filter { activity == null || findActivity(it.key) === activity }.toList().forEach { (list, listener) ->
            val observer = list.viewTreeObserver
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
            listPreDraws.remove(list)
            clearListRipple(list)
        }
        val entries = bounceColors.entries.filter { activity == null || findActivity(it.key) === activity }
            .map { it.key to it.value.toMap() }
        restoringBounce = true
        try {
            entries.forEach { (view, colors) ->
                colors.forEach { (method, color) -> KavaReflector.invoke(method, view, color) }
                bounceColors.remove(view)
                initializedChrome.remove(view)
            }
        } finally { restoringBounce = false }
    }

    private fun installBackgroundWriteHook(): Boolean {
        var installed = false
        listOf("setBackgroundDrawable" to Drawable::class.java, "setBackgroundColor" to Integer.TYPE).forEach { (name, parameter) ->
            KavaReflector.findMethodRecursive(View::class.java, name, parameter)?.let { method ->
                installed = hookOnce(method, after = { param ->
                    if (surfaceMutationDepth != 0 || Looper.myLooper() != Looper.getMainLooper()) return@hookOnce
                    val view = param.thisObject as? View ?: return@hookOnce
                    val state = surfaceOwners[view] ?: return@hookOnce
                    val snapshot = state.surfaces[view] ?: return@hookOnce
                    if (view.background !== snapshot.appliedBackground) {
                        if (diagnosticCounts.computeIfAbsent("write:${view.javaClass.name}") { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet() <= 3) {
                            HLog.i("$TAG DIAG rewrite class=${view.javaClass.name} setter=$name background=${view.background?.javaClass?.name}")
                        }
                        if (resolveTarget(state.activity) == BackgroundBeautySettings.Slot.WECHAT &&
                            (isChromeSurface(view) || isSystemBarBackground(view) || view === state.activity.window.decorView)) {
                            val key = "wxwrite:${view.javaClass.name}:$name"
                            if (diagnosticCounts.computeIfAbsent(key) { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet() <= 2) {
                                HLog.i("$TAG WXWRITE class=${view.javaClass.name} method=$name trace=${Throwable().stackTrace.take(16).joinToString(" > ")}")
                            }
                            armWechatDiagnostics(state.activity, "write:${view.javaClass.name}")
                        }
                        val applied = snapshot.appliedBackground
                        snapshot.originalBackground = view.background
                        snapshot.originalTintList = view.backgroundTintList
                        snapshot.originalTintMode = view.backgroundTintMode
                        if (applied != null && prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE) &&
                            snapshot.slot?.let { BackgroundBeautyStore.hasImage(context.hostContext(), it) } == true) {
                            surfaceMutationDepth++
                            try {
                                view.backgroundTintList = null
                                view.backgroundTintMode = null
                                view.background = applied
                                restoreSurfaceGeometry(view, snapshot)
                            } finally { surfaceMutationDepth-- }
                        }
                        if (applied == null) scheduleRefresh(state.activity)
                    }
                }) || installed
            }
        }
        listOf("setStatusBarColor", "setNavigationBarColor").forEach { name ->
            val windowClass = KavaReflector.loadClass("com.android.internal.policy.PhoneWindow", context.hostClassLoader())
            KavaReflector.findMethodRecursive(windowClass, name, Integer.TYPE)?.let { method ->
                hookOnce(method, before = { param ->
                    if (surfaceMutationDepth != 0 || restoringBounce) return@hookOnce
                    val window = param.thisObject as? android.view.Window ?: return@hookOnce
                    val activity = findActivity(window.decorView) ?: return@hookOnce
                    val slot = resolveTarget(activity) ?: return@hookOnce
                    if (slot != BackgroundBeautySettings.Slot.CHAT && states[activity]?.systemBarsApplied == true && hasConfiguredBackground(activity)) {
                        val hostColor = param.args[0] as? Int ?: return@hookOnce
                        if (hostColor != Color.TRANSPARENT) {
                            if (name == "setStatusBarColor") states[activity]?.originalStatusBarColor = hostColor
                            else states[activity]?.originalNavigationBarColor = hostColor
                        }
                        param.args[0] = Color.TRANSPARENT
                    }
                })
            }
        }
        return installed
    }
    private val dirtyBoundRows = WeakHashMap<View, Boolean>()
    private val listSelectors = WeakHashMap<AbsListView, Drawable?>()
    private val listPreDraws = WeakHashMap<View, ViewTreeObserver.OnPreDrawListener>()
    private val touchFeedbackRows = WeakHashMap<View, View>()
    private val touchFeedbackOrigins = WeakHashMap<View, Pair<Float, Float>>()
    private fun updateListRipple(list: ViewGroup, event: android.view.MotionEvent) {
        val slot = homeLists[list] ?: return
        val activity = findActivity(list) ?: return
        if (!prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE) || states[activity] == null ||
            !BackgroundBeautyStore.hasImage(context.hostContext(), slot)) return
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                for (index in list.childCount - 1 downTo 0) {
                    val row = list.getChildAt(index)
                    val x = event.x + list.scrollX - row.left - row.translationX
                    val y = event.y + list.scrollY - row.top - row.translationY
                    if (row.visibility == View.VISIBLE && x >= 0 && y >= 0 && x < row.width && y < row.height) {
                        applyBoundRow(list, row)
                        (row.background as? BackgroundBeautyFeedbackDrawable)?.let { ripple ->
                            ripple.setHotspot(x, y)
                            ripple.setTouchPressed(true)
                            if (diagnosticCounts.computeIfAbsent("ripple:${slot.key}") { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet() <= 4) {
                                HLog.i("$TAG DIAG ripple slot=${slot.key} row=${row.javaClass.name} hotspot=$x/$y")
                            }
                            touchFeedbackRows[list] = row
                            touchFeedbackOrigins[list] = event.x to event.y
                        }
                        break
                    }
                }
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val origin = touchFeedbackOrigins[list] ?: return
                val slop = android.view.ViewConfiguration.get(list.context).scaledTouchSlop
                if (kotlin.math.abs(event.x - origin.first) > slop || kotlin.math.abs(event.y - origin.second) > slop) clearListRipple(list)
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> clearListRipple(list)
        }
    }
    private fun clearListRipple(list: View) {
        (touchFeedbackRows.remove(list)?.background as? BackgroundBeautyFeedbackDrawable)?.setTouchPressed(false)
        touchFeedbackOrigins.remove(list)
    }
    private fun installRecyclerBinding(view: View) {
        KavaReflector.findMethodRecursive(view.javaClass, "dispatchTouchEvent", android.view.MotionEvent::class.java)?.let { method ->
            hookOnce(method, before = { param ->
                val list = param.thisObject as? ViewGroup ?: return@hookOnce
                val event = param.args.firstOrNull() as? android.view.MotionEvent ?: return@hookOnce
                updateListRipple(list, event)
            })
        }
        if (view is ViewGroup && !listPreDraws.containsKey(view)) {
            val listener = ViewTreeObserver.OnPreDrawListener {
                if (!destroyed && view.isShown) {
                    if (view.childCount > 0) {
                        val ready = pendingPageRoots.keys.firstOrNull { isDescendantOf(view, it) }
                        if (ready != null) {
                            pendingPageRoots.remove(ready)
                            findActivity(view)?.let { activity ->
                                val state = states[activity]
                                val slot = homeLists[view]
                                if (state != null && slot != null) preparePage(state, ready, slot)
                            }
                        }
                    }
                    for (index in 0 until view.childCount) {
                        val row = view.getChildAt(index)
                        val owner = findActivity(view)?.let { states[it] }
                        if (dirtyBoundRows.remove(row) == true || owner?.surfaces?.get(row) == null) applyBoundRow(view, row)
                    }
                }
                true
            }
            listPreDraws[view] = listener
            view.viewTreeObserver.addOnPreDrawListener(listener)
        }
        if (view is AbsListView) {
            if (!listSelectors.containsKey(view)) {
                listSelectors[view] = view.selector
                view.selector = BackgroundBeautyFeedbackDrawable()
            }
            val adapter = view.adapter ?: return
            if (listAdapters[view] === adapter) return
            listAdapters[view] = adapter
            KavaReflector.findMethodRecursive(adapter.javaClass, "getView", Integer.TYPE, View::class.java, ViewGroup::class.java)?.let { method ->
                hookOnce(method, after = { param ->
                    val list = param.args.getOrNull(2) as? View ?: return@hookOnce
                    val row = param.result as? View ?: return@hookOnce
                    applyBoundRow(list, row)
                })
            }
            return
        }
        if (!isMajorSurface(view) || view is ScrollView) return
        val adapter = KavaReflector.findMethodRecursive(view.javaClass, "getAdapter")?.let { KavaReflector.invoke(it, view) }
        if (adapter != null && listAdapters[view] !== adapter) {
            listAdapters[view] = adapter
            installRecyclerAdapter(adapter)
        }
        var type: Class<*>? = view.javaClass
        while (type != null && type != ViewGroup::class.java) {
            KavaReflector.declaredMethods(type).filter { method ->
                method.name == "dispatchChildAttached" &&
                    method.parameterTypes.contentEquals(arrayOf(View::class.java))
            }.forEach { method ->
                hookOnce(method, after = { param ->
                    val list = param.thisObject as? View ?: return@hookOnce
                    val row = param.args.firstOrNull() as? View ?: return@hookOnce
                    applyBoundRow(list, row)
                })
            }
            type = type.superclass
        }
    }

    private fun installRecyclerAdapter(adapter: Any) {
        var type: Class<*>? = adapter.javaClass
        while (type != null && type != Any::class.java) {
            KavaReflector.declaredMethods(type).filter { method ->
                method.name == "onBindViewHolder" && !java.lang.reflect.Modifier.isAbstract(method.modifiers) &&
                    method.parameterTypes.size in 2..3 &&
                    method.parameterTypes.getOrNull(1) == Integer.TYPE
            }.forEach { method ->
                hookOnce(method, after = { param ->
                    val holder = param.args.firstOrNull() ?: return@hookOnce
                    val row = KavaReflector.readField(holder, "itemView") as? View ?: return@hookOnce
                    dirtyBoundRows[row] = true
                    val list = row.parent as? View
                    if (list != null) applyBoundRow(list, row)
                })
            }
            type = type.superclass
        }
    }

    private fun applyBoundRow(list: View, row: View) {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        val slot = homeLists[list] ?: return
        val activity = findActivity(list) ?: return
        if (!prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE) ||
            !BackgroundBeautyStore.hasImage(context.hostContext(), slot)) return
        val state = states[activity] ?: return
        applyHomeRow(state, row, slot)
    }

    private fun applyHomeRow(state: HostState, row: View, slot: BackgroundBeautySettings.Slot) {
        state.surfaces[row]?.let { snapshot ->
            snapshot.originalPaddingLeft = row.paddingLeft
            snapshot.originalPaddingTop = row.paddingTop
            snapshot.originalPaddingRight = row.paddingRight
            snapshot.originalPaddingBottom = row.paddingBottom
            snapshot.originalMinimumWidth = row.minimumWidth
            snapshot.originalMinimumHeight = row.minimumHeight
        }
        setTransparentSurface(state, row, slot, preserveFeedback = true)
        val queue = ArrayDeque<View>()
        if (row is ViewGroup) for (i in 0 until row.childCount) queue.add(row.getChildAt(i))
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 160) {
            val child = queue.removeFirst()
            if (isComposeSurface(child) || isBottomBar(child)) continue
            if (child is ViewGroup) {
                if (child.background != null) setTransparentSurface(state, child, slot, preserveFeedback = true)
                for (i in 0 until child.childCount) queue.add(child.getChildAt(i))
            }
        }
    }



    @Synchronized
    fun installChatBackgroundComponentHook(allowDexSearch: Boolean): Boolean {
        if (destroyed) return false
        val runtimeKey = DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader())
        val cached = DexMethodCache.load(
            methodPrefs,
            runtimeKey,
            context.hostClassLoader(),
            CACHE_CHAT_BACKGROUND_COMPONENT
        )?.takeIf { it.returnType == Void.TYPE && it.parameterTypes.isEmpty() }
        val method = cached ?: run {
            DexMethodCache.clear(methodPrefs, runtimeKey, CACHE_CHAT_BACKGROUND_COMPONENT)
            if (!allowDexSearch) return false
            val candidates = runCatching {
                context.dexKitBridge().findMethod(FindMethod().apply {
                    matcher(MethodMatcher().apply {
                        usingStrings(
                            listOf(
                                CHAT_BACKGROUND_COMPONENT_TAG,
                                CHAT_BACKGROUND_COMPONENT_INIT
                            )
                        )
                    })
                }).asSequence()
                    .mapNotNull { data ->
                        runCatching { data.getMethodInstance(context.hostClassLoader()) }.getOrNull()
                    }
                    .filter { candidate ->
                        candidate.returnType == Void.TYPE && candidate.parameterTypes.isEmpty()
                    }
                    .distinctBy { candidate -> candidate.toGenericString() }
                    .toList()
            }.getOrElse {
                HLog.e("$TAG 定位聊天原生背景初始化失败: ${it.message}", it)
                emptyList()
            }
            val located = candidates.singleOrNull()
            if (located == null) {
                HLog.e("$TAG 未找到唯一聊天原生背景初始化入口: count=${candidates.size}")
                return false
            }
            DexMethodCache.save(methodPrefs, runtimeKey, CACHE_CHAT_BACKGROUND_COMPONENT, located)
            located
        }
        return hookOnce(method, after = { param -> bindNativeChatBackground(param.thisObject) })
    }

    private fun bindNativeChatBackground(component: Any?) {
        component ?: return
        val backgroundId = resourceId("bka")
        val imageView = resolveComponentChatImage(component, backgroundId) ?: return
        rememberNativeChatImage(imageView)
    }

    private fun resolveComponentChatImage(component: Any, backgroundId: Int): ImageView? {
        if (backgroundId != 0) {
            val viewOwner = KavaReflector.readField(component, "d")
            val lookup = viewOwner?.let { owner ->
                KavaReflector.findMethodRecursive(owner.javaClass, "c", Integer.TYPE)
                    ?.takeIf { View::class.java.isAssignableFrom(it.returnType) }
            }
            val resolved = if (viewOwner != null && lookup != null) {
                KavaReflector.invoke(lookup, viewOwner, backgroundId) as? View
            } else null
            if (resolved is ImageView) return resolved
            resolved?.findViewById<ImageView>(backgroundId)?.let { return it }
        }
        val nativeBackground = KavaReflector.readField(component, "f") as? ImageView
        if (nativeBackground != null &&
            ((backgroundId != 0 && nativeBackground.id == backgroundId) ||
                hasClassName(nativeBackground, CHATTING_IMAGE_BG_VIEW))
        ) {
            return nativeBackground
        }
        return null
    }

    private fun rememberNativeChatImage(imageView: ImageView) {
        val activity = findActivity(imageView) ?: return
        synchronized(nativeChatImages) { nativeChatImages[activity] = WeakReference(imageView) }
        imageView.post { scheduleRefresh(activity, MAX_INITIAL_REFRESHES) }
    }

    private fun rememberFragmentRoot(fragment: Any?, supplied: View?) {
        if (fragment == null || slotForFragment(fragment) == null) return
        val root = supplied ?: fragmentView(fragment) ?: return
        synchronized(fragmentRoots) { fragmentRoots[fragment] = WeakReference(root) }
        preparedRoots.remove(root)
        observeLayout(root)
        val activity = findActivity(fragment) ?: findActivity(root) ?: return
        scheduleRefresh(activity, MAX_INITIAL_REFRESHES)
    }

    private fun observeLayout(view: View) {
        if (view.isLaidOut && view.width > 0 && view.height > 0) return
        synchronized(layoutListeners) {
            if (layoutListeners.containsKey(view)) return
            val listener = View.OnLayoutChangeListener { target, _, _, _, _, _, _, _, _ ->
                synchronized(layoutListeners) {
                    layoutListeners.remove(target)?.let(target::removeOnLayoutChangeListener)
                }
                if (!destroyed) findActivity(target)?.let { scheduleRefresh(it) }
            }
            layoutListeners[view] = listener
            view.addOnLayoutChangeListener(listener)
        }
    }

    private fun hookOnce(
        member: Member,
        before: ((XC_MethodHook.MethodHookParam) -> Unit)? = null,
        after: ((XC_MethodHook.MethodHookParam) -> Unit)? = null
    ): Boolean {
        if (!hookedMembers.add(member)) return true
        return runCatching {
            ownedHooks += HookRegistry.get().hook(member, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!destroyed) before?.invoke(param)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!destroyed) after?.invoke(param)
                }
            })
            true
        }.getOrElse {
            hookedMembers.remove(member)
            HLog.e("$TAG Hook 安装失败: $member", it)
            false
        }
    }

    private fun scheduleRefresh(activity: Activity, retries: Int = 0) {
        if (destroyed || activity.isFinishing || activity.isDestroyed) return
        synchronized(observedActivities) { observedActivities[activity] = true }
        val task = synchronized(pendingRefreshes) {
            if (pendingRefreshes.containsKey(activity)) return@synchronized null
            Runnable {
                synchronized(pendingRefreshes) { pendingRefreshes.remove(activity) }
                val started = android.os.SystemClock.uptimeMillis()
                applyForActivity(activity)
                val elapsed = android.os.SystemClock.uptimeMillis() - started
                if (elapsed >= 16 && diagnosticCounts.computeIfAbsent("slow-refresh") { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet() <= 12) {
                    HLog.i("$TAG DIAG slowRefresh elapsedMs=$elapsed")
                }
                if (retries > 0 && !destroyed) {
                    main.postDelayed({ scheduleRefresh(activity, retries - 1) }, 120L)
                }
            }.also { pendingRefreshes[activity] = it }
        } ?: return
        activity.window.decorView.postOnAnimation(task)
    }

    private fun refreshAll() {
        if (destroyed) return
        runCatching { WeChatApis.currentActivity()?.currentActivity() }
            .getOrNull()
            ?.takeUnless { it.isFinishing || it.isDestroyed }
            ?.let { activity -> synchronized(observedActivities) { observedActivities[activity] = true } }
        val activities = synchronized(observedActivities) { observedActivities.keys.toList() }
        activities.forEach { activity ->
            if (activity.isFinishing || activity.isDestroyed) forgetActivity(activity)
            else scheduleRefresh(activity, MAX_INITIAL_REFRESHES)
        }
    }

    private var launcherChatActive: Boolean? = null
    fun onChatPageChanged(entered: Boolean) {
        main.post {
            launcherChatActive = entered
            val launchers = synchronized(observedActivities) { observedActivities.keys.filter { it.javaClass.name == LAUNCHER_ACTIVITY } }
            launchers.forEach { activity ->
                HLog.i("$TAG DIAG chatTransition entered=$entered")
                if (!entered) {
                    preparedRoots.entries.removeAll { it.value.first.activity === activity }
                    scheduleRefresh(activity, 1)
                }
            }
        }
    }
    private fun applyForActivity(activity: Activity) {
        if (destroyed || activity.isFinishing || activity.isDestroyed) return
        if (!prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE)) {
            clearActivity(activity)
            return
        }
        val slot = resolveTarget(activity) ?: run {
            clearActivity(activity)
            return
        }
        if (!BackgroundBeautyStore.hasImage(context.hostContext(), slot)) {
            // LauncherUI keeps homepage behind its embedded chatting Fragment. Do not restore it during navigation.
            if (slot == BackgroundBeautySettings.Slot.CHAT && activity.javaClass.name == LAUNCHER_ACTIVITY) return
            restoreBounceColors(activity)
            synchronized(states) { states[activity] }?.let { state ->
                restoreSlot(state, slot)
                restoreSharedSurfaces(state)
                restoreSystemBars(state)
            }
            return
        }
        val revision = prefs.getInt(BackgroundBeautySettings.KEY_REVISION, 0)
        val state = ensureState(activity)
        state.loadedBitmaps[slot]?.takeIf { it.revision == revision }?.let { loaded ->
            applyBitmap(state, slot, loaded.bitmap)
            return
        }
        val request = LoadRequest(slot, revision)
        if (synchronized(pendingLoads) { pendingLoads[activity] } == request) return
        synchronized(pendingLoads) { pendingLoads[activity] = request }
        loader.execute {
            val bitmap = BackgroundBeautyStore.loadBitmap(context.hostContext(), slot)
            main.post {
                if (synchronized(pendingLoads) { pendingLoads[activity] } != request) return@post
                synchronized(pendingLoads) { pendingLoads.remove(activity) }
                if (bitmap == null || destroyed || activity.isFinishing || activity.isDestroyed) return@post
                if (!prefs.getBoolean(
                        BackgroundBeautySettings.KEY_ENABLE,
                        BackgroundBeautySettings.DEFAULT_ENABLE
                    ) ||
                    resolveTarget(activity) != slot ||
                    !BackgroundBeautyStore.hasImage(context.hostContext(), slot)
                ) return@post
                val currentState = ensureState(activity)
                currentState.loadedBitmaps[slot] = LoadedBitmap(revision, bitmap)
                trimLauncherBitmaps(currentState, slot)
                applyBitmap(currentState, slot, bitmap)
            }
        }
    }

    private fun ensureState(activity: Activity): HostState = synchronized(states) {
        states[activity] ?: HostState(activity).also { states[activity] = it }
    }

    private fun trimLauncherBitmaps(
        state: HostState,
        current: BackgroundBeautySettings.Slot
    ) {
        if (current == BackgroundBeautySettings.Slot.CHAT) return
        while (state.loadedBitmaps.keys.count { it != BackgroundBeautySettings.Slot.CHAT } >
            MAX_LAUNCHER_BITMAPS
        ) {
            val eldest = state.loadedBitmaps.keys.firstOrNull {
                it != current && it != BackgroundBeautySettings.Slot.CHAT
            } ?: return
            state.loadedBitmaps.remove(eldest)
        }
    }

    private fun applyBitmap(
        state: HostState,
        slot: BackgroundBeautySettings.Slot,
        bitmap: Bitmap
    ) {
        if (bitmap.isRecycled) return
        state.surfaces.entries.toList().forEach { (view, snapshot) ->
            if (!view.isAttachedToWindow) {
                restoreSurface(view, snapshot)
                state.surfaces.remove(view)
            }
        }
        val activity = state.activity
        val opacity = BackgroundBeautySettings.opacity(
            prefs.getFloat(
                BackgroundBeautySettings.opacityKey(slot),
                BackgroundBeautySettings.DEFAULT_OPACITY
            )
        )
        val host = resolveBackgroundHost(activity) ?: return
        if (slot == BackgroundBeautySettings.Slot.CHAT) {
            setImageSurface(state, host, slot, bitmap, opacity)
        } else {
            setImageSurface(state, activity.window.decorView, slot, bitmap, opacity)
            if (host !== activity.window.decorView) setTransparentSurface(state, host, slot)
        }
        val content = activity.findViewById<View>(android.R.id.content)
        if (content != null && content !== host) setTransparentSurface(state, content, slot)
        if (slot == BackgroundBeautySettings.Slot.CHAT) {
            applyChatBackground(state, bitmap, opacity)
        } else {
            applyLauncherBackground(state, slot, bitmap, opacity)
        }
        applyChromeTransparency(state, slot)
        applySystemBars(state)
        if (state.lastLoggedSlot != slot) {
            state.lastLoggedSlot = slot
            // Full tree/frame diagnostics removed after capturing 66fe209 evidence.
            HLog.i("$TAG 页面背景已应用: slot=${slot.key} surfaces=${state.surfaces.size} lists=${homeLists.size} singleWindowImage=${slot != BackgroundBeautySettings.Slot.CHAT}")
        }
    }

    private val pendingPageRoots = WeakHashMap<View, BackgroundBeautySettings.Slot>()
    private fun isDescendantOf(view: View, root: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === root) return true
            current = current.parent as? View
        }
        return false
    }
    private val preparedRoots = WeakHashMap<View, Pair<HostState, Int>>()
    private fun preparePage(state: HostState, root: View, slot: BackgroundBeautySettings.Slot) {
        val revision = prefs.getInt(BackgroundBeautySettings.KEY_REVISION, 0)
        val prepared = preparedRoots[root]
        if (prepared?.first === state && prepared.second == revision) return
        val started = android.os.SystemClock.uptimeMillis()
        applyWhitelistedSurfaces(state, root, slot)
        val pendingList = homeLists.entries.any { (list, listSlot) ->
            listSlot == slot && list is ViewGroup && list.childCount == 0 && isDescendantOf(list, root)
        }
        if (!pendingList) preparedRoots[root] = state to revision
        else {
            pendingPageRoots[root] = slot
        }
        if (diagnosticCounts.computeIfAbsent("prepare:${slot.key}") { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet() <= 4) {
            HLog.i("$TAG DIAG prepare slot=${slot.key} elapsedMs=${android.os.SystemClock.uptimeMillis() - started}")
        }
    }
    private fun applyLauncherBackground(
        state: HostState,
        slot: BackgroundBeautySettings.Slot,
        bitmap: Bitmap,
        opacity: Float
    ) {
        val roots = LinkedHashSet<View>()
        currentFragment(state.activity)
            ?.takeIf { fragment -> slotForFragment(fragment) == slot }
            ?.let(::fragmentView)
            ?.takeIf { root -> findActivity(root) === state.activity }
            ?.let(roots::add)
        synchronized(fragmentRoots) {
            fragmentRoots.forEach { (fragment, reference) ->
                if (slotForFragment(fragment) == slot) {
                    reference.get()
                        ?.takeIf { root -> findActivity(root) === state.activity }
                        ?.let(roots::add)
                }
            }
        }
        roots.filter { it.isAttachedToWindow }.forEach { root ->
            observeLayout(root)
            setTransparentSurface(state, root, slot)
            var parent = root.parent as? View
            val host = resolveBackgroundHost(state.activity)
            while (parent != null && parent !== host && parent !== state.activity.window.decorView) {
                setTransparentSurface(state, parent, slot)
                parent = parent.parent as? View
            }
            preparePage(state, root, slot)
        }
        val neighbors = synchronized(fragmentRoots) { fragmentRoots.entries.map { it.key to it.value.get() } }
        neighbors.forEach { (fragment, root) ->
            val neighborSlot = slotForFragment(fragment) ?: return@forEach
            if (neighborSlot != slot && root != null && root.isAttachedToWindow && findActivity(root) === state.activity &&
                BackgroundBeautyStore.hasImage(context.hostContext(), neighborSlot)) {
                setTransparentSurface(state, root, neighborSlot)
                preparePage(state, root, neighborSlot)
            }
        }
    }

    private fun applyChatBackground(
        state: HostState,
        bitmap: Bitmap,
        opacity: Float
    ) {
        val activity = state.activity
        val image = synchronized(nativeChatImages) {
            nativeChatImages[activity]?.get()?.takeIf { it.isAttachedToWindow }
        } ?: findDescendant(
            activity.findViewById(android.R.id.content),
            CHATTING_IMAGE_BG_VIEW
        ) as? ImageView
        if (image != null) setNativeImage(state, image, bitmap, opacity)
        activity.findViewById<View>(android.R.id.content)?.let { root ->
            applyWhitelistedSurfaces(state, root, BackgroundBeautySettings.Slot.CHAT)
        }
    }

    private fun applyWhitelistedSurfaces(
        state: HostState,
        root: View,
        slot: BackgroundBeautySettings.Slot
    ) {
        val targetIds = targetResourceIds(slot)
        val queue = ArrayDeque<View>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_SCAN_NODES) {
            val view = queue.removeFirst()
            val name = view.javaClass.name
            if (view !== root && isBottomBar(view)) continue
            if (view !== root && isComposeSurface(view)) continue
            if (name == "com.tencent.mm.ui.FrostedContentView" || name == APP_BRAND_DESKTOP_CONTAINER ||
                hasClassName(view, BOUNCE_VIEW) || hasClassName(view, NESTED_BOUNCE_VIEW) ||
                name == CHAT_FOOTER_CUSTOM ||
                (view !== root && isMajorSurface(view))
            ) {
                if (!hasClassName(view, CHATTING_IMAGE_BG_VIEW)) {
                    setTransparentSurface(state, view, slot)
                }
            }
            if (isHomeContentList(view, slot)) {
                homeLists[view] = slot
                if (diagnosedLists.put(view, true) == null) {
                    HLog.i("$TAG 主页列表: slot=${slot.key} class=${view.javaClass.name} children=${(view as? ViewGroup)?.childCount ?: 0}")
                }
                installRecyclerBinding(view)
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) applyHomeRow(state, view.getChildAt(index), slot)
                }
            }
            if (slot == BackgroundBeautySettings.Slot.WECHAT && view is android.widget.TextView &&
                view.text?.toString()?.trim() == "折叠置顶聊天") {
                val parent = view.parent as? ViewGroup
                if (parent != null && parent !== root) setTransparentSurface(state, parent, slot, preserveFeedback = true)
            }
            if (slot == BackgroundBeautySettings.Slot.CONTACTS && view.id == resourceId("imk")) {
                // 运行快照确认该页 imk 外层 LinearLayout 仍绘制 #FF232323。
                val parent = view.parent as? View
                val container = parent?.parent as? View
                if (container is android.widget.LinearLayout && (container.background as? ColorDrawable)?.color == 0xFF232323.toInt()) {
                    setTransparentSurface(state, container, slot)
                }
            }
            if (view.id in targetIds) {
                setTransparentSurface(state, view, slot, clearForeground = true)
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    queue.addLast(view.getChildAt(index))
                }
            }
        }
    }

    private fun applyChromeTransparency(
        state: HostState,
        slot: BackgroundBeautySettings.Slot
    ) {
        val host = resolveBackgroundHost(state.activity)
        val cached = state.surfaces.keys.filter { view ->
            view !== host && view.isAttachedToWindow && isChromeSurface(view)
        }
        cached.forEach { view -> setTransparentSurface(state, view, slot) }
        val decor = state.activity.window.decorView
        val queue = ArrayDeque<View>()
        queue.add(decor)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_SCAN_NODES) {
            val view = queue.removeFirst()
            if (view !== decor && isBottomBar(view)) {
                if (slot != BackgroundBeautySettings.Slot.CHAT && view.tag != "Hchat:FloatingBottomBar") {
                    setTransparentSurface(state, view, slot)
                    // 微信 8.0.78 的底栏实色来自内部 LinearLayout，而非外层 RelativeLayout。
                    if (view.javaClass.name == "com.tencent.mm.ui.LauncherUIBottomTabView" && view is ViewGroup) {
                        for (index in 0 until view.childCount) {
                            val child = view.getChildAt(index)
                            if (child is android.widget.LinearLayout) {
                                setTransparentSurface(state, child, slot)
                                for (tabIndex in 0 until child.childCount) {
                                    val tab = child.getChildAt(tabIndex)
                                    if (tab is ViewGroup) setTransparentSurface(state, tab, slot, preserveFeedback = true)
                                }
                            }
                        }
                    }
                }
                continue
            }
            if (view !== decor && isComposeSurface(view)) continue
            if (view !== host && (isChromeSurface(view) || isSystemBarBackground(view))) {
                setTransparentSurface(state, view, slot)
            }
            // 页面内部已由 preparePage 和列表绑定处理，公共栏扫描不重复进入四页条目树。
            if (hasClassName(view, "com.tencent.mm.ui.base.CustomViewPager")) continue
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) queue.addLast(view.getChildAt(index))
            }
        }
    }

    private fun targetResourceIds(slot: BackgroundBeautySettings.Slot): Set<Int> {
        val names = when (slot) {
            BackgroundBeautySettings.Slot.WECHAT -> listOf("cj1", "og9", "nhm")
            BackgroundBeautySettings.Slot.ME -> listOf("gxp", "gxn", "hxi", "jlt", "imk", "s1d")
            BackgroundBeautySettings.Slot.CONTACTS, BackgroundBeautySettings.Slot.DISCOVER -> listOf("imk")
            else -> emptyList()
        }
        return names.mapNotNull { name -> resourceId(name).takeIf { it != 0 } }.toSet()
    }

    private fun isMajorSurface(view: View): Boolean {
        var type: Class<*>? = view.javaClass
        if (view is AbsListView || view is ScrollView) return true
        while (type != null) {
            if (type.name.contains("RecyclerView") || type.name.contains("NestedScrollView")) return true
            type = type.superclass
        }
        return false
    }

    private fun isHomeContentList(view: View, slot: BackgroundBeautySettings.Slot): Boolean {
        if (slot == BackgroundBeautySettings.Slot.CHAT) return false
        return when (slot) {
            BackgroundBeautySettings.Slot.WECHAT -> hasClassName(view, "com.tencent.mm.ui.conversation.ConversationListView") ||
                hasClassName(view, "com.tencent.mm.ui.conversation.ConversationRecyclerView")
            BackgroundBeautySettings.Slot.CONTACTS -> hasClassName(view, "com.tencent.mm.view.recyclerview.WxRecyclerView")
            BackgroundBeautySettings.Slot.DISCOVER, BackgroundBeautySettings.Slot.ME ->
                hasClassName(view, "com.tencent.mm.ui.widget.listview.PullDownListView")
            else -> false
        }
    }

    private val wechatDiagnosticFrames = WeakHashMap<Activity, Int>()
    private val wechatDiagnosticListeners = WeakHashMap<Activity, ViewTreeObserver.OnPreDrawListener>()
    private fun armWechatDiagnostics(activity: Activity, reason: String) {
        if (resolveTarget(activity) != BackgroundBeautySettings.Slot.WECHAT) return
        val key = "wechat-arm:$reason"
        if (diagnosticCounts.computeIfAbsent(key) { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet() > 3) return
        wechatDiagnosticFrames[activity] = 2
        snapshotWechatLayers(activity, reason)
        if (wechatDiagnosticListeners.containsKey(activity)) return
        val listener = ViewTreeObserver.OnPreDrawListener {
            val remaining = wechatDiagnosticFrames[activity] ?: 0
            if (remaining > 0) {
                wechatDiagnosticFrames[activity] = remaining - 1
                snapshotWechatLayers(activity, "predraw:$remaining")
            }
            true
        }
        wechatDiagnosticListeners[activity] = listener
        activity.window.decorView.viewTreeObserver.addOnPreDrawListener(listener)
    }
    private fun snapshotWechatLayers(activity: Activity, reason: String) {
        if (diagnosticCounts.computeIfAbsent("wechat-snapshot-total") { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet() > 14) return
        val state = states[activity] ?: return
        val slot = resolveTarget(activity)
        HLog.i("$TAG WXFRAME reason=$reason slot=$slot bars=${activity.window.statusBarColor}/${activity.window.navigationBarColor} chat=$launcherChatActive pending=${pendingRefreshes.containsKey(activity)}")
        val queue = ArrayDeque<Pair<View, Int>>()
        queue.add(activity.window.decorView to 0)
        var visited = 0
        var emitted = 0
        while (queue.isNotEmpty() && visited++ < MAX_SCAN_NODES && emitted < 65) {
            val (view, depth) = queue.removeFirst()
            val rect = Rect()
            if (!view.getGlobalVisibleRect(rect) || view.alpha <= 0f) continue
            val bg = view.background
            val fg = view.foreground
            val id = runCatching { view.resources.getResourceEntryName(view.id) }.getOrDefault("none")
            val top = rect.top < 550
            val broad = rect.width() >= activity.window.decorView.width * 3 / 4
            if (depth == 0 || top || broad && (bg != null || fg != null)) {
                emitted++
                val snapshot = state.surfaces[view]
                val draw = KavaReflector.declaredMethods(view.javaClass).filter { it.name in setOf("onDraw", "dispatchDraw", "draw") }.joinToString { it.name }
                val extras = if (view.javaClass.name == "com.tencent.mm.ui.FrostedContentView") {
                    listOf("getFrostedEnabled", "getFrostedColor", "getTopBlurAreaHeight").joinToString { name -> "$name=${KavaReflector.findMethodRecursive(view.javaClass,name)?.let { KavaReflector.invoke(it,view) }}" }
                } else if (view.javaClass.name == "androidx.appcompat.widget.ActionBarContainer") {
                    listOf("h", "i", "m").joinToString { field -> val d=KavaReflector.readField(view,field) as? Drawable; "$field=${d?.javaClass?.name}:${(d as? ColorDrawable)?.color}" }
                } else "-"
                HLog.i("$TAG WXLAYER depth=$depth class=${view.javaClass.name} id=$id rect=$rect alpha=${view.alpha} bg=${bg?.javaClass?.name}:${(bg as? ColorDrawable)?.color} fg=${fg?.javaClass?.name}:${(fg as? ColorDrawable)?.color} tint=${view.backgroundTintList} owned=${snapshot != null && bg === snapshot.appliedBackground} slot=${snapshot?.slot} scroll=${view.scrollX}/${view.scrollY} translation=${view.translationX}/${view.translationY} draw=$draw extras=$extras")
            }
            if (view is ViewGroup && !isComposeSurface(view)) for (i in 0 until view.childCount) queue.add(view.getChildAt(i) to depth+1)
        }
    }
    private fun isSystemBarBackground(view: View): Boolean {
        val id = view.id
        if (id == View.NO_ID) return false
        return runCatching {
            view.resources.getResourcePackageName(id) == "android" &&
                view.resources.getResourceEntryName(id) in setOf("statusBarBackground", "navigationBarBackground")
        }.getOrDefault(false)
    }

    private fun isChromeSurface(view: View): Boolean {
        val name = view.javaClass.name
        return name.contains("ActionBar", ignoreCase = true) ||
            name.contains("Toolbar", ignoreCase = true)
    }

    private fun isComposeSurface(view: View): Boolean {
        val name = view.javaClass.name
        return name.contains("ComposeView") || name.startsWith("androidx.compose.")
    }

    private fun setImageSurface(
        state: HostState,
        view: View,
        slot: BackgroundBeautySettings.Slot,
        bitmap: Bitmap,
        opacity: Float
    ) {
        surfaceMutationDepth++
        try {
            val snapshot = surfaceSnapshot(state, view)
            synchronizeExternalBackground(snapshot, view)
            if (snapshot.slot == slot &&
                snapshot.appliedBitmap === bitmap &&
                snapshot.appliedOpacity == opacity &&
                view.background === snapshot.appliedBackground
            ) return
            val image = ScreenAlignedBitmapDrawable(bitmap, view, opacity,
                if (slot == BackgroundBeautySettings.Slot.CHAT) null else resolveThemeBackground(view.context) or (0xff shl 24))
            val applied = if (slot == BackgroundBeautySettings.Slot.CHAT && opacity < 0.999f) {
                LayerDrawable(
                    arrayOf(
                        cloneDrawable(snapshot.originalBackground, view.context)
                            ?: ColorDrawable(resolveThemeBackground(view.context)),
                        image
                    )
                )
            } else {
                image
            }
            view.backgroundTintList = null
            view.backgroundTintMode = null
            view.background = applied
            restoreSurfaceGeometry(view, snapshot)
            snapshot.appliedBackground = applied
            snapshot.appliedBitmap = bitmap
            snapshot.appliedOpacity = opacity
            snapshot.slot = slot

        } finally { surfaceMutationDepth-- }
    }

    private val initializedChrome = WeakHashMap<View, Boolean>()
    private fun applyNativeChrome(view: View, slot: BackgroundBeautySettings.Slot) {
        if (slot == BackgroundBeautySettings.Slot.CHAT || restoringBounce || initializedChrome[view] == true) return
        if (hasClassName(view, BOUNCE_VIEW) || hasClassName(view, NESTED_BOUNCE_VIEW) ||
            view.javaClass.name == "com.tencent.mm.ui.FrostedContentView" || view.javaClass.name == "androidx.appcompat.widget.ActionBarContainer") initializedChrome[view] = true
        if (hasClassName(view, BOUNCE_VIEW) || hasClassName(view, NESTED_BOUNCE_VIEW)) {
            KavaReflector.findMethodRecursive(view.javaClass, "a", View::class.java, Integer.TYPE)?.let { method ->
                hookOnce(method, before = { param ->
                    val bounce = param.thisObject as? View ?: return@hookOnce
                    val owner = findActivity(bounce) ?: return@hookOnce
                    val slotForBounce = surfaceOwners[bounce]?.surfaces?.get(bounce)?.slot ?: return@hookOnce
                    if (restoringBounce || !prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE) ||
                        !BackgroundBeautyStore.hasImage(context.hostContext(), slotForBounce)) return@hookOnce
                    if (diagnosticCounts.computeIfAbsent("bounce-offset") { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet() <= 12) {
                        val colors = listOf("MStart2EndBg", "MEnd2StartBg", "MStart2EndBgByActionBar", "MEnd2StartBgByNavigationBar").joinToString(",") { property ->
                            val getter = KavaReflector.findMethodRecursive(bounce.javaClass, "get$property")
                            val drawable = getter?.let { KavaReflector.invoke(it, bounce) } as? Drawable
                            "$property=${drawable?.javaClass?.name}:${(drawable as? ColorDrawable)?.color}"
                        }
                        val backdrop = KavaReflector.readField(bounce, "G") as? View
                        HLog.i("$TAG DIAG bounceOffset offset=${param.args.getOrNull(1)} colors=$colors backdrop=${backdrop?.background?.javaClass?.name} managed=${backdrop?.let { surfaceOwners.containsKey(it) }}")
                    }
                }, after = { param ->
                    val bounce = param.thisObject as? View ?: return@hookOnce
                    val activity = findActivity(bounce) ?: return@hookOnce
                    val offset = param.args.getOrNull(1) as? Int ?: return@hookOnce
                    if (offset > 100) armWechatDiagnostics(activity, "bounce-positive")
                    else if (offset == 0) armWechatDiagnostics(activity, "bounce-zero")
                })
            }
            val activity = findActivity(view)
            val state = activity?.let { states[it] }
            val backdrop = KavaReflector.readField(view, "G") as? View
            if (state != null && backdrop != null) setTransparentSurface(state, backdrop, slot)
            HLog.i("$TAG DIAG bouncePrepared class=${view.javaClass.name} backdrop=${backdrop?.javaClass?.name}")
            listOf("MStart2EndBg", "MEnd2StartBg", "MStart2EndBgByActionBar", "MEnd2StartBgByNavigationBar").forEach { property ->
                val getter = KavaReflector.findMethodRecursive(view.javaClass, "get$property") ?: return@forEach
                val setter = KavaReflector.findMethodRecursive(view.javaClass, "set$property", Drawable::class.java) ?: return@forEach
                val values = bounceColors.getOrPut(view) { LinkedHashMap() }
                if (!values.containsKey(setter)) {
                    values[setter] = KavaReflector.invoke(getter, view)
                    surfaceMutationDepth++
                    try { KavaReflector.invoke(setter, view, ColorDrawable(Color.TRANSPARENT)) } finally { surfaceMutationDepth-- }
                }
            }
        }
        if (view.javaClass.name == "com.tencent.mm.ui.FrostedContentView") {
            val getter = KavaReflector.findMethodRecursive(view.javaClass, "getFrostedEnabled") ?: return
            val setter = KavaReflector.findMethodRecursive(view.javaClass, "setFrostedEnabled", Boolean::class.javaPrimitiveType!!) ?: return
            val enabled = KavaReflector.invoke(getter, view) as? Boolean ?: return
            val values = bounceColors.getOrPut(view) { LinkedHashMap() }
            if (!values.containsKey(setter) || enabled) values[setter] = enabled
            if (enabled) {
                surfaceMutationDepth++
                try { KavaReflector.invoke(setter, view, false) } finally { surfaceMutationDepth-- }
            }
            KavaReflector.findMethodRecursive(view.javaClass, "dispatchDraw", android.graphics.Canvas::class.java)?.let { draw ->
                hookOnce(draw, before = { param ->
                    val target = param.thisObject as? View ?: return@hookOnce
                    val owner = findActivity(target) ?: return@hookOnce
                    if (!restoringBounce && states[owner] != null && bounceColors[target]?.containsKey(setter) == true) {
                        if (KavaReflector.invoke(getter, target) == true) {
                            bounceColors[target]!![setter] = true
                            surfaceMutationDepth++
                            try { KavaReflector.invoke(setter, target, false) } finally { surfaceMutationDepth-- }
                        }
                    }
                })
            }
            hookOnce(setter, before = { param ->
                val target = param.thisObject as? View ?: return@hookOnce
                if (!restoringBounce && surfaceMutationDepth == 0 && bounceColors[target]?.containsKey(setter) == true) {
                    bounceColors[target]!![setter] = param.args[0]
                    param.args[0] = false
                }
            })
        }
        if (view.javaClass.name == "androidx.appcompat.widget.ActionBarContainer") {
            listOf("setPrimaryBackground" to "h", "setStackedBackground" to "i", "setSplitBackground" to "m").forEach { (name, field) ->
                val setter = KavaReflector.findMethodRecursive(view.javaClass, name, Drawable::class.java) ?: return@forEach
                val values = bounceColors.getOrPut(view) { LinkedHashMap() }
                if (!values.containsKey(setter)) {
                    values[setter] = KavaReflector.readField(view, field) as? Drawable
                    surfaceMutationDepth++
                    try { KavaReflector.invoke(setter, view, null) } finally { surfaceMutationDepth-- }
                }
                hookOnce(setter, before = { param ->
                    val target = param.thisObject as? View ?: return@hookOnce
                    if (!restoringBounce && surfaceMutationDepth == 0 && bounceColors[target]?.containsKey(setter) == true) {
                        bounceColors[target]!![setter] = param.args[0]
                        param.args[0] = null
                    }
                })
            }
        }
    }
    private fun setTransparentSurface(
        state: HostState,
        view: View,
        slot: BackgroundBeautySettings.Slot,
        clearForeground: Boolean = false,
        preserveFeedback: Boolean = false
    ) {
        applyNativeChrome(view, slot)
        surfaceMutationDepth++
        try {
            val snapshot = surfaceSnapshot(state, view)
            synchronizeExternalBackground(snapshot, view)
            if (snapshot.slot == slot &&
                snapshot.appliedBitmap == null &&
                snapshot.appliedBackground != null &&
                view.background === snapshot.appliedBackground &&
                (!preserveFeedback || snapshot.appliedBackground is BackgroundBeautyFeedbackDrawable) &&
                (!clearForeground || view.foreground === snapshot.appliedForeground)
            ) return
            val marker: Drawable = if (preserveFeedback) {
                BackgroundBeautyFeedbackDrawable()
            } else BackgroundBeautyTransparentDrawable()
            view.backgroundTintList = null
            view.backgroundTintMode = null
            view.background = marker
            marker.state = view.drawableState
            restoreSurfaceGeometry(view, snapshot)
            snapshot.appliedBackground = marker
            snapshot.appliedBitmap = null
            snapshot.appliedOpacity = null
            if (clearForeground) {
                val foregroundMarker = BackgroundBeautyTransparentDrawable()
                view.foreground = foregroundMarker
                snapshot.appliedForeground = foregroundMarker
            }
            snapshot.slot = slot

        } finally { surfaceMutationDepth-- }
    }

    private fun surfaceSnapshot(state: HostState, view: View): SurfaceSnapshot {
        surfaceOwners[view] = state
        return state.surfaces[view] ?: SurfaceSnapshot(view).also { state.surfaces[view] = it }
    }

    private fun synchronizeExternalBackground(snapshot: SurfaceSnapshot, view: View) {
        val applied = snapshot.appliedBackground
        if (applied != null && view.background !== applied) {
            snapshot.originalBackground = view.background
            snapshot.originalTintList = view.backgroundTintList
            snapshot.originalTintMode = view.backgroundTintMode
            snapshot.originalPaddingLeft = view.paddingLeft
            snapshot.originalPaddingTop = view.paddingTop
            snapshot.originalPaddingRight = view.paddingRight
            snapshot.originalPaddingBottom = view.paddingBottom
            snapshot.originalMinimumWidth = view.minimumWidth
            snapshot.originalMinimumHeight = view.minimumHeight
            snapshot.appliedBackground = null
            snapshot.appliedBitmap = null
            snapshot.appliedOpacity = null
        }
        val foreground = snapshot.appliedForeground
        if (foreground != null && view.foreground !== foreground) {
            snapshot.originalForeground = view.foreground
            snapshot.appliedForeground = null
        }
    }

    private fun setNativeImage(
        state: HostState,
        view: ImageView,
        bitmap: Bitmap,
        opacity: Float
    ) {
        val snapshot = state.nativeImages[view] ?: NativeImageSnapshot(view).also {
            state.nativeImages[view] = it
        }
        if (snapshot.slot == BackgroundBeautySettings.Slot.CHAT &&
            snapshot.appliedBitmap === bitmap &&
            snapshot.appliedOpacity == opacity &&
            view.drawable === snapshot.appliedDrawable
        ) return
        if (snapshot.appliedDrawable != null && view.drawable !== snapshot.appliedDrawable) {
            snapshot.originalDrawable = view.drawable
            snapshot.originalTintList = view.imageTintList
        }
        view.imageTintList = null
        view.scaleType = ImageView.ScaleType.CENTER_CROP
        view.setImageBitmap(bitmap)
        view.alpha = opacity
        snapshot.appliedDrawable = view.drawable
        snapshot.appliedAlpha = opacity
        snapshot.appliedScaleType = ImageView.ScaleType.CENTER_CROP
        snapshot.appliedBitmap = bitmap
        snapshot.appliedOpacity = opacity
        snapshot.slot = BackgroundBeautySettings.Slot.CHAT
    }

    private fun restoreSlot(state: HostState, slot: BackgroundBeautySettings.Slot) {
        preparedRoots.entries.removeAll { it.value.first === state }
        state.loadedBitmaps.remove(slot)
        state.surfaces.entries.toList().forEach { (view, snapshot) ->
            if (snapshot.slot == slot) {
                restoreSurface(view, snapshot)
                state.surfaces.remove(view)
            }
        }
        state.nativeImages.entries.toList().forEach { (view, snapshot) ->
            if (snapshot.slot == slot) {
                restoreNativeImage(view, snapshot)
                state.nativeImages.remove(view)
            }
        }
    }

    private fun restoreSharedSurfaces(state: HostState) {
        val host = resolveBackgroundHost(state.activity)
        val content = state.activity.findViewById<View>(android.R.id.content)
        state.surfaces.entries.toList().forEach { (view, snapshot) ->
            if (view === state.activity.window.decorView || view === host || view === content || isChromeSurface(view) ||
                isBottomBar(view) || isSystemBarBackground(view)) {
                restoreSurface(view, snapshot)
                state.surfaces.remove(view)
            }
        }
    }

    private fun restoreSurface(view: View, snapshot: SurfaceSnapshot) {
        surfaceOwners.remove(view)
        surfaceMutationDepth++
        try {
            val appliedBackground = snapshot.appliedBackground
            if (appliedBackground != null && view.background === appliedBackground) {
                view.background = snapshot.originalBackground
                view.backgroundTintList = snapshot.originalTintList
                view.backgroundTintMode = snapshot.originalTintMode
                restoreSurfaceGeometry(view, snapshot)
            }
            snapshot.appliedForeground?.let { appliedForeground ->
                if (view.foreground === appliedForeground) {
                    view.foreground = snapshot.originalForeground
                }
            }

        } finally { surfaceMutationDepth-- }
    }

    private fun restoreNativeImage(view: ImageView, snapshot: NativeImageSnapshot) {
        if (view.drawable === snapshot.appliedDrawable) {
            view.setImageDrawable(snapshot.originalDrawable)
            view.imageTintList = snapshot.originalTintList
        }
        snapshot.appliedAlpha?.let { applied ->
            if (view.alpha == applied) view.alpha = snapshot.originalAlpha
        }
        snapshot.appliedScaleType?.let { applied ->
            if (view.scaleType == applied) view.scaleType = snapshot.originalScaleType
        }
    }

    private fun restoreSurfaceGeometry(view: View, snapshot: SurfaceSnapshot) {
        view.setPadding(
            snapshot.originalPaddingLeft,
            snapshot.originalPaddingTop,
            snapshot.originalPaddingRight,
            snapshot.originalPaddingBottom
        )
        view.minimumWidth = snapshot.originalMinimumWidth
        view.minimumHeight = snapshot.originalMinimumHeight
    }

    private fun applySystemBars(state: HostState) {
        val window = state.activity.window
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or IMMERSIVE_FLAGS
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }
        state.systemBarsApplied = true
    }

    private fun restoreSystemBars(state: HostState) {
        if (!state.systemBarsApplied) return
        state.systemBarsApplied = false
        val window = state.activity.window
        window.decorView.systemUiVisibility =
            (window.decorView.systemUiVisibility and IMMERSIVE_FLAGS.inv()) or
            (state.originalSystemUiVisibility and IMMERSIVE_FLAGS)
        if (window.statusBarColor == Color.TRANSPARENT) {
            window.statusBarColor = state.originalStatusBarColor
        }
        if (window.navigationBarColor == Color.TRANSPARENT) {
            window.navigationBarColor = state.originalNavigationBarColor
        }
        if (android.os.Build.VERSION.SDK_INT >= 28 &&
            state.originalNavigationBarDividerColor != null &&
            window.navigationBarDividerColor == Color.TRANSPARENT
        ) {
            window.navigationBarDividerColor = state.originalNavigationBarDividerColor
        }
        if (android.os.Build.VERSION.SDK_INT >= 29 && state.originalNavigationBarContrast != null) {
            window.isNavigationBarContrastEnforced = state.originalNavigationBarContrast
        }
        state.systemBarsApplied = false
    }

    private fun clearActivity(activity: Activity) {
        restoreBounceColors(activity)
        synchronized(pendingLoads) { pendingLoads.remove(activity) }
        synchronized(pendingRefreshes) { pendingRefreshes.remove(activity) }
        removeLayoutListeners(activity)
        homeLists.keys.removeAll { findActivity(it) === activity }
        listAdapters.keys.removeAll { findActivity(it) === activity }
        val state = synchronized(states) { states.remove(activity) } ?: return
        restoreState(state)
    }

    private fun removeLayoutListeners(activity: Activity) {
        synchronized(layoutListeners) {
            val targets = layoutListeners.keys.filter { view -> findActivity(view) === activity }
            targets.forEach { view ->
                layoutListeners.remove(view)?.let(view::removeOnLayoutChangeListener)
            }
        }
    }

    private fun forgetActivity(activity: Activity) {
        synchronized(observedActivities) { observedActivities.remove(activity) }
        synchronized(nativeChatImages) { nativeChatImages.remove(activity) }
        synchronized(launcherTabIndices) { launcherTabIndices.remove(activity) }
        clearActivity(activity)
    }

    private fun restoreState(state: HostState) {
        preparedRoots.entries.removeAll { it.value.first === state }
        state.surfaces.entries.toList().forEach { (view, snapshot) ->
            restoreSurface(view, snapshot)
        }
        state.nativeImages.entries.toList().forEach { (view, snapshot) ->
            restoreNativeImage(view, snapshot)
        }
        state.surfaces.clear()
        state.nativeImages.clear()
        state.loadedBitmaps.clear()
        restoreSystemBars(state)
    }

    private fun resolveBackgroundHost(activity: Activity): View? {
        val content = activity.findViewById<View>(android.R.id.content) ?: return null
        var current: View? = content
        while (current != null && current !== activity.window.decorView) {
            if (current.javaClass.name.contains("ActionBarOverlayLayout")) return current
            current = current.parent as? View
        }
        return content
    }

    private fun resolveTarget(activity: Activity): BackgroundBeautySettings.Slot? {
        val name = activity.javaClass.name
        if (name == CHAT_ACTIVITY || name.contains("ChattingUI")) {
            return BackgroundBeautySettings.Slot.CHAT
        }
        if (name != LAUNCHER_ACTIVITY && !name.endsWith("LauncherUI")) return null
        val fragment = currentFragment(activity)
        if (launcherChatActive == true || (launcherChatActive == null && fragment?.javaClass?.name?.contains("chatting", ignoreCase = true) == true)) {
            return BackgroundBeautySettings.Slot.CHAT
        }
        return currentLauncherSlot(activity)
    }

    private fun currentLauncherSlot(activity: Activity): BackgroundBeautySettings.Slot? {
        val index = KavaReflector.findMethodRecursive(activity.javaClass, "getCurrentTabIndex")
            ?.takeIf { it.parameterTypes.isEmpty() }
            ?.let { method -> KavaReflector.invoke(method, activity) as? Int }
            ?: synchronized(launcherTabIndices) { launcherTabIndices[activity] }
        return when (index) {
            TAB_WECHAT -> BackgroundBeautySettings.Slot.WECHAT
            TAB_CONTACTS -> BackgroundBeautySettings.Slot.CONTACTS
            TAB_DISCOVER -> BackgroundBeautySettings.Slot.DISCOVER
            TAB_ME -> BackgroundBeautySettings.Slot.ME
            else -> slotForFragment(currentFragment(activity))
        }
    }

    private fun currentFragment(activity: Activity): Any? {
        KavaReflector.findMethodRecursive(activity.javaClass, "getCurrentFragmet")
            ?.takeIf { it.parameterTypes.isEmpty() }
            ?.let { method -> KavaReflector.invoke(method, activity) }
            ?.takeIf { it.javaClass.name.contains("chatting", ignoreCase = true) }
            ?.let { return it }
        val home = KavaReflector.findMethodRecursive(activity.javaClass, "getHomeUI")
            ?.let { KavaReflector.invoke(it, activity) }
        val mainTab = home?.let { owner ->
            KavaReflector.findMethodRecursive(owner.javaClass, "getMainTabUI")
                ?.let { KavaReflector.invoke(it, owner) }
                ?: findFieldValueByClassName(owner, MAIN_TAB_CLASS)
        } ?: findFieldValueByClassName(activity, MAIN_TAB_CLASS) ?: return null
        return KavaReflector.findMethodRecursive(mainTab.javaClass, "g")
            ?.takeIf { it.parameterTypes.isEmpty() }
            ?.let { method -> KavaReflector.invoke(method, mainTab) }
    }

    private fun fragmentView(fragment: Any): View? {
        synchronized(fragmentRoots) {
            fragmentRoots[fragment]?.get()?.let { return it }
        }
        return KavaReflector.findMethodRecursive(fragment.javaClass, "getView")
            ?.takeIf { it.parameterTypes.isEmpty() }
            ?.let { method -> KavaReflector.invoke(method, fragment) as? View }
    }

    private fun slotForFragment(fragment: Any?): BackgroundBeautySettings.Slot? {
        fragment ?: return null
        return when {
            hasClassName(fragment, MAIN_FRAGMENT) -> BackgroundBeautySettings.Slot.WECHAT
            hasClassName(fragment, CONTACTS_FRAGMENT) -> BackgroundBeautySettings.Slot.CONTACTS
            hasClassName(fragment, DISCOVER_FRAGMENT) -> BackgroundBeautySettings.Slot.DISCOVER
            hasClassName(fragment, ME_FRAGMENT) -> BackgroundBeautySettings.Slot.ME
            else -> null
        }
    }

    private fun resourceId(name: String): Int {
        val host = context.hostContext()
        return host.resources.getIdentifier(name, "id", host.packageName)
    }

    private fun cloneDrawable(drawable: Drawable?, context: Context): Drawable? {
        return drawable?.constantState?.newDrawable(context.resources)?.mutate()
    }

    private fun resolveThemeBackground(context: Context): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(android.R.attr.colorBackground, value, true)) {
            value.data
        } else {
            Color.TRANSPARENT
        }
    }

    private fun hasConfiguredBackground(activity: Activity): Boolean {
        if (!prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE)) {
            return false
        }
        val slot = resolveTarget(activity) ?: return false
        return BackgroundBeautyStore.hasImage(context.hostContext(), slot)
    }

    private fun isBottomBar(view: View): Boolean {
        val name = view.javaClass.name
        return name == BOTTOM_TAB_CLASS ||
            name == "com.tencent.mm.ui.LauncherUITabView" ||
            view.tag == "Hchat:FloatingBottomBar"
    }

    private fun findDescendant(root: View?, className: String): View? {
        root ?: return null
        val queue = ArrayDeque<View>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_SCAN_NODES) {
            val view = queue.removeFirst()
            if (hasClassName(view, className)) return view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) queue.addLast(view.getChildAt(index))
            }
        }
        return null
    }

    private fun findFieldValueByClassName(owner: Any, className: String): Any? {
        var type: Class<*>? = owner.javaClass
        while (type != null && type != Any::class.java) {
            KavaReflector.declaredFields(type).forEach { field ->
                if (field.type.name == className) {
                    KavaReflector.readField(field, owner)?.let { return it }
                }
            }
            type = type.superclass
        }
        return null
    }

    private fun hasClassName(value: Any, className: String): Boolean {
        var type: Class<*>? = value.javaClass
        while (type != null && type != Any::class.java) {
            if (type.name == className) return true
            type = type.superclass
        }
        return false
    }

    private fun findActivity(owner: Any?): Activity? = findActivity(
        owner,
        Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
    )

    private fun findActivity(owner: Any?, visited: MutableSet<Any>): Activity? {
        if (owner == null || !visited.add(owner)) return null
        if (owner is Activity) return owner
        var current: Context? = when (owner) {
            is View -> owner.context
            is Context -> owner
            else -> null
        }
        while (current != null) {
            if (current is Activity) return current
            val base = (current as? ContextWrapper)?.baseContext ?: break
            if (base === current) break
            current = base
        }
        KavaReflector.findMethodRecursive(owner.javaClass, "getActivity")
            ?.takeIf {
                it.parameterTypes.isEmpty() && Activity::class.java.isAssignableFrom(it.returnType)
            }
            ?.let { method -> KavaReflector.invoke(method, owner) as? Activity }
            ?.let { return it }
        var type: Class<*>? = owner.javaClass
        while (type != null && type != Any::class.java) {
            KavaReflector.declaredFields(type).forEach { field ->
                if (KavaReflector.isStatic(field)) return@forEach
                val value = KavaReflector.readField(field, owner) ?: return@forEach
                if (value is Activity) return value
                if (value is View || value is Context) {
                    findActivity(value, visited)?.let { return it }
                }
            }
            type = type.superclass
        }
        return null
    }
}
