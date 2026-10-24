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

private class BackgroundBeautyFeedbackDrawable(private val manualOnly: Boolean = false) : Drawable(), Drawable.Callback {
    private val ripple = android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(0x40808080), null, ColorDrawable(Color.WHITE)
    ).apply { callback = this@BackgroundBeautyFeedbackDrawable }
    private var touchRegion: Rect? = null
    fun setTouchRegion(region: Rect?) { touchRegion = region?.let { Rect(it) }; invalidateSelf() }
    override fun draw(canvas: Canvas) {
        val region = touchRegion
        val clipped = if (region == null) bounds else Rect(bounds.left + region.left, bounds.top + region.top, bounds.left + region.right, bounds.top + region.bottom)
        val save = canvas.save()
        canvas.clipRect(clipped)
        ripple.bounds = clipped
        ripple.draw(canvas)
        canvas.restoreToCount(save)
    }
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
        ripple.state = if (touchPressed) state + intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed) else if (manualOnly) state.filter { it != android.R.attr.state_pressed }.toIntArray() else state
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
    private val baseColor: Int? = null,
    private val followHorizontalMotion: Boolean = false
) : Drawable() {
    private val bitmapRef = WeakReference(bitmap)
    private val anchorRef = WeakReference(anchor)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
        alpha = (BackgroundBeautySettings.opacity(opacity) * 255f).toInt().coerceIn(0, 255)
    }
    private val rootLocation = IntArray(2)
    private val anchorLocation = IntArray(2)
    private val destination = RectF()
    private var geometryWidth = -1
    private var geometryHeight = -1
    private var geometryX = Int.MIN_VALUE
    private var geometryY = Int.MIN_VALUE

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
        val nextX = if (followHorizontalMotion) 0 else anchorLocation[0] - rootLocation[0]
        val nextY = anchorLocation[1] - rootLocation[1]
        if (geometryWidth == rootWidth && geometryHeight == rootHeight && geometryX == nextX && geometryY == nextY) return
        geometryWidth = rootWidth; geometryHeight = rootHeight; geometryX = nextX; geometryY = nextY
        val scale = max(
            rootWidth.toFloat() / bitmap.width.toFloat(),
            rootHeight.toFloat() / bitmap.height.toFloat()
        )
        val drawnWidth = bitmap.width * scale
        val drawnHeight = bitmap.height * scale
        // Chat backgrounds belong to the moving chat surface. Compensating its screen X
        // shifts the bitmap out of its own clip while the page enters or swipes back.
        val offsetX = if (followHorizontalMotion) 0 else anchorLocation[0] - rootLocation[0]
        val offsetY = anchorLocation[1] - rootLocation[1]
        destination.set(
            (rootWidth - drawnWidth) / 2f - offsetX,
            (rootHeight - drawnHeight) / 2f - offsetY,
            (rootWidth + drawnWidth) / 2f - offsetX,
            (rootHeight + drawnHeight) / 2f - offsetY
        )
    }

    override fun onBoundsChange(bounds: Rect) {
        updateDestination()
    }

    override fun draw(canvas: Canvas) {
        // Insets, keyboard and page transitions can move the anchor without changing drawable bounds.
        updateDestination()
        val bitmap = bitmapRef.get()
        baseColor?.let { canvas.drawColor(it) }
        if (bitmap != null && !bitmap.isRecycled && !destination.isEmpty) canvas.drawBitmap(bitmap, null, destination, paint)
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
        const val BASE_CONVERSATION_ACTIVITY = "com.tencent.mm.ui.conversation.BaseConversationUI"
        const val CHAT_CONTAINER = "com.tencent.mm.ui.tools.TestTimeForChatting"
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
        val inputUnderlay = ChatInputMessageUnderlay()
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
    private var preloadedHome: LoadedBitmap? = null
    @Volatile
    private var destroyed = false

    fun install(): Boolean {
        // Decode concurrently with hook installation, before the first page refresh.
        if (prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE)) {
            loader.execute {
                val revision = prefs.getInt(BackgroundBeautySettings.KEY_REVISION, 0)
                val bitmap = BackgroundBeautyStore.loadBitmap(context.hostContext(), BackgroundBeautySettings.Slot.WECHAT)
                if (bitmap != null && !destroyed && revision == prefs.getInt(BackgroundBeautySettings.KEY_REVISION, 0)) {
                    preloadedHome = LoadedBitmap(revision, bitmap)
                }

                main.post { if (!destroyed) refreshAll() }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        val installed = installActivityHooks() or
            installTabHooks() or
            installFragmentHooks() or
            installSurfaceHooks() or installBackgroundWriteHook() or installWechatVerifiedPaths()
        installChatChromeLifecycle()
        installChatBackgroundComponentHook(allowDexSearch = false)
        if (!installed) HLog.e("$TAG 未安装任何页面 Hook")
        main.post(::refreshAll)
        return installed
    }

    fun destroy() {
        destroyed = true
        preloadedHome = null
        decorUpdateDepth.clear()
        wechatTitleBackdrops.keys.toList().forEach { it.invalidate() }
        wechatTitleBackdrops.clear()
        restoreInitialStoryMuteViews()
        wechatGlVisibility.keys.toList().forEach(::restoreWechatGl)
        wechatGradientControllers.keys.toList().forEach { it.invalidate() }
        wechatGradientControllers.clear()
        wechatTaskControllers.clear()
        ownedHooks.toList().forEach { runCatching { HookRegistry.get().unhook(it) } }
        ownedHooks.clear()
        hookedMembers.clear()
        suppressedDynamicContainers.keys.toList().forEach { view ->
            if (view.isAttachedToWindow) {
                KavaReflector.findMethodRecursive(view.javaClass, "a")?.let { KavaReflector.invoke(it, view) }
            }
        }
        suppressedDynamicContainers.clear()
        restoreBounceColors(null)
        contactOverlays.toMap().forEach { (row, overlay) -> row.overlay.remove(overlay) }
        contactOverlays.clear()
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
        listOf(CHAT_ACTIVITY, LAUNCHER_ACTIVITY, BASE_CONVERSATION_ACTIVITY).forEach { className ->
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
        val homeClass = KavaReflector.loadClass("com.tencent.mm.ui.HomeUI", context.hostClassLoader())
        KavaReflector.declaredMethods(homeClass).filter { it.name in setOf("q", "w") && it.returnType == Void.TYPE }.forEach { method ->
            installed = hookOnce(method, after = { param ->
                val activity = findActivity(param.thisObject) ?: return@hookOnce
                val state = states[activity] ?: return@hookOnce
                val slot = currentLauncherSlot(activity) ?: return@hookOnce
                if (slot != BackgroundBeautySettings.Slot.CHAT && hasConfiguredBackground(activity)) {
                    // q is also invoked by ConversationListView.onScroll: never scan the tree here.
                    state.surfaces.entries.toList().forEach { (view, snapshot) ->
                        if (view.isAttachedToWindow && isChromeSurface(view) &&
                            (view.background !== snapshot.appliedBackground || snapshot.slot != slot)) {
                            setTransparentSurface(state, view, slot)
                        }
                    }
                    if (!state.systemBarsApplied) applySystemBars(state)
                }
            }) || installed
        }
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

    private val initialStoryMuteVisibility = WeakHashMap<View, Int>()
    private fun restoreInitialStoryMuteViews() {
        initialStoryMuteVisibility.entries.toList().forEach { (view, original) ->
            // Restore only the initialization change; respect later host visibility writes.
            if (view.visibility == View.GONE) view.visibility = original
        }
        initialStoryMuteVisibility.clear()
    }

    /** 8.0.78 F0 posts v5: account status -> V1 -> preference visibility + notify. */
    private fun installDiscoverStatusBeforeDraw(): Boolean {
        val version = runCatching { WeChatApis.version()?.current()?.versionName }.getOrNull()
        if (version != "8.0.78") return false
        val loader = context.hostClassLoader()
        val taskClass = KavaReflector.loadClass("com.tencent.mm.ui.v5", loader) ?: return false
        if (!Runnable::class.java.isAssignableFrom(taskClass)) return false
        val ownerField = KavaReflector.declaredFields(taskClass).singleOrNull {
            it.name == "d" && it.type.name == DISCOVER_FRAGMENT
        } ?: return false
        val scheduler = KavaReflector.loadClass("com.tencent.mm.sdk.platformtools.x3", loader) ?: return false
        val enqueue = KavaReflector.findMethodRecursive(scheduler, "h", Runnable::class.java)
            ?.takeIf { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE }
            ?: return false
        return hookOnce(enqueue, before = { param ->
            if (Looper.myLooper() != Looper.getMainLooper()) return@hookOnce
            val task = param.args.firstOrNull() as? Runnable ?: return@hookOnce
            if (task.javaClass !== taskClass || KavaReflector.readField(ownerField, task) == null) return@hookOnce
            // Run exactly the task the host created; do not enqueue a second update.
            task.run()
            param.result = null
        })
    }

    private fun installFragmentHooks(): Boolean {
        var installed = installDiscoverStatusBeforeDraw()
        KavaReflector.loadClass(ME_FRAGMENT, context.hostClassLoader())?.let { clazz ->
            findWechatVerifiedMethod(clazz, "C0", Bundle::class.java)?.let { method ->
                installed = hookOnce(method, after = { param ->
                    if (destroyed) return@hookOnce
                    val mute = readNativeField(param.thisObject, "v") as? View ?: return@hookOnce
                    if (mute.javaClass.name != "com.tencent.mm.plugin.story.ui.view.StoryMuteView") return@hookOnce
                    val activity = findActivity(mute) ?: return@hookOnce
                    if (activity.javaClass.name != LAUNCHER_ACTIVITY ||
                        !prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE) ||
                        !BackgroundBeautyStore.hasImage(context.hostContext(), BackgroundBeautySettings.Slot.ME)) return@hookOnce
                    if (mute.visibility == View.VISIBLE) {
                        val original = mute.visibility
                        mute.visibility = View.GONE
                        initialStoryMuteVisibility[mute] = original

                    }
                }) || installed
            }
        }
        // 8.0.78 MainUI.B0 creates/recolors controller.g's og9 status mask.
        // It is a sibling of the fragment content, so preparePage cannot discover it.
        KavaReflector.loadClass(MAIN_FRAGMENT, context.hostClassLoader())?.let { clazz ->
            findWechatVerifiedMethod(clazz, "B0")?.let { method ->
                installed = hookOnce(method, after = { param ->
                    val activity = findActivity(param.thisObject) ?: return@hookOnce
                    applyWechatStatusMasks(activity, "main-tab-resume")
                }) || installed
            }
            findWechatVerifiedMethod(clazz, "onConfigurationChanged", android.content.res.Configuration::class.java)?.let { method ->
                installed = hookOnce(method, after = { param ->
                    val activity = findActivity(param.thisObject) ?: return@hookOnce
                    applyWechatStatusMasks(activity, "main-configuration")
                    activity.window.decorView.postOnAnimation {
                        if (!destroyed) applyWechatStatusMasks(activity, "main-configuration-frame")
                    }
                }) || installed
            }
        }
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

    private fun readNativeField(owner: Any?, name: String): Any? = owner?.let { KavaReflector.readField(it, name) }

    private fun wechatHomepageBackdropEnabled(activity: Activity): Boolean =
        activity.javaClass.name == LAUNCHER_ACTIVITY &&
            prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE) &&
            BackgroundBeautyStore.hasImage(context.hostContext(), BackgroundBeautySettings.Slot.WECHAT)
    private val wechatTaskControllers = WeakHashMap<View, WeakReference<Any>>()
    private val wechatGradientControllers = WeakHashMap<View, WeakReference<Any>>()
    private fun rememberWechatTaskController(owner: Any?) {
        val container = readNativeField(owner, "g") as? View ?: return
        if (owner != null) wechatTaskControllers[container] = WeakReference(owner)
    }
    private fun wechatControllerForGradient(view: View): Any? {
        wechatGradientControllers[view]?.get()?.let { return it }
        var node = view.parent as? View
        repeat(3) {
            val current = node ?: return null
            if (current.javaClass.name == "com.tencent.mm.ui.conversation.MainUIView") {
                return wechatTaskControllers.entries.firstOrNull { (container, reference) ->
                    val controller = reference.get() ?: return@firstOrNull false
                    val scrollHost = readNativeField(controller, "f") ?: return@firstOrNull false
                    val list = KavaReflector.findMethodRecursive(scrollHost.javaClass, "a")?.let {
                        runCatching { it.invoke(scrollHost) as? View }.getOrNull()
                    }
                    container.isAttachedToWindow && list?.parent === current
                }?.value?.get()?.also { wechatGradientControllers[view] = WeakReference(it) }
            }
            // Only the verified MainUIView direct child and DynamicBgContainer child paths.
            if (current.javaClass.name != "com.tencent.mm.plugin.multitask.ui.bg.DynamicBgContainer") return null
            node = current.parent as? View
        }
        return null
    }
    private fun applyWechatControllerChrome(owner: Any?) {
        val container = readNativeField(owner, "g") as? View ?: return
        val activity = findActivity(container) ?: return
        if (resolveTarget(activity) != BackgroundBeautySettings.Slot.WECHAT || !hasConfiguredBackground(activity)) return
        val state = states[activity] ?: return
        listOf("U1", "T1").forEach { field ->
            (readNativeField(owner, field) as? View)?.takeIf { findActivity(it) === activity }?.let {
                setTransparentSurface(state, it, BackgroundBeautySettings.Slot.WECHAT)

            }
        }
    }
    private val wechatTitleBackdrops = WeakHashMap<View, Boolean>()
    private val wechatGlVisibility = WeakHashMap<View, Int>()
    private var wechatGlMutation = false
    private fun restoreWechatGl(view: View) {
        val visibility = wechatGlVisibility.remove(view) ?: return
        wechatGlMutation = true
        try { view.visibility = visibility } finally { wechatGlMutation = false }
    }
    private fun protectWechatGl(owner: Any?) {
        val dynamic = readNativeField(owner, "q") as? ViewGroup ?: return
        val activity = findActivity(dynamic) ?: return
        val protected = wechatHomepageBackdropEnabled(activity)
        for (index in 0 until dynamic.childCount) {
            val view = dynamic.getChildAt(index)
            if (view.javaClass.name != "com.tencent.mm.dynamicbackground.view.DynamicBackgroundGLSurfaceView") continue
            if (!protected) { restoreWechatGl(view); continue }
            if (!wechatGlVisibility.containsKey(view)) wechatGlVisibility[view] = view.visibility
            if (view.visibility != View.INVISIBLE) {
                wechatGlMutation = true
                try { view.visibility = View.INVISIBLE } finally { wechatGlMutation = false }

            }
        }
    }
    private fun invalidateWechatBackdrops(activity: Activity) {
        wechatGradientControllers.keys.toList().filter { findActivity(it) === activity }.forEach { it.invalidate() }
        wechatTitleBackdrops.keys.toList().filter { findActivity(it) === activity }.forEach { it.invalidate() }
    }

    private fun completeWechatReturn(activity: Activity, reason: String) {
        if (destroyed || activity.javaClass.name != LAUNCHER_ACTIVITY || resolveTarget(activity) == BackgroundBeautySettings.Slot.CHAT || !hasConfiguredBackground(activity)) return
        preparedRoots.entries.removeAll { it.value.first.activity === activity }
        applyForActivity(activity)
        invalidateWechatBackdrops(activity)

        wechatTaskControllers.entries.toList().filter { findActivity(it.key) === activity }.forEach {
            protectWechatGl(it.value.get())
            applyWechatControllerChrome(it.value.get())
        }

    }

    private fun findWechatVerifiedMethod(clazz: Class<*>?, name: String, vararg types: Class<*>): java.lang.reflect.Method? {
        val method = KavaReflector.findMethodRecursive(clazz, name, *types)

        return method
    }
    private val suppressedDynamicContainers = WeakHashMap<View, Boolean>()
    private fun suppressHomepageDynamicBackground(view: View): Boolean {
        if ((view.parent as? View)?.javaClass?.name != "com.tencent.mm.ui.conversation.MainUIView") return false
        val activity = findActivity(view) ?: return false
        return activity.javaClass.name == LAUNCHER_ACTIVITY &&
            prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE) &&
            BackgroundBeautyStore.hasImage(context.hostContext(), BackgroundBeautySettings.Slot.WECHAT)
    }
    private fun installHomepageDynamicLifecycle(): Boolean {
        val clazz = KavaReflector.loadClass("com.tencent.mm.plugin.multitask.ui.bg.DynamicBgContainer", context.hostClassLoader())
        val activate = findWechatVerifiedMethod(clazz, "a") ?: return false
        val stop = findWechatVerifiedMethod(clazz, "b", Boolean::class.javaPrimitiveType!!) ?: return false
        if (activate.returnType != Void.TYPE || stop.returnType != Void.TYPE) return false
        return hookOnce(activate, before = { param ->
            val container = param.thisObject as? View ?: return@hookOnce
            if (!suppressHomepageDynamicBackground(container)) return@hookOnce
            // b(false) pauses/releases an existing GL view and removes it using WeChat's lifecycle.
            // In the common case e is null: skip activation before allocation or render-thread resume.
            if (readNativeField(container, "e") != null) {
                KavaReflector.invoke(stop, container, false)
                if (readNativeField(container, "e") != null) return@hookOnce
            }
            suppressedDynamicContainers[container] = true
            param.result = null
        })
    }
    private fun restoreHomepageDynamicBackgrounds() {
        suppressedDynamicContainers.keys.toList().forEach { view ->
            if (!suppressHomepageDynamicBackground(view)) {
                suppressedDynamicContainers.remove(view)
                if (view.isAttachedToWindow) {
                    KavaReflector.findMethodRecursive(view.javaClass, "a")?.let { KavaReflector.invoke(it, view) }
                }
            }
        }
    }
    private fun installWechatVerifiedPaths(): Boolean {
        var installed = installHomepageDynamicLifecycle()
        val controllerClass = KavaReflector.loadClass("com.tencent.mm.plugin.taskbar.ui.b0", context.hostClassLoader())
        KavaReflector.declaredConstructors(controllerClass).forEach { constructor ->
            installed = hookOnce(constructor, after = { param -> rememberWechatTaskController(param.thisObject) }) || installed
        }
        findWechatVerifiedMethod(controllerClass, "y", Float::class.javaPrimitiveType!!)?.let { method ->
            installed = hookOnce(method, after = { param ->
                rememberWechatTaskController(param.thisObject)
                protectWechatGl(param.thisObject)
                applyWechatControllerChrome(param.thisObject)
            }) || installed
        }
        val ints = Int::class.javaPrimitiveType!!
        val bools = Boolean::class.javaPrimitiveType!!
        listOf("f" to arrayOf(ints, ints, ints), "m" to arrayOf(bools, bools, ints, ints), "n" to arrayOf(bools)).forEach { (name, types) ->
            findWechatVerifiedMethod(controllerClass, name, *types)?.let { method ->
                installed = hookOnce(method, after = { param ->
                    rememberWechatTaskController(param.thisObject)
                    protectWechatGl(param.thisObject)
                    applyWechatControllerChrome(param.thisObject)
                    if (name != "f") {
                        val owner = param.thisObject
                        listOf("u", "v", "q").forEach { field -> (readNativeField(owner, field) as? View)?.invalidate() }
                        val container = readNativeField(owner, "g") as? View
                        (readNativeField(container, "o") as? View)?.let { title ->
                            title.invalidate()
                            wechatTitleBackdrops.keys.toList().filter { backdrop ->
                                var parent = backdrop.parent as? View
                                var belongs = false
                                repeat(4) {
                                    if (parent === title) belongs = true
                                    parent = parent?.parent as? View
                                }
                                belongs
                            }.forEach { it.invalidate() }
                        }
                    }
                }) || installed
            }
        }
        val gradientClass = KavaReflector.loadClass("com.tencent.mm.dynamicbackground.view.GradientColorBackgroundView", context.hostClassLoader())
        findWechatVerifiedMethod(gradientClass, "onDraw", android.graphics.Canvas::class.java)?.let { method ->
            ownedHooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (destroyed) return
                    val view = param.thisObject as? View ?: return
                    val owner = wechatControllerForGradient(view)
                    var ancestor = view.parent as? View
                    var desktopGradient = false
                    repeat(6) {
                        val parent = ancestor ?: return@repeat
                        if (parent.javaClass.name == APP_BRAND_DESKTOP_CONTAINER) desktopGradient = true
                        ancestor = parent.parent as? View
                    }
                    if (owner == null && !desktopGradient) return
                    if (owner != null) protectWechatGl(owner)
                    val activity = findActivity(view) ?: return
                    if (activity.javaClass.name != LAUNCHER_ACTIVITY || !wechatHomepageBackdropEnabled(activity)) return
                    param.result = null

                }
            })
            installed = true
        }
        val conversationClass = KavaReflector.loadClass("com.tencent.mm.ui.conversation.ConversationListView", context.hostClassLoader())
        findWechatVerifiedMethod(conversationClass, "y")?.let { method ->
            installed = hookOnce(method, after = { param ->
                val list = param.thisObject as? View ?: return@hookOnce
                val owner = readNativeField(list, "m") ?: return@hookOnce
                rememberWechatTaskController(owner)
                val activity = findActivity(list) ?: return@hookOnce
                if (resolveTarget(activity) == BackgroundBeautySettings.Slot.WECHAT && hasConfiguredBackground(activity)) {
                    val state = states[activity] ?: return@hookOnce
                    (readNativeField(list, "C") as? View)?.let {
                        setTransparentSurface(state, it, BackgroundBeautySettings.Slot.WECHAT)

                    }
                }
                protectWechatGl(owner)
            }) || installed
        }
        findWechatVerifiedMethod(View::class.java, "setVisibility", Int::class.javaPrimitiveType!!)?.let { method ->
            // Independent callback: visibility is not covered by the background setter hooks.
            ownedHooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (destroyed || wechatGlMutation) return
                    val view = param.thisObject as? View ?: return
                    if (view.javaClass.name == "com.tencent.mm.plugin.story.ui.view.StoryMuteView") {
                        initialStoryMuteVisibility.remove(view)
                        return
                    }
                    if (view.javaClass.name != "com.tencent.mm.dynamicbackground.view.DynamicBackgroundGLSurfaceView") return
                    val requested = param.args.firstOrNull() as? Int ?: return
                    val owner = wechatControllerForGradient(view)
                    val activity = findActivity(view)
                    if (owner != null && activity != null && activity.javaClass.name == LAUNCHER_ACTIVITY &&
                        wechatHomepageBackdropEnabled(activity)) {
                        wechatGlVisibility[view] = requested
                        param.args[0] = View.INVISIBLE
                    } else wechatGlVisibility.remove(view)
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (destroyed || wechatGlMutation) return
                    val view = param.thisObject as? View ?: return
                    if (view.javaClass.name != "com.tencent.mm.ui.tools.TestTimeForChatting" || view.visibility != View.GONE) return
                    findActivity(view)?.let { activity ->
                        // Finish in the same UI transaction; a next-frame post leaves one frame
                        // with hidden chat content and the previous window backdrop.
                        if (activity.javaClass.name == LAUNCHER_ACTIVITY) completeWechatReturn(activity, "chat-container-hidden-sync")
                        else if (isConversationActivity(activity)) scheduleRefresh(activity, 1)
                    }
                }
            })
            installed = true
        }
        val bottomClass = KavaReflector.loadClass("com.tencent.mm.plugin.taskbar.ui.TaskBarBottomView", context.hostClassLoader())
        findWechatVerifiedMethod(bottomClass, "onDraw", android.graphics.Canvas::class.java)?.let { method ->
            installed = hookOnce(method, before = { param ->
                val view = param.thisObject as? View ?: return@hookOnce
                var parent = view.parent as? View
                var owner: Any? = null
                repeat(4) {
                    val node = parent ?: return@repeat
                    if (node.javaClass.name == "com.tencent.mm.plugin.taskbar.ui.TaskBarContainer") owner = readNativeField(node, "d")
                    parent = node.parent as? View
                }
                val controller = owner ?: return@hookOnce
                val activity = findActivity(view) ?: return@hookOnce
                if (activity.javaClass.name != LAUNCHER_ACTIVITY || !wechatHomepageBackdropEnabled(activity)) return@hookOnce
                wechatTitleBackdrops[view] = true
                param.result = null

            }) || installed
        }
        val chatTabClass = KavaReflector.loadClass("com.tencent.mm.ui.NewChattingTabUI", context.hostClassLoader())
        listOf("u" to emptyArray<Class<*>>(), "s" to arrayOf(Int::class.javaPrimitiveType!!)).forEach { (name, types) ->
            val method = findWechatVerifiedMethod(chatTabClass, name, *types)
            if (method != null) {
                val hooked = hookOnce(method, after = { param ->
                    val owner = param.thisObject
                    val activity = readNativeField(owner, "a") as? Activity ?: return@hookOnce
                    val chatting = readNativeField(owner, "c") as? View ?: return@hookOnce
                    if (name == "s" && param.args.firstOrNull() != View.VISIBLE) return@hookOnce
                    if (chatting.visibility == View.GONE) {
                        activity.window.decorView.postOnAnimation {
                            if (chatting.visibility == View.GONE) completeWechatReturn(activity, "chat-tab-$name")
                        }
                    }
                })

                installed = hooked || installed
            }
        }
        val endClass = KavaReflector.loadClass("com.tencent.mm.ui.uf", context.hostClassLoader())
        findWechatVerifiedMethod(endClass, "onAnimationEnd", android.view.animation.Animation::class.java)?.let { method ->
            installed = hookOnce(method, after = { param ->
                val chatUi = readNativeField(param.thisObject, "d")
                val chatView = readNativeField(chatUi, "c") as? View ?: return@hookOnce
                if (chatView.visibility != View.GONE) return@hookOnce
                val activity = findActivity(chatView) ?: return@hookOnce
                if (activity.javaClass.name != LAUNCHER_ACTIVITY || resolveTarget(activity) == BackgroundBeautySettings.Slot.CHAT || !hasConfiguredBackground(activity)) return@hookOnce
                preparedRoots.entries.removeAll { it.value.first.activity === activity }
                applyForActivity(activity)

            }) || installed
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
        listOf("setImageDrawable" to Drawable::class.java, "setImageBitmap" to Bitmap::class.java).forEach { (name, type) ->
            KavaReflector.findMethodRecursive(ImageView::class.java, name, type)?.let { method ->
                installed = hookOnce(method, after = { param ->
                    if (destroyed || surfaceMutationDepth != 0) return@hookOnce
                    val image = param.thisObject as? ImageView ?: return@hookOnce
                    if (!hasClassName(image, CHATTING_IMAGE_BG_VIEW)) return@hookOnce
                    val activity = findActivity(image) ?: return@hookOnce
                    val state = states[activity] ?: return@hookOnce
                    val snapshot = state.nativeImages[image] ?: return@hookOnce
                    if (resolveTarget(activity) != BackgroundBeautySettings.Slot.CHAT || !hasConfiguredBackground(activity)) return@hookOnce
                    if (image.drawable === snapshot.appliedDrawable) return@hookOnce
                    snapshot.originalDrawable = image.drawable
                    snapshot.originalTintList = image.imageTintList
                    surfaceMutationDepth++
                    try {
                        image.imageTintList = null
                        image.setImageDrawable(snapshot.appliedDrawable)
                    } finally { surfaceMutationDepth-- }
                }) || installed
            }
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

    private val actualViewBackgroundField by lazy {
        runCatching { View::class.java.getDeclaredField("mBackground").apply { isAccessible = true } }.getOrNull()
    }
    private fun actualViewBackground(view: View): Drawable? =
        runCatching { actualViewBackgroundField?.get(view) as? Drawable }.getOrNull()
    private fun backgroundContains(actual: Drawable?, expected: Drawable?, depth: Int = 0): Boolean {
        if (expected == null || actual == null || depth > 4) return false
        if (actual === expected) return true
        if (actual is android.graphics.drawable.InsetDrawable) return backgroundContains(actual.drawable, expected, depth + 1)
        if (actual is LayerDrawable) {
            for (index in 0 until actual.numberOfLayers) {
                if (backgroundContains(actual.getDrawable(index), expected, depth + 1)) return true
            }
        }
        return false
    }
    private fun decorBackgroundApplied(view: View, expected: Drawable?): Boolean {
        if (expected == null || view.background !== expected) return false
        if (actualViewBackgroundField != null) return backgroundContains(actualViewBackground(view), expected)
        // Some ROMs may disallow reading the field. Follow framework drawable wrappers as fallback.
        var callback = expected.callback
        repeat(6) {
            if (callback === view) return true
            callback = (callback as? Drawable)?.callback ?: return false
        }
        return false
    }

    private val decorUpdateDepth = WeakHashMap<View, Int>()
    private fun installDecorBackgroundHook(): Boolean {
        val clazz = KavaReflector.loadClass("com.android.internal.policy.DecorView", context.hostClassLoader()) ?: return false
        var installed = false
        // Both callbacks run after the framework commits its background cache fields.
        listOf("setWindowBackground" to arrayOf(Drawable::class.java),
            "updateBackgroundDrawable" to emptyArray<Class<*>>()).forEach { (name, types) ->
            val method = KavaReflector.findMethodRecursive(clazz, name, *types)
            if (method == null) {

                return@forEach
            }
            val hooked = hookOnce(method, before = { param ->
                val view = param.thisObject as? View ?: return@hookOnce
                if (name == "setWindowBackground") decorUpdateDepth[view] = (decorUpdateDepth[view] ?: 0) + 1
            }, after = { param ->
                val decor = param.thisObject as? View ?: return@hookOnce
                if (name == "setWindowBackground") {
                    val depth = (decorUpdateDepth[decor] ?: 1) - 1
                    if (depth == 0) decorUpdateDepth.remove(decor) else decorUpdateDepth[decor] = depth
                }
                if ((decorUpdateDepth[decor] ?: 0) != 0) return@hookOnce
                if (surfaceMutationDepth != 0 || Looper.myLooper() != Looper.getMainLooper()) return@hookOnce
                val view = param.thisObject as? View ?: return@hookOnce
                val state = surfaceOwners[view] ?: return@hookOnce
                if (view !== state.activity.window.decorView) return@hookOnce
                val snapshot = state.surfaces[view] ?: return@hookOnce
                val applied = snapshot.appliedBackground ?: return@hookOnce
                val slot = snapshot.slot ?: return@hookOnce
                if (!prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE) ||
                    !BackgroundBeautyStore.hasImage(context.hostContext(), slot)) return@hookOnce
                if (decorBackgroundApplied(view, applied)) return@hookOnce
                val host = view.background
                if (host !== applied) {
                    snapshot.originalBackground = host
                    snapshot.originalTintList = view.backgroundTintList
                    snapshot.originalTintMode = view.backgroundTintMode
                }
                surfaceMutationDepth++
                try {
                    // A new identity also repairs a pre-existing split getter/actual state.
                    val bitmap = snapshot.appliedBitmap
                    val opacity = snapshot.appliedOpacity
                    if (bitmap != null && !bitmap.isRecycled && opacity != null) {
                        snapshot.appliedBackground = ScreenAlignedBitmapDrawable(bitmap, view, opacity,
                            resolveThemeBackground(view.context) or (0xff shl 24),
                followHorizontalMotion = slot == BackgroundBeautySettings.Slot.CHAT)
                    }
                    view.backgroundTintList = null
                    view.backgroundTintMode = null
                    view.background = snapshot.appliedBackground
                    restoreSurfaceGeometry(view, snapshot)
                    view.invalidate()
                } finally { surfaceMutationDepth-- }

            })

            installed = hooked || installed
        }
        return installed
    }
    private fun installBackgroundWriteHook(): Boolean {
        var installed = false
        listOf("setBackgroundDrawable" to Drawable::class.java, "setBackgroundColor" to Integer.TYPE).forEach { (name, parameter) ->
            KavaReflector.findMethodRecursive(View::class.java, name, parameter)?.let { method ->
                installed = hookOnce(method, after = { param ->
                    if (surfaceMutationDepth != 0 || Looper.myLooper() != Looper.getMainLooper()) return@hookOnce
                    val view = param.thisObject as? View ?: return@hookOnce
                    if (t.mma.hooks.items.floatingchat.FloatingChatCardVisuals.contains(view)) return@hookOnce
                    val state = surfaceOwners[view] ?: run {
                        // This global setter also sees every recycled message bubble.
                        // Only a known contacts list ancestor can need late-row repair;
                        // never resolve the active page or walk the decor tree here.
                        if (framePageSlots.values.none { it == BackgroundBeautySettings.Slot.CONTACTS }) return@hookOnce
                        var ancestor = view.parent as? View
                        var depth = 0
                        while (ancestor != null && depth++ < 12) {
                            val parent = ancestor.parent as? View
                            if (parent != null && homeLists[parent] == BackgroundBeautySettings.Slot.CONTACTS) {
                                dirtyBoundRows[ancestor] = true
                                break
                            }
                            ancestor = parent
                        }
                        return@hookOnce
                    }
                    val snapshot = state.surfaces[view] ?: return@hookOnce
                    // DecorView.getBackground is not View.mBackground; never reenter its outer update here.
                    if (view === state.activity.window.decorView) return@hookOnce
                    if (view.background !== snapshot.appliedBackground) {

                        if (snapshot.slot == BackgroundBeautySettings.Slot.CONTACTS) {
                            captureContactGeometry(view, snapshot)
                            if (isContactIconSurface(view)) {
                                snapshot.originalBackground = view.background
                                snapshot.originalTintList = view.backgroundTintList
                                snapshot.originalTintMode = view.backgroundTintMode
                                snapshot.appliedBackground = null
                                return@hookOnce
                            }
                        }
                        val applied = snapshot.appliedBackground
                        snapshot.originalBackground = view.background
                        snapshot.originalTintList = view.backgroundTintList
                        snapshot.originalTintMode = view.backgroundTintMode
                        if (applied != null && prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE) &&
                            snapshot.slot?.let { state.loadedBitmaps[it]?.bitmap?.isRecycled == false } == true) {
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
        installed = installDecorBackgroundHook() || installed
        listOf("setStatusBarColor", "setNavigationBarColor").forEach { name ->
            val windowClass = KavaReflector.loadClass("com.android.internal.policy.PhoneWindow", context.hostClassLoader())
            KavaReflector.findMethodRecursive(windowClass, name, Integer.TYPE)?.let { method ->
                hookOnce(method, before = { param ->
                    if (surfaceMutationDepth != 0 || restoringBounce) return@hookOnce

                    if (surfaceMutationDepth != 0 || restoringBounce) return@hookOnce
                    val window = param.thisObject as? android.view.Window ?: return@hookOnce
                    val activity = findActivity(window.decorView) ?: return@hookOnce
                    val slot = resolveTarget(activity) ?: return@hookOnce
                    if (states[activity]?.systemBarsApplied == true && hasConfiguredBackground(activity)) {
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
    private val contactOverlays = WeakHashMap<View, BackgroundBeautyFeedbackDrawable>()
    private val touchFeedbackRows = WeakHashMap<View, View>()
    private val touchFeedbackOrigins = WeakHashMap<View, Pair<Float, Float>>()
    // Resolve structural rows as well as clickable children: WeChat often handles
    // clicks in its adapter, leaving the actual content container non-clickable.
    private fun contactTouchRegion(row: View, x: Float, y: Float): Rect? {
        val density = row.resources.displayMetrics.density
        val minimumHeight = 40f * density
        var best: Rect? = null
        var bestArea = Long.MAX_VALUE
        var visited = 0
        fun visit(group: ViewGroup, localX: Float, localY: Float) {
            if (++visited > 80) return
            for (i in group.childCount - 1 downTo 0) {
                val child = group.getChildAt(i)
                val cx = localX + group.scrollX - child.left - child.translationX
                val cy = localY + group.scrollY - child.top - child.translationY
                if (child.visibility != View.VISIBLE || cy < 0 || cy >= child.height) continue
                if (child is ViewGroup && child.isEnabled && child.width >= row.width * 0.75f && child.height >= minimumHeight && child.height <= 88f * density) {
                    val rect = Rect(0, 0, child.width, child.height)
                    (row as ViewGroup).offsetDescendantRectToMyCoords(child, rect)
                    val area = rect.width().toLong() * rect.height()
                    if (area < bestArea) { best = rect; bestArea = area }
                }
                if (child is ViewGroup) visit(child, cx, cy)
            }
        }
        if (row is ViewGroup) visit(row, x, y)
        // A normal leaf row needs no descendant. Never fall back to a tall
        // compound header (friend requests, action entries or section labels).
        if (best == null && row.height >= minimumHeight && row.height <= 88f * density) {
            val hasHeader = row is ViewGroup && (0 until row.childCount).any {
                val child = row.getChildAt(it)
                child.visibility == View.VISIBLE && child.width >= row.width * 0.75f &&
                    child.height > 0 && child.height < minimumHeight && child.top < minimumHeight / 2
            }
            if (!hasHeader) best = Rect(0, 0, row.width, row.height)
        }
        // Horizontal text insets do not define the row: the avatar is its sibling.
        return best?.apply { left = 0; right = row.width }
    }
    // Profile headers contain independent actions; spacer/footer rows are not actions.
    private fun meTouchRegion(row: View, x: Float, y: Float): Rect? {
        val density = row.resources.displayMetrics.density
        var visited = 0
        fun hasLabel(view: View): Boolean {
            if (++visited > 160 || view.visibility != View.VISIBLE || view.alpha <= 0f) return false
            if (view is android.widget.TextView && view.text.isNotBlank()) return true
            return view is ViewGroup && (0 until view.childCount).any { hasLabel(view.getChildAt(it)) }
        }
        if (!hasLabel(row)) return null
        var best: Rect? = null
        var bestArea = Long.MAX_VALUE
        visited = 0
        fun visit(view: View) {
            if (++visited > 160 || view.visibility != View.VISIBLE || view.alpha <= 0f) return
            val rect = Rect(0, 0, view.width, view.height)
            if (view !== row) (row as? ViewGroup)?.offsetDescendantRectToMyCoords(view, rect)
            if (!rect.contains(x.toInt(), y.toInt())) return
            if (view.isEnabled && (view.isClickable || view.isLongClickable) &&
                rect.width() > 0 && rect.height() > 0 && rect.height() <= 100f * density) {
                val area = rect.width().toLong() * rect.height()
                if (area < bestArea) { best = rect; bestArea = area }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(row)
        if (best != null) return best
        // Adapter menu rows need not be clickable themselves. Tall profile headers
        // must never use this fallback, and empty adapter spacers were rejected above.
        return if (row.height in (40f * density).toInt()..(100f * density).toInt())
            Rect(0, 0, row.width, row.height) else null
    }
    private fun isContactIconSurface(view: View): Boolean {
        // Native contact icons can be plain/custom Views with a background or
        // foreground drawable, and may be visited before the first measurement.
        if (view is ImageView) return true
        val parent = view.parent as? View ?: return false
        val maxIcon = 80f * view.resources.displayMetrics.density
        val params = view.layoutParams
        if (params != null && params.width > 0 && params.height > 0 &&
            params.width <= maxIcon && params.height <= maxIcon) return true
        // Preserve compact drawable-backed icon containers, not full-width rows.
        return view.width > 0 && view.height > 0 && parent.width > 0 &&
            view.width <= maxIcon && view.height <= maxIcon && view.width < parent.width / 2
    }
    private fun captureContactGeometry(view: View, snapshot: SurfaceSnapshot) {
        snapshot.originalPaddingLeft = view.paddingLeft
        snapshot.originalPaddingTop = view.paddingTop
        snapshot.originalPaddingRight = view.paddingRight
        snapshot.originalPaddingBottom = view.paddingBottom
        snapshot.originalMinimumWidth = view.minimumWidth
        snapshot.originalMinimumHeight = view.minimumHeight
    }
    private fun updateListRipple(list: ViewGroup, event: android.view.MotionEvent) {
        // A cancelled drag has no ripple to update; skip subsequent owner queries.
        if (event.actionMasked != android.view.MotionEvent.ACTION_DOWN &&
            !touchFeedbackOrigins.containsKey(list)) return
        val slot = homeLists[list] ?: return
        if (!prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE)) return
        val activity = findActivity(list) ?: return
        if (states[activity]?.loadedBitmaps?.get(slot)?.bitmap?.isRecycled != false) return
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                for (index in list.childCount - 1 downTo 0) {
                    val row = list.getChildAt(index)
                    val x = event.x + list.scrollX - row.left - row.translationX
                    val y = event.y + list.scrollY - row.top - row.translationY
                    if (row.visibility == View.VISIBLE && x >= 0 && y >= 0 && x < row.width && y < row.height) {
                        if (dirtyBoundRows.remove(row) == true || surfaceOwners[row] == null) applyBoundRow(list, row)
                        (row.background as? BackgroundBeautyFeedbackDrawable)?.let { ripple ->
                            val region = when (slot) {
                                BackgroundBeautySettings.Slot.CONTACTS -> contactTouchRegion(row, x, y)
                                BackgroundBeautySettings.Slot.ME -> meTouchRegion(row, x, y)
                                else -> null
                            }
                            ripple.setTouchRegion(region)
                            ripple.setHotspot(x, y)
                            if (slot == BackgroundBeautySettings.Slot.CONTACTS || slot == BackgroundBeautySettings.Slot.ME) {
                                contactOverlays.remove(row)?.let { row.overlay.remove(it) }
                                ripple.setTouchPressed(false)
                                if (region != null) {
                                    val overlay = BackgroundBeautyFeedbackDrawable(manualOnly = true)
                                    overlay.bounds = Rect(0, 0, row.width, row.height)
                                    overlay.setTouchRegion(region)
                                    row.overlay.add(overlay)
                                    overlay.setHotspot(x, y)
                                    overlay.setTouchPressed(true)
                                    contactOverlays[row] = overlay
                                }
                            } else ripple.setTouchPressed(true)

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
        touchFeedbackRows.remove(list)?.let { row ->
            (row.background as? BackgroundBeautyFeedbackDrawable)?.setTouchPressed(false)
            contactOverlays[row]?.let { overlay ->
                overlay.setTouchPressed(false)
                main.postDelayed({
                    row.overlay.remove(overlay)
                    if (contactOverlays[row] === overlay) contactOverlays.remove(row)
                }, 600)
            }
        }
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
                            preparedRoots.remove(ready)
                            findActivity(view)?.let { activity ->
                                val state = states[activity]
                                val slot = homeLists[view]
                                if (state != null && slot != null) preparePage(state, ready, slot)
                            }
                        }
                    }
                    val slot = homeLists[view]
                    val owner = findActivity(view)?.let { states[it] }
                    if (slot != null && owner?.loadedBitmaps?.get(slot)?.bitmap?.isRecycled == false &&
                        prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE)) {
                        for (index in 0 until view.childCount) {
                            val row = view.getChildAt(index)
                            val snapshot = owner.surfaces[row]
                            if (dirtyBoundRows.remove(row) == true || snapshot == null ||
                                (slot == BackgroundBeautySettings.Slot.CONTACTS && row.background !== snapshot.appliedBackground)) {
                                applyHomeRow(owner, row, slot)
                            }
                        }
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
                view.selector = BackgroundBeautyFeedbackDrawable(manualOnly = homeLists[view] == BackgroundBeautySettings.Slot.CONTACTS || homeLists[view] == BackgroundBeautySettings.Slot.ME)
            }
            val adapter = view.adapter ?: return
            if (listAdapters[view] === adapter) return
            listAdapters[view] = adapter
            KavaReflector.findMethodRecursive(adapter.javaClass, "getView", Integer.TYPE, View::class.java, ViewGroup::class.java)?.let { method ->
                hookOnce(method, after = { param ->
                    val list = param.args.getOrNull(2) as? View ?: return@hookOnce
                    val row = param.result as? View ?: return@hookOnce
                    // getView may run repeatedly while measuring/layouting the same row.
                    // Coalesce work until preDraw, after the adapter has finished rebinding it.
                    if (homeLists.containsKey(list)) dirtyBoundRows[row] = true
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
                    // preDraw applies the final bound state once, including payload rebinds.
                })
            }
            type = type.superclass
        }
    }

    private fun applyBoundRow(list: View, row: View) {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        val slot = homeLists[list] ?: return
        val activity = findActivity(list) ?: return
        if (!prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE)) return
        val state = states[activity] ?: return
        if (state.loadedBitmaps[slot]?.bitmap?.isRecycled != false) return
        applyHomeRow(state, row, slot)
        dirtyBoundRows.remove(row)
    }

    private fun applyHomeRow(state: HostState, row: View, slot: BackgroundBeautySettings.Slot) {
        if (t.mma.hooks.items.homeextension.HomeExtensionSurface.contains(row)) return
        contactOverlays.remove(row)?.let { row.overlay.remove(it) }
        state.surfaces[row]?.let { snapshot ->
            snapshot.originalPaddingLeft = row.paddingLeft
            snapshot.originalPaddingTop = row.paddingTop
            snapshot.originalPaddingRight = row.paddingRight
            snapshot.originalPaddingBottom = row.paddingBottom
            snapshot.originalMinimumWidth = row.minimumWidth
            snapshot.originalMinimumHeight = row.minimumHeight
        }
        setTransparentSurface(state, row, slot, preserveFeedback = true)
        if (isWechatTaskSurface(row)) return
        val queue = ArrayDeque<View>()
        if (row is ViewGroup) for (i in 0 until row.childCount) queue.add(row.getChildAt(i))
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 160) {
            val child = queue.removeFirst()
            if (t.mma.hooks.items.homeextension.HomeExtensionSurface.contains(child)) continue
            if (isComposeSurface(child) || isBottomBar(child) || isWechatTaskSurface(child)) continue
            if (child is ViewGroup) {
                // Register background-less contacts containers too: native code can
                // give them a background after resume without rebinding the row.
                if (child.background != null || slot == BackgroundBeautySettings.Slot.CONTACTS)
                    setTransparentSurface(state, child, slot, preserveFeedback = true)
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

    private val framePageSlots = WeakHashMap<Activity, BackgroundBeautySettings.Slot>()
    private fun scheduleRefresh(activity: Activity, retries: Int = 0) {
        if (destroyed || activity.isFinishing || activity.isDestroyed) return
        synchronized(observedActivities) { observedActivities[activity] = true }
        val task = synchronized(pendingRefreshes) {
            if (pendingRefreshes.containsKey(activity)) return@synchronized null
            Runnable {
                synchronized(pendingRefreshes) { pendingRefreshes.remove(activity) }
                // Resume/focus refreshes revisit prepared header rows once, including
                // preference descendants replaced without an adapter bind.
                homeLists.entries.toList().forEach { (list, slot) ->
                    if (slot == BackgroundBeautySettings.Slot.CONTACTS && findActivity(list) === activity && list is ViewGroup) {
                        for (i in 0 until list.childCount) dirtyBoundRows[list.getChildAt(i)] = true
                    }
                }
                applyForActivity(activity)

                if (retries > 0 && !destroyed) {
                    main.postDelayed({ scheduleRefresh(activity, retries - 1) }, 120L)
                }
            }.also { pendingRefreshes[activity] = it }
        } ?: return
        activity.window.decorView.postOnAnimation(task)
    }

    private fun refreshAll() {
        restoreHomepageDynamicBackgrounds()
        if (!prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE) ||
            !BackgroundBeautyStore.hasImage(context.hostContext(), BackgroundBeautySettings.Slot.ME)) {
            restoreInitialStoryMuteViews()
        }
        wechatTitleBackdrops.keys.toList().forEach { it.invalidate() }
        wechatGlVisibility.keys.toList().forEach { view ->
            val owner = wechatControllerForGradient(view)
            val activity = findActivity(view)
            if (owner == null || activity == null || !wechatHomepageBackdropEnabled(activity)) restoreWechatGl(view)
        }
        wechatGradientControllers.keys.toList().forEach { it.invalidate() }
        wechatTaskControllers.values.toList().forEach { protectWechatGl(it.get()) }
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

    fun onChatPageChanged(entered: Boolean) {
        main.post {
            val launchers = synchronized(observedActivities) { observedActivities.keys.filter { it.javaClass.name == LAUNCHER_ACTIVITY } }
            launchers.forEach { activity ->

                scheduleRefresh(activity, 1)
                invalidateWechatBackdrops(activity)

                if (!entered) {
                    preparedRoots.entries.removeAll { it.value.first.activity === activity }
                    scheduleRefresh(activity, 1)
                }
            }
        }
    }
    private fun applyForActivity(activity: Activity) {
        val traceStarted = t.mma.utils.ChatScrollDiagnostic.beginRefresh()
        try {
        if (destroyed || activity.isFinishing || activity.isDestroyed) return
        if (!prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE)) {
            clearActivity(activity)
            return
        }
        val slot = t.mma.utils.ChatScrollDiagnostic.phase("background.target") { resolveTarget(activity) } ?: run {
            val animating = states[activity]?.surfaces?.keys?.any(::hasRunningChatAnimation) == true
            if (animating) {
                // BaseConversationUI marks the container GONE before running its exit animation.
                // Preserve its image until the last animation frame has been drawn.
                scheduleRefresh(activity)
            } else clearActivity(activity)
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
        if (slot == BackgroundBeautySettings.Slot.WECHAT) {
            preloadedHome?.takeIf { it.revision == revision && !it.bitmap.isRecycled }?.let { loaded ->
                preloadedHome = null
                state.loadedBitmaps[slot] = loaded
                applyBitmap(state, slot, loaded.bitmap)
                return
            }
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

        } finally { t.mma.utils.ChatScrollDiagnostic.endRefresh(traceStarted) }
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
        t.mma.utils.ChatScrollDiagnostic.phase("background.cleanup") {
            state.surfaces.entries.toList().forEach { (view, snapshot) ->
                if (!view.isAttachedToWindow) {
                    restoreSurface(view, snapshot)
                    state.surfaces.remove(view)
                }
            }
        }
        val activity = state.activity
        val opacity = BackgroundBeautySettings.opacity(
            prefs.getFloat(
                BackgroundBeautySettings.opacityKey(slot),
                BackgroundBeautySettings.DEFAULT_OPACITY
            )
        )
        val host = t.mma.utils.ChatScrollDiagnostic.phase("background.host") { resolveBackgroundHost(activity) } ?: return
        if (slot == BackgroundBeautySettings.Slot.CHAT) {
            // Embedded chat animates over the conversation page in the same window.
            // Its bitmap belongs to the moving chat container, never the shared decor.
            val chatHost = t.mma.utils.ChatScrollDiagnostic.phase("background.chatHost") { chatBackgroundHost(activity) } ?: return
            val movingContent = t.mma.utils.ChatScrollDiagnostic.phase("background.movingContent") { chatMovingContent(chatHost) }
            // The target can appear after initial layout. Retire the previous outer
            // bitmap explicitly before the transparency guard starts protecting it.
            t.mma.utils.ChatScrollDiagnostic.phase("background.retireChatSurfaces") {
                state.surfaces.entries.toList().filter { (view, snapshot) ->
                    snapshot.slot == slot && snapshot.appliedBitmap != null && view !== movingContent &&
                        isDescendantOf(view, chatHost)
                }.forEach { (view, snapshot) ->
                    restoreSurface(view, snapshot)
                    state.surfaces.remove(view)
                }
            }
            t.mma.utils.ChatScrollDiagnostic.phase("background.imageSurface") {
                if (movingContent !== chatHost) setTransparentSurface(state, chatHost, slot, clearForeground = true)
                setImageSurface(state, movingContent, slot, bitmap, opacity)
            }
            t.mma.utils.ChatScrollDiagnostic.phase("background.chatApply") {
                applyChatBackground(state, bitmap, opacity)
            }
        } else {
            // Keep cached chat bitmaps, but release the closed chat's managed surfaces.
            state.surfaces.entries.toList().filter { it.value.slot == BackgroundBeautySettings.Slot.CHAT && !hasRunningChatAnimation(it.key) }.forEach { (view, snapshot) ->
                restoreSurface(view, snapshot)
                state.surfaces.remove(view)
            }
            state.nativeImages.entries.toList().filter { !hasRunningChatAnimation(it.key) }.forEach { (view, snapshot) ->
                restoreNativeImage(view, snapshot)
                state.nativeImages.remove(view)
            }
            setImageSurface(state, activity.window.decorView, slot, bitmap, opacity)
            if (host !== activity.window.decorView) setTransparentSurface(state, host, slot)
            val content = activity.findViewById<View>(android.R.id.content)
            if (content != null && content !== host) setTransparentSurface(state, content, slot)
            applyLauncherBackground(state, slot, bitmap, opacity)
        }
        t.mma.utils.ChatScrollDiagnostic.phase("background.chrome") {
            applyChromeTransparency(state, slot)
        }
        t.mma.utils.ChatScrollDiagnostic.phase("background.systemBars") {
            applySystemBars(state)
        }
        t.mma.utils.ChatScrollDiagnostic.phase("background.invalidate") {
            if (state.activity.javaClass.name == LAUNCHER_ACTIVITY) invalidateWechatBackdrops(state.activity)
        }
        if (state.lastLoggedSlot != slot) {
            state.lastLoggedSlot = slot

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
        applyWhitelistedSurfaces(state, root, slot)
        val pendingList = homeLists.entries.any { (list, listSlot) ->
            listSlot == slot && list is ViewGroup && list.childCount == 0 && isDescendantOf(list, root)
        }
        if (!pendingList) preparedRoots[root] = state to revision
        else pendingPageRoots[root] = slot

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
        val chatHost = chatBackgroundHost(activity) ?: return
        val image = synchronized(nativeChatImages) {
            nativeChatImages[activity]?.get()?.takeIf { it.isAttachedToWindow && isDescendantOf(it, chatHost) }
        } ?: findDescendant(chatHost, CHATTING_IMAGE_BG_VIEW) as? ImageView
        if (image != null) {
            setNativeImage(state, image, bitmap, opacity)
            var parent = image.parent as? View
            while (parent != null && parent !== chatHost) {
                setTransparentSurface(state, parent, BackgroundBeautySettings.Slot.CHAT, clearForeground = true)
                parent = parent.parent as? View
            }
        }
        chatHost.let { root ->
            applyWhitelistedSurfaces(state, root, BackgroundBeautySettings.Slot.CHAT)
            applyChatChromeRoot(state, root)
            findDescendant(root, "com.tencent.mm.view.SmileyPanelImpl")?.let(::applyEmojiPanelSurfaces)
        }
    }

    private fun isWechatDark(view: View): Boolean {
        val clazz = KavaReflector.loadClass("com.tencent.mm.ui.gk", context.hostClassLoader())
        val method = KavaReflector.findMethodRecursive(clazz, "D")
        return (method?.let { KavaReflector.invoke(it, null) } as? Boolean)
            ?: ((view.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES)
    }
    private data class IconStroke(val drawable: Drawable, val width: Int, val height: Int, val scale: Float, val pixels: Int, val top: Int, val bottom: Int)
    private val measuredIconStrokes = WeakHashMap<View, IconStroke>()
    private fun chatInputStrokeWidth(footer: View): Int {
        // 8.0.78 ChatFooter.B0(): smile button = id/bqr (0x7f0a10a3).
        val icon = footer.findViewById<ImageView>(resourceId("bqr"))
        val drawable = icon?.drawable
        if (icon == null || drawable == null || icon.width <= 0 || icon.height <= 0) {

            return (footer.resources.displayMetrics.density * 1.2f).toInt().coerceAtLeast(1)
        }
        val matrix = FloatArray(9).also { icon.imageMatrix.getValues(it) }
        val scale = matrix[android.graphics.Matrix.MSCALE_Y]
        measuredIconStrokes[icon]?.takeIf { it.drawable === drawable && it.width == icon.width && it.height == icon.height && it.scale == scale }?.let { return it.pixels }
        // Rasterize the actual ImageView viewport with its padding and matrix: no intrinsic-size
        // assumption and no alpha threshold that discards antialiased ring edge pixels.
        val bitmap = Bitmap.createBitmap(icon.width, icon.height, Bitmap.Config.ARGB_8888)
        var weighted = 0f
        var inkTop = icon.height
        var inkBottom = 0
        try {
            val canvas = Canvas(bitmap)
            canvas.translate(icon.paddingLeft.toFloat(), icon.paddingTop.toFloat())
            canvas.concat(icon.imageMatrix)
            drawable.draw(canvas)
            for (y in 0 until icon.height) {
                for (column in 0 until icon.width) {
                    if (Color.alpha(bitmap.getPixel(column, y)) > 0) {
                        inkTop = minOf(inkTop, y)
                        inkBottom = y + 1
                        break
                    }
                }
            }
            val x = (icon.paddingLeft + (icon.width - icon.paddingLeft - icon.paddingRight) / 2).coerceIn(0, icon.width - 1)
            val samples = IntArray(icon.height / 2) { y -> Color.alpha(bitmap.getPixel(x, y)) }
            val peak = samples.maxOrNull() ?: 0
            if (peak > 0) {
                var started = false
                for (alpha in samples) {
                    if (alpha > 0) { started = true; weighted += alpha.toFloat() / peak }
                    else if (started) break
                }
            }
        } catch (error: Throwable) {
            HLog.e("$TAG 表情按钮实际线宽采样失败: ${drawable.javaClass.name}", error)
        } finally { bitmap.recycle() }
        val pixels = if (weighted > 0) kotlin.math.ceil(weighted).toInt() else (footer.resources.displayMetrics.density * 1.2f).toInt().coerceAtLeast(1)

        if (weighted > 0) measuredIconStrokes[icon] = IconStroke(drawable, icon.width, icon.height, scale, pixels, inkTop, inkBottom)
        return pixels
    }
    private fun applySpeechSurfaces(state: HostState, footer: View) {
        // SpeechInputLayout constructor and ew_.xml verified in 8.0.78.
        val speech = findDescendant(footer, "com.tencent.mm.pluginsdk.ui.SpeechInputLayout") ?: return
        val queue = ArrayDeque<View>()
        queue.add(speech)
        var count = 0
        while (queue.isNotEmpty() && count++ < 80) {
            val node = queue.removeFirst()
            if (node.javaClass == View::class.java || node is ViewGroup || node is ImageView) {
                // Match the footer traversal below: speech surfaces have no separate feedback.
                // Otherwise this pass installs feedback and the next pass removes it,
                // replacing background/foreground on every layout and requesting another.
                setTransparentSurface(state, node, BackgroundBeautySettings.Slot.CHAT, clearForeground = true)
            }
            if (node is ViewGroup) for (i in 0 until node.childCount) queue.add(node.getChildAt(i))
        }
    }
    private fun applyChatNoticeSurfaces(state: HostState, root: View) {
        val queue = ArrayDeque<View>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (hasClassName(node, "com.tencent.mm.ui.tipsbar.ChatTipsBarGroup")) {
                // 8.0.78 s3.xml: hyi card body; ow1/ovv scrims; ovt shadow.
                // Preserve all image/text content, adapter binding and notice clicks.
                val surfaces = listOfNotNull(node,
                    node.findViewById<View>(resourceId("hyi")),
                    node.findViewById<View>(resourceId("ow1")),
                    node.findViewById<View>(resourceId("ovv")),
                    node.findViewById<View>(resourceId("ovt")),
                    node.findViewById<View>(resourceId("m98")))
                // s4.xml notice rows also have their own bce background. Recycler
                // children are created after the group attaches; cover their containers.
                val rows = node.findViewById<ViewGroup>(resourceId("m98"))
                val rowNodes = ArrayDeque<View>()
                if (rows != null) for (i in 0 until rows.childCount) rowNodes.add(rows.getChildAt(i))
                while (rowNodes.isNotEmpty()) {
                    val row = rowNodes.removeFirst()
                    if (row is ViewGroup || row.javaClass == View::class.java) {
                        setTransparentSurface(state, row, BackgroundBeautySettings.Slot.CHAT, clearForeground = true)
                    }
                    if (row is ViewGroup) for (i in 0 until row.childCount) rowNodes.add(row.getChildAt(i))
                }
                surfaces.forEach { surface ->
                    setTransparentSurface(state, surface, BackgroundBeautySettings.Slot.CHAT, clearForeground = true)
                }
            }
            if (node is ViewGroup) for (i in 0 until node.childCount) queue.add(node.getChildAt(i))
        }
    }
    private fun applyChatVoiceSurfaces(state: HostState, root: View) {
        // Both legacy and modern input trees may coexist. Do not assume the visible
        // microphone and its shadow belong to the first ChatFooter in the hierarchy.
        val talkId = resourceId("p6x")
        val overlayId = resourceId("sub")
        val queue = ArrayDeque<View>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (talkId != 0 && node.id == talkId && node is android.widget.Button) {
                setTransparentSurface(state, node, BackgroundBeautySettings.Slot.CHAT, clearForeground = true, preserveFeedback = true)
            }
            if (overlayId != 0 && node.id == overlayId && node is ViewGroup) {
                // t8.xml sub/suc: anonymous View uses drawable/de7.png, outside SpeechInputLayout.
                val layers = ArrayDeque<View>()
                layers.add(node)
                while (layers.isNotEmpty()) {
                    val layer = layers.removeFirst()
                    if (layer.javaClass == View::class.java) {
                        setTransparentSurface(state, layer, BackgroundBeautySettings.Slot.CHAT, clearForeground = true)
                    }
                    if (layer is ViewGroup) for (i in 0 until layer.childCount) layers.add(layer.getChildAt(i))
                }
            }
            if (node is ViewGroup) for (i in 0 until node.childCount) queue.add(node.getChildAt(i))
        }
    }
    private fun applyEmojiPanelSurfaces(panel: View) {
        val activity = findActivity(panel) ?: return
        if (destroyed || resolveTarget(activity) != BackgroundBeautySettings.Slot.CHAT || !hasConfiguredBackground(activity)) return
        val state = states[activity] ?: return
        val manager = KavaReflector.readField(panel, "f") ?: return
        // a77.xml tab background (m), nested panel (p), tab wrapper (l).
        listOf("m", "p", "l").forEach { field ->
            (KavaReflector.readField(manager, field) as? View)?.let { view ->
                setTransparentSurface(state, view, BackgroundBeautySettings.Slot.CHAT, clearForeground = true, preserveFeedback = true)
            }
        }
    }
    private val appGridItems = WeakHashMap<View, Boolean>()
    private fun installChatChromeLifecycle() {
        // 8.0.78 AppGrid adapter c.getView binds each page's icon surfaces.
        val appAdapter = KavaReflector.loadClass("com.tencent.mm.pluginsdk.ui.chat.c", context.hostClassLoader())
        findWechatVerifiedMethod(appAdapter, "getView", Integer.TYPE, View::class.java, ViewGroup::class.java)?.let { method ->
            hookOnce(method, after = { param ->
                val grid = param.args.getOrNull(2) as? View ?: return@hookOnce
                if (!hasClassName(grid, "com.tencent.mm.pluginsdk.ui.chat.AppGrid")) return@hookOnce
                val row = param.result as? View ?: return@hookOnce
                val activity = findActivity(grid) ?: return@hookOnce
                val state = states[activity] ?: return@hookOnce
                if (!hasConfiguredBackground(activity) || resolveTarget(activity) != BackgroundBeautySettings.Slot.CHAT) return@hookOnce
                appGridItems[row] = true
                val holder = row.tag
                val key = holder?.let { KavaReflector.readField(it, "p") as? String }
                val iconName = when (key) {
                    "favorite" -> "icons_filled_favorites"
                    "contact_card" -> "icons_filled_contacts"
                    "file" -> "icons_filled_folder"
                    "card" -> "icons_filled_cards_offers"
                    "music" -> "icons_filled_music"
                    "voice_input" -> "icons_outlined_mic_on"
                    "transfer" -> "icons_outlined_transfer"
                    else -> null
                }
                val icon = row.findViewById<ImageView>(resourceId("a10"))
                if (icon != null && iconName == null) {
                    state.nativeImages.remove(icon)?.let { restoreNativeImage(icon, it) }
                }
                if (icon != null && iconName != null) {
                    val id = icon.resources.getIdentifier(iconName, "raw", activity.packageName)
                    if (id != 0) {
                        val snapshot = state.nativeImages[icon] ?: NativeImageSnapshot(icon).also { state.nativeImages[icon] = it }
                        if (icon.drawable !== snapshot.appliedDrawable) snapshot.originalDrawable = icon.drawable
                        icon.setImageResource(id)
                        snapshot.appliedDrawable = icon.drawable
                        snapshot.slot = BackgroundBeautySettings.Slot.CHAT
                    }
                }
                val queue = ArrayDeque<View>()
                queue.add(row)
                while (queue.isNotEmpty()) {
                    val view = queue.removeFirst()
                    if (view is ViewGroup || view is ImageView || view.javaClass == View::class.java) {
                        setTransparentSurface(state, view, BackgroundBeautySettings.Slot.CHAT, clearForeground = true, preserveFeedback = true)
                    }
                    if (view is ViewGroup) for (i in 0 until view.childCount) queue.add(view.getChildAt(i))
                }
            })
        }

        val noticeClass = KavaReflector.loadClass("com.tencent.mm.ui.tipsbar.ChatTipsBarGroup", context.hostClassLoader())
        listOf("onAttachedToWindow", "c", "d", "onLayout").forEach { name ->
            val types = if (name == "onLayout") arrayOf(Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!) else emptyArray()
            KavaReflector.findMethodRecursive(noticeClass, name, *types)?.let { method ->
                hookOnce(method, after = { param ->
                    val group = param.thisObject as? View ?: return@hookOnce
                    if (!hasClassName(group, "com.tencent.mm.ui.tipsbar.ChatTipsBarGroup")) return@hookOnce
                    val activity = findActivity(group) ?: return@hookOnce
                    if (destroyed || resolveTarget(activity) != BackgroundBeautySettings.Slot.CHAT || !hasConfiguredBackground(activity)) return@hookOnce
                    val state = states[activity] ?: return@hookOnce
                    applyChatNoticeSurfaces(state, group)
                })
            }
        }

        val emojiClass = KavaReflector.loadClass("com.tencent.mm.view.SmileyPanelImpl", context.hostClassLoader())
        listOf("onAttachedToWindow" to emptyArray<Class<*>>(),
            "onLayout" to arrayOf(Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
            "setTabBackgroundColor" to arrayOf(Int::class.javaPrimitiveType!!),
            "setTabBackgroundResource" to arrayOf(Int::class.javaPrimitiveType!!),
            "setPanelBackgroundColor" to arrayOf(Int::class.javaPrimitiveType!!),
            "setEmojiPanelBackground" to arrayOf(Int::class.javaPrimitiveType!!),
            "setShowClose" to arrayOf(Boolean::class.javaPrimitiveType!!)).forEach { (name, types) ->
            KavaReflector.findMethodRecursive(emojiClass, name, *types)?.let { method ->
                hookOnce(method, after = { param ->
                    val panel = param.thisObject as? View ?: return@hookOnce
                    if (!hasClassName(panel, "com.tencent.mm.view.SmileyPanelImpl")) return@hookOnce
                    applyEmojiPanelSurfaces(panel)
                })
            }
        }

        val layoutClass = KavaReflector.loadClass("com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout", context.hostClassLoader())
        findWechatVerifiedMethod(layoutClass, "dispatchDraw", Canvas::class.java)?.let { method ->
            ownedHooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (destroyed) return
                    val view = param.thisObject as? View ?: return
                    val activity = findActivity(view) ?: return
                    val owner = states[activity] ?: return
                    if (owner.lastLoggedSlot != BackgroundBeautySettings.Slot.CHAT ||
                        !prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE)) return
                    // 8.0.78: dispatchDraw paints the navigation inset with q after drawing children.
                    val paint = KavaReflector.readField(view, "q") as? Paint ?: return
                    param.setObjectExtra("Hchat:chatInsetPaint", paint to paint.alpha)
                    paint.alpha = 0
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    @Suppress("UNCHECKED_CAST")
                    val saved = param.getObjectExtra("Hchat:chatInsetPaint") as? Pair<Paint, Int> ?: return
                    saved.first.alpha = saved.second
                }
            })
        }
        val baseConversation = KavaReflector.loadClass(BASE_CONVERSATION_ACTIVITY, context.hostClassLoader())
        findWechatVerifiedMethod(baseConversation, "closeChatting", Boolean::class.javaPrimitiveType!!)?.let { method ->
            hookOnce(method, after = { param ->
                (param.thisObject as? Activity)?.let { activity ->
                    scheduleRefresh(activity, 1)

                }
            })
        }
        val folderClass = KavaReflector.loadClass("com.tencent.mm.ui.conversation.ConversationFolderItemView", context.hostClassLoader())
        KavaReflector.findMethodRecursive(folderClass, "draw", Canvas::class.java)?.let { method ->
            hookOnce(method, before = { param ->
                if (destroyed) return@hookOnce
                val view = param.thisObject as? View ?: return@hookOnce
                if (view.javaClass.name != "com.tencent.mm.ui.conversation.ConversationFolderItemView") return@hookOnce
                val activity = findActivity(view) ?: return@hookOnce
                if (!wechatHomepageBackdropEnabled(activity) || !isOpenDesktopBottomClip(view)) return@hookOnce
                // TaskBarContainer reserves 52 px; the next conversation row remains clipped at the bottom.
                // Skip that row's entire drawing (including text/foreground), without changing layout or touch.
                param.result = null

            })
        }
        // Reapply overlap after every native scroll layout; this is the verified dd6383f path.
        val scrollClass = KavaReflector.loadClass(ChatInputMessageUnderlay.SCROLL, context.hostClassLoader())
        findWechatVerifiedMethod(scrollClass, "onLayout", Boolean::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)?.let { method ->
            hookOnce(method, before = { param ->
                val scroll = param.thisObject as? ViewGroup ?: return@hookOnce
                val activity = findActivity(scroll) ?: return@hookOnce
                t.mma.utils.ChatScrollTrace.watch(scroll)
                t.mma.utils.ChatScrollDiagnostic.hit("scroll.nativeLayout")
                states[activity]?.inputUnderlay?.beforeLayout(scroll)
                t.mma.utils.ChatScrollTrace.nativeBegin()
            }, after = { param ->
                t.mma.utils.ChatScrollTrace.nativeEnd()
                val scroll = param.thisObject as? ViewGroup ?: return@hookOnce
                val activity = findActivity(scroll) ?: return@hookOnce
                val state = states[activity] ?: return@hookOnce
                try {
                    // The verified scroll instance is already a chat-specific anchor.
                    val enabled = !destroyed && scroll.isShown &&
                        prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE) &&
                        BackgroundBeautyStore.hasImage(context.hostContext(), BackgroundBeautySettings.Slot.CHAT) &&
                        chatChromeVersionName() == "8.0.78"
                    if (enabled && !floatingFooterEnabled()) state.inputUnderlay.apply(scroll) else state.inputUnderlay.restore(scroll)
                } catch (e: Throwable) {
                    state.inputUnderlay.restore(scroll)
                    HLog.e("$TAG 输入栏消息穿透布局失败", e)
                }
            })
        }
        listOf("com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout", "com.tencent.mm.pluginsdk.ui.chat.ChatFooter").forEach { className ->
            val clazz = KavaReflector.loadClass(className, context.hostClassLoader()) ?: return@forEach
            val apply: (XC_MethodHook.MethodHookParam) -> Unit = { param ->
                val view = param.thisObject as? View
                if (view != null && hasClassName(view, className)) {
                    val activity = findActivity(view)
                    val state = activity?.let { states[it] ?: ensureState(it) }
                    if (activity != null && (state == null || state.lastLoggedSlot != BackgroundBeautySettings.Slot.CHAT)) scheduleRefresh(activity)
                    if (activity != null && state != null && !destroyed &&
                        resolveTarget(activity) == BackgroundBeautySettings.Slot.CHAT && hasConfiguredBackground(activity)) {
                        // Prepare the editor outline synchronously after native layout,
                        // before its first draw. Full root transparency remains in refresh.
                        applyChatFooterTransparency(state, view)
                    }
                }
            }
            KavaReflector.findMethodRecursive(clazz, "onAttachedToWindow")?.let { method -> hookOnce(method, after = apply) }
            KavaReflector.findMethodRecursive(clazz, "onLayout", Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)?.let { method -> hookOnce(method, after = apply) }
        }
    }
    private fun findAncestor(start: View, className: String): View? {
        var current: View? = start
        while (current != null) {
            if (current.javaClass.name == className) return current
            current = current.parent as? View
        }
        return null
    }
    private fun findVisibleDescendant(root: View, className: String): View? {
        val queue = ArrayDeque<View>()
        queue.add(root)
        var best: View? = null
        var bestArea = 0
        while (queue.isNotEmpty()) {
            val view = queue.removeFirst()
            if (view.javaClass.name == className && view.isAttachedToWindow && view.isShown) {
                val rect = Rect()
                val area = if (view.getGlobalVisibleRect(rect)) rect.width() * rect.height() else 0
                if (area > bestArea) { best = view; bestArea = area }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) queue.add(view.getChildAt(i))
        }
        return best
    }
    // APK versionName is stable for this runtime. current() rebuilds the full
    // patch fingerprint, so keep it out of per-layout compatibility checks.
    // Missing startup values are retried; no public version API policy changes.
    private var chatChromeVersionName: String? = null
    private fun chatChromeVersionName(): String? {
        chatChromeVersionName?.let { return it }
        return WeChatApis.version()?.versionName()?.takeIf { it.isNotBlank() }?.also {
            chatChromeVersionName = it
        }
    }
    private fun floatingFooterEnabled(): Boolean =
        context.configStore().getGlobalBoolean("floating_chat_footer_enabled", false)
    private fun applyChatChromeRoot(state: HostState, root: View) {
        val layout = when {
            root.javaClass.name == "com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout" -> root
            root.javaClass.name == "com.tencent.mm.pluginsdk.ui.chat.ChatFooter" ->
                findAncestor(root, "com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout")
            else -> findVisibleDescendant(root, "com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout")
        }
        if (layout != null) {
            setTransparentSurface(state, layout, BackgroundBeautySettings.Slot.CHAT, clearForeground = true)
            val chatHost = chatBackgroundHost(state.activity)
            var parent = layout.parent as? View
            while (parent != null && parent !== chatHost) {
                setTransparentSurface(state, parent, BackgroundBeautySettings.Slot.CHAT, clearForeground = true)
                parent = parent.parent as? View
            }
        }
        applyChatNoticeSurfaces(state, layout ?: root)
        applySpeechSurfaces(state, layout ?: root)
        applyChatVoiceSurfaces(state, layout ?: root)
        applyChatFooterTransparency(state, layout ?: root)
        if (chatChromeVersionName() == "8.0.78") {
            val scroll = findVisibleDescendant(layout ?: root, ChatInputMessageUnderlay.SCROLL) as? ViewGroup
            if (scroll != null) runCatching {
                if (floatingFooterEnabled()) state.inputUnderlay.restore(scroll) else state.inputUnderlay.apply(scroll)
            }
                .onFailure { state.inputUnderlay.restore(scroll); HLog.e("$TAG 输入栏消息穿透布局失败", it) }
        }
    }
    private fun applyChatFooterTransparency(state: HostState, root: View) {
        val traceStarted = t.mma.utils.ChatScrollDiagnostic.begin()
        try {
        val footer = when {
            root.javaClass.name == "com.tencent.mm.pluginsdk.ui.chat.ChatFooter" -> root
            else -> findVisibleDescendant(root, "com.tencent.mm.pluginsdk.ui.chat.ChatFooter")
        } as? ViewGroup
        if (footer == null) {
            return
        }
        // The complete ChatFooter contains both the input surface and the sibling plus-menu
        // panel. Both are intended to reveal the chat background, so process the whole footer.
        applySpeechSurfaces(state, footer)
        applyChatVoiceSurfaces(state, footer)
        val queue = ArrayDeque<View>()
        queue.add(footer)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < 240) {
            val view = queue.removeFirst()
            // Keep the bkc/AppPanel branch in this traversal: its surface is intentionally transparent.

            if (view is android.widget.EditText) {
                if (floatingFooterEnabled()) {
                    // Floating input has a single translucent card, without an editor border.
                    setTransparentSurface(state, view, BackgroundBeautySettings.Slot.CHAT, clearForeground = true)
                    inputOutlineStyles.remove(view)
                    continue
                }
                val snapshot = surfaceSnapshot(state, view)
                val dark = isWechatDark(view)
                val strokeColor = if (dark) Color.WHITE else Color.BLACK
                val strokeWidth = chatInputStrokeWidth(footer)
                val icon = footer.findViewById<ImageView>(resourceId("bqr"))
                val ink = icon?.let { measuredIconStrokes[it] }
                val style = listOf(strokeColor, strokeWidth, icon, icon?.drawable, ink?.top, ink?.bottom)
                if (snapshot.slot == BackgroundBeautySettings.Slot.CHAT && inputOutlineStyles[view] == style &&
                    snapshot.appliedBackground is ChatInputOutline && view.background === snapshot.appliedBackground) continue
                synchronizeExternalBackground(snapshot, view)
                val density = view.resources.displayMetrics.density
                val outline = ChatInputOutline(view, icon, ink?.top ?: 0, ink?.bottom ?: 0,
                    strokeWidth.toFloat(), strokeColor, density * 10f)
                surfaceMutationDepth++
                try {
                    view.backgroundTintList = null
                    view.backgroundTintMode = null
                    view.background = outline
                    restoreSurfaceGeometry(view, snapshot)
                    snapshot.appliedBackground = outline
                    snapshot.appliedBitmap = null
                    snapshot.slot = BackgroundBeautySettings.Slot.CHAT
                    inputOutlineStyles[view] = style

                } finally { surfaceMutationDepth-- }
            } else if (view is ViewGroup) {
                setTransparentSurface(state, view, BackgroundBeautySettings.Slot.CHAT, clearForeground = true, preserveFeedback = appGridItems[view] == true)
                for (i in 0 until view.childCount) queue.add(view.getChildAt(i))
            } else if (view.javaClass == View::class.java && findAncestor(view, "com.tencent.mm.pluginsdk.ui.SpeechInputLayout") != null) {
                // ew_.xml: SpeechInputLayout.m and .o are independent solid View backgrounds.
                setTransparentSurface(state, view, BackgroundBeautySettings.Slot.CHAT, clearForeground = true)
            } else if (view is ImageView) {
                // Footer icons retain their image drawable. Only their independent button surface is cleared.
                val inSpeech = findAncestor(view, "com.tencent.mm.pluginsdk.ui.SpeechInputLayout") != null
                setTransparentSurface(state, view, BackgroundBeautySettings.Slot.CHAT, clearForeground = true, preserveFeedback = !inSpeech)
            }
        }

        } finally { t.mma.utils.ChatScrollDiagnostic.end("background.footer", traceStarted) }
    }
    private fun applyWhitelistedSurfaces(
        state: HostState,
        root: View,
        slot: BackgroundBeautySettings.Slot
    ) {
        val targetIds = targetResourceIds(slot)
        val chatHost = if (slot == BackgroundBeautySettings.Slot.CHAT) chatBackgroundHost(state.activity) else null
        val queue = ArrayDeque<View>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_SCAN_NODES) {
            val view = queue.removeFirst()
            val name = view.javaClass.name
            if (slot == BackgroundBeautySettings.Slot.CHAT &&
                name == "com.tencent.mm.pluginsdk.ui.chat.ChatFooter") continue
            if (view !== root && isBottomBar(view)) continue
            if (view !== root && isComposeSurface(view)) continue
            if (isWechatTaskSurface(view)) {
                setTransparentSurface(state, view, slot)
                continue
            }
            if (name == "com.tencent.mm.ui.FrostedContentView" || name == APP_BRAND_DESKTOP_CONTAINER ||
                hasClassName(view, BOUNCE_VIEW) || hasClassName(view, NESTED_BOUNCE_VIEW) ||
                name == CHAT_FOOTER_CUSTOM ||
                (view !== root && isMajorSurface(view))
            ) {
                if (!hasClassName(view, CHATTING_IMAGE_BG_VIEW) &&
                    view !== chatHost) {
                    setTransparentSurface(state, view, slot)
                }
            }
            if (isHomeContentList(view, slot)) {
                homeLists[view] = slot

                installRecyclerBinding(view)
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) {
                        val child = view.getChildAt(index)
                        applyHomeRow(state, child, slot)
                    }
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
            if (view.id in targetIds && !(slot == BackgroundBeautySettings.Slot.CHAT && view === root)) {
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
        val chatHost = if (slot == BackgroundBeautySettings.Slot.CHAT) chatBackgroundHost(state.activity) ?: return else null
        val cached = state.surfaces.keys.filter { view ->
            view !== host && view.isAttachedToWindow && isChromeSurface(view) &&
                (chatHost == null || isDescendantOf(view, chatHost))
        }
        cached.forEach { view -> setTransparentSurface(state, view, slot) }
        val decor = chatHost ?: state.activity.window.decorView
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
                setTransparentSurface(state, view, slot, clearForeground = true)
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

    private fun isOpenDesktopBottomClip(view: View): Boolean {
        if (view.javaClass.name != "com.tencent.mm.ui.conversation.ConversationFolderItemView") return false
        val activity = findActivity(view) ?: return false
        val rect = Rect()
        if (!view.getGlobalVisibleRect(rect) || rect.bottom < activity.window.decorView.height - 2) return false
        val controller = wechatTaskControllers.entries.firstOrNull { findActivity(it.key) === activity }?.value?.get() ?: return false
        return readNativeField(controller, "P") as? Boolean == true && rect.top > activity.window.decorView.height * 0.94f
    }
    private fun isWechatTaskSurface(view: View): Boolean =
        view.javaClass.name == "com.tencent.mm.plugin.taskbar.ui.TaskBarContainer" ||
            view.javaClass.name == APP_BRAND_DESKTOP_CONTAINER
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

    private val wechatCoveragePulls = WeakHashMap<Activity, Boolean>()

    private val wechatTouchStart = WeakHashMap<Activity, Float>()

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
            if (slot == BackgroundBeautySettings.Slot.CONTACTS) captureContactGeometry(view, snapshot)
            if (snapshot.slot == slot &&
                snapshot.appliedBitmap === bitmap &&
                snapshot.appliedOpacity == opacity &&
                view.background === snapshot.appliedBackground &&
                (view !== state.activity.window.decorView || decorBackgroundApplied(view, snapshot.appliedBackground))
            ) return

            val image = ScreenAlignedBitmapDrawable(bitmap, view, opacity,
                resolveThemeBackground(view.context) or (0xff shl 24),
                followHorizontalMotion = slot == BackgroundBeautySettings.Slot.CHAT)
            val applied = image
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
    private val inputOutlineStyles = WeakHashMap<View, List<Any?>>()
    private fun applyNativeChrome(view: View, slot: BackgroundBeautySettings.Slot) {
        if (restoringBounce || initializedChrome[view] == true) return
        // ActionBarContainer draws primary/stacked/split layers independently of View.background.
        if (slot == BackgroundBeautySettings.Slot.CHAT && view.javaClass.name != "androidx.appcompat.widget.ActionBarContainer") return
        if (hasClassName(view, BOUNCE_VIEW) || hasClassName(view, NESTED_BOUNCE_VIEW) ||
            view.javaClass.name == "com.tencent.mm.ui.FrostedContentView" || view.javaClass.name == "androidx.appcompat.widget.ActionBarContainer") initializedChrome[view] = true
        if (hasClassName(view, BOUNCE_VIEW) || hasClassName(view, NESTED_BOUNCE_VIEW)) {
            val activity = findActivity(view)
            val state = activity?.let { states[it] }
            val backdrop = KavaReflector.readField(view, "G") as? View
            if (state != null && backdrop != null) setTransparentSurface(state, backdrop, slot)

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
        if (t.mma.hooks.items.homeextension.HomeExtensionSurface.contains(view)) return
        // The floating card owns its translucent surface; still clear native descendants.
        if (t.mma.hooks.items.floatingchat.FloatingChatCardVisuals.contains(view)) return
        if (slot == BackgroundBeautySettings.Slot.CONTACTS && isContactIconSurface(view)) {
            // A pre-layout pass may have treated an unmeasured icon as a surface.
            state.surfaces[view]?.let { snapshot ->
                if (snapshot.appliedForeground != null && view.foreground === snapshot.appliedForeground) {
                    surfaceMutationDepth++
                    try { view.foreground = snapshot.originalForeground; snapshot.appliedForeground = null }
                    finally { surfaceMutationDepth-- }
                }
                if (snapshot.appliedBackground != null && view.background === snapshot.appliedBackground) {
                    captureContactGeometry(view, snapshot)
                    surfaceMutationDepth++
                    try {
                        view.background = snapshot.originalBackground
                        view.backgroundTintList = snapshot.originalTintList
                        view.backgroundTintMode = snapshot.originalTintMode
                        restoreSurfaceGeometry(view, snapshot)
                        snapshot.appliedBackground = null
                    } finally { surfaceMutationDepth-- }
                }
            }
            return
        }
        // AppPanel containers cover the entire page. Only an AppGrid item root
        // owns feedback; propagated pressed states must not animate ancestors.
        var inAppPanel = false
        if (slot == BackgroundBeautySettings.Slot.CHAT && preserveFeedback) {
            var itemAncestor: View? = view
            while (itemAncestor != null && appGridItems[itemAncestor] != true) {
                itemAncestor = itemAncestor.parent as? View
            }
            inAppPanel = itemAncestor != null ||
                findAncestor(view, "com.tencent.mm.pluginsdk.ui.chat.AppPanel") != null
        }
        // Footer hosts span input + offscreen panel. A propagated pressed state must
        // never paint a full-width rectangle; actual app-grid items keep feedback.
        val footerHost = slot == BackgroundBeautySettings.Slot.CHAT && view is ViewGroup &&
            appGridItems[view] != true &&
            findAncestor(view, "com.tencent.mm.pluginsdk.ui.chat.ChatFooter") != null
        val feedback = preserveFeedback && !footerHost && (slot != BackgroundBeautySettings.Slot.CHAT ||
            !inAppPanel || appGridItems[view] == true)
        if (slot == BackgroundBeautySettings.Slot.CHAT && state.surfaces[view]?.let {
                it.slot == slot && it.appliedBitmap != null && view.background === it.appliedBackground
            } == true) return
        applyNativeChrome(view, slot)
        surfaceMutationDepth++
        try {
            val snapshot = surfaceSnapshot(state, view)
            // Capture before synchronization clears the previous marker on native replacement.
            val writeReason = if (!t.mma.utils.ChatScrollDiagnostic.isActive()) 0 else when {
                snapshot.appliedBackground != null && view.background !== snapshot.appliedBackground -> 1
                snapshot.appliedBackground == null -> 2
                snapshot.slot != slot -> 3
                snapshot.appliedBitmap != null -> 4
                else -> 5
            }
            val diagnosticPreviousSlot = if (writeReason != 0) snapshot.slot?.name else null
            synchronizeExternalBackground(snapshot, view)
            if (slot == BackgroundBeautySettings.Slot.CONTACTS) captureContactGeometry(view, snapshot)
            val backgroundMatches = snapshot.slot == slot &&
                snapshot.appliedBitmap == null &&
                snapshot.appliedBackground != null &&
                view.background === snapshot.appliedBackground &&
                (if (feedback) snapshot.appliedBackground is BackgroundBeautyFeedbackDrawable else snapshot.appliedBackground !is BackgroundBeautyFeedbackDrawable)
            // Foreground replacement must not reinstall an already correct background.
            // Keep native background changes and feedback-mode changes on the full path.
            if (!backgroundMatches) {
                val marker: Drawable = if (feedback) {
                    BackgroundBeautyFeedbackDrawable(manualOnly = slot == BackgroundBeautySettings.Slot.CONTACTS || slot == BackgroundBeautySettings.Slot.ME)
                } else BackgroundBeautyTransparentDrawable()
                view.backgroundTintList = null
                view.backgroundTintMode = null
                view.background = marker
                if (writeReason != 0) t.mma.utils.ChatScrollDiagnostic.backgroundWrite(writeReason, view.javaClass.name, view, diagnosticPreviousSlot, slot.name)
                marker.state = view.drawableState
                restoreSurfaceGeometry(view, snapshot)
                snapshot.appliedBackground = marker
                snapshot.appliedBitmap = null
                snapshot.appliedOpacity = null
            }
            if (clearForeground && (!backgroundMatches || view.foreground !== snapshot.appliedForeground)) {
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
        // The chat container owns the only screen-aligned bitmap. Keep the native layer present but empty,
        // so its smaller CENTER_CROP viewport cannot redraw a differently scaled copy.
        surfaceMutationDepth++
        try {
            view.imageTintList = null
            view.setImageDrawable(BackgroundBeautyTransparentDrawable())
        } finally { surfaceMutationDepth-- }
        setTransparentSurface(state, view, BackgroundBeautySettings.Slot.CHAT, clearForeground = true)
        snapshot.appliedDrawable = view.drawable
        snapshot.appliedBitmap = bitmap
        snapshot.appliedOpacity = opacity
        snapshot.slot = BackgroundBeautySettings.Slot.CHAT
    }

    private fun restoreSlot(state: HostState, slot: BackgroundBeautySettings.Slot) {
        if (slot == BackgroundBeautySettings.Slot.CHAT) state.inputUnderlay.restoreAll()
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
            if (!t.mma.hooks.items.floatingchat.FloatingChatCardVisuals.contains(view) &&
                appliedBackground != null && view.background === appliedBackground) {
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
        if (view.paddingLeft != snapshot.originalPaddingLeft ||
            view.paddingTop != snapshot.originalPaddingTop ||
            view.paddingRight != snapshot.originalPaddingRight ||
            view.paddingBottom != snapshot.originalPaddingBottom) {
            view.setPadding(snapshot.originalPaddingLeft, snapshot.originalPaddingTop,
                snapshot.originalPaddingRight, snapshot.originalPaddingBottom)
        }
        if (view.minimumWidth != snapshot.originalMinimumWidth) view.minimumWidth = snapshot.originalMinimumWidth
        if (view.minimumHeight != snapshot.originalMinimumHeight) view.minimumHeight = snapshot.originalMinimumHeight
    }
    private fun applyWechatStatusMasks(activity: Activity, phase: String) {
        if (destroyed || activity.javaClass.name != LAUNCHER_ACTIVITY ||
            !prefs.getBoolean(BackgroundBeautySettings.KEY_ENABLE, BackgroundBeautySettings.DEFAULT_ENABLE) ||
            !BackgroundBeautyStore.hasImage(context.hostContext(), BackgroundBeautySettings.Slot.WECHAT)) return
        val state = states[activity] ?: return
        // og9 belongs to MainUI's controller; nhm is MainUIView's own inset mask.
        // Find from decor rather than the fragment root or the current tab's scan.
        listOf("og9", "nhm").forEach { name ->
            val id = resourceId(name)
            if (id == 0) return@forEach
            val mask = activity.window.decorView.findViewById<View>(id) ?: return@forEach
            setTransparentSurface(state, mask, BackgroundBeautySettings.Slot.WECHAT, clearForeground = true)

        }
    }
    private fun applySystemBars(state: HostState) {
        applyWechatStatusMasks(state.activity, "apply-bars")
        val window = state.activity.window
        surfaceMutationDepth++
        try {
        // BaseConversationUI reparents its native action bar/content into a
        // FitSystemWindowLayoutView on first chat. Keep its original inset policy
        // throughout chat/return rather than toggling full-window layout flags.
        if (!isConversationActivity(state.activity)) {
            val desired = window.decorView.systemUiVisibility or IMMERSIVE_FLAGS
            if (window.decorView.systemUiVisibility != desired) window.decorView.systemUiVisibility = desired
        }
        if (window.statusBarColor != Color.TRANSPARENT) window.statusBarColor = Color.TRANSPARENT
        if (window.navigationBarColor != Color.TRANSPARENT) window.navigationBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            if (window.navigationBarDividerColor != Color.TRANSPARENT) window.navigationBarDividerColor = Color.TRANSPARENT
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            if (window.isNavigationBarContrastEnforced) window.isNavigationBarContrastEnforced = false
        }
        state.systemBarsApplied = true
        } finally { surfaceMutationDepth-- }

    }

    private fun restoreSystemBars(state: HostState) {
        if (!state.systemBarsApplied) return
        state.systemBarsApplied = false
        val window = state.activity.window
        if (!isConversationActivity(state.activity)) {
            window.decorView.systemUiVisibility =
                (window.decorView.systemUiVisibility and IMMERSIVE_FLAGS.inv()) or
                (state.originalSystemUiVisibility and IMMERSIVE_FLAGS)
        }
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
        framePageSlots.remove(activity)
        synchronized(observedActivities) { observedActivities.remove(activity) }
        synchronized(nativeChatImages) { nativeChatImages.remove(activity) }
        synchronized(launcherTabIndices) { launcherTabIndices.remove(activity) }
        clearActivity(activity)
    }

    private fun restoreState(state: HostState) {
        state.inputUnderlay.restoreAll()
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

    private fun hasRunningChatAnimation(view: View): Boolean {
        var node: View? = view
        while (node != null) {
            val animation = node.animation
            if (animation != null && !animation.hasEnded()) return true
            if (node.javaClass.name == CHAT_CONTAINER) break
            node = node.parent as? View
        }
        return false
    }

    private fun isConversationActivity(activity: Activity): Boolean {
        var type: Class<*>? = activity.javaClass
        while (type != null) {
            if (type.name == BASE_CONVERSATION_ACTIVITY) return true
            type = type.superclass
        }
        return false
    }
    private fun chatMovingContent(host: View): View {
        val swipe = findDescendant(host, "com.tencent.mm.ui.widget.SwipeBackLayout") ?: return host
        val getter = KavaReflector.findMethodRecursive(swipe.javaClass, "getTargetContentView") ?: return host
        val target = KavaReflector.invoke(getter, swipe) as? View ?: return host
        if (target === host || !isDescendantOf(target, host)) return host

        return target
    }
    private fun chatBackgroundHost(activity: Activity): View? {
        val decor = activity.window.decorView
        val known = if (isConversationActivity(activity)) KavaReflector.readField(activity, "chattingView") as? View else null
        if (known != null && known.isAttachedToWindow && known.isShown) return known
        val container = findVisibleDescendant(decor, CHAT_CONTAINER)
        if (container != null) return container
        if (hasClassName(activity, CHAT_ACTIVITY)) return decor
        return null
    }
    private fun resolveTarget(activity: Activity): BackgroundBeautySettings.Slot? {
        val name = activity.javaClass.name
        val slot = when {
            hasClassName(activity, CHAT_ACTIVITY) -> BackgroundBeautySettings.Slot.CHAT
            (name == LAUNCHER_ACTIVITY || isConversationActivity(activity)) &&
                chatBackgroundHost(activity) != null -> BackgroundBeautySettings.Slot.CHAT
            name == LAUNCHER_ACTIVITY -> currentLauncherSlot(activity)
            else -> null
        }
        if (slot == null) framePageSlots.remove(activity) else framePageSlots[activity] = slot
        return slot
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

    private val resourceIds = HashMap<String, Int>()
    private fun resourceId(name: String): Int = resourceIds.getOrPut(name) {
        val host = context.hostContext()
        host.resources.getIdentifier(name, "id", host.packageName)
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

    private fun findActivity(owner: Any?): Activity? {
        if (owner is Activity) return owner
        if (owner is View) {
            surfaceOwners[owner]?.activity?.let { return it }
            var current: Context? = owner.context
            while (current != null) {
                if (current is Activity) return current
                val base = (current as? ContextWrapper)?.baseContext ?: break
                if (base === current) break
                current = base
            }
            states.keys.firstOrNull { it.window.decorView === owner }?.let { return it }
        }
        return findActivity(owner, Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()))
    }

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
