package t.mma.hooks.items.floatingchat
import t.mma.utils.HLog

import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import t.mma.dexkit.DexMethodCache
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.utils.KavaReflector
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.roundToInt

class FloatingChatFooterFeature : BaseFeature() {
    private var runtime: FooterRuntime? = null
    // Install lifecycle hooks even while disabled; runtime checks gate mutations.
    override fun isEnabled(context: FeatureContext): Boolean = true
    override fun onConfigChanged(context: FeatureContext, key: String?) {
        android.os.Handler(android.os.Looper.getMainLooper()).post { runtime?.refreshVisible() }
    }
    override fun featureId(): String = ID
    override fun name(): String = "悬浮输入框"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(FloatingChatFooterSettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        runtime = FooterRuntime(context)
        DexInstallScheduler.schedule(ID, name()) { runtime?.install() == true }
    }
    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }
    companion object { const val ID = "floating_chat_footer" }
}
private class FooterRuntime(private val context: FeatureContext) {
    private val livePrefs = t.mma.preferences.HchatStorage.preferences(context.hostContext(), "Hchat_global_config")
    private var backgroundTransparency = livePrefs.getInt("floating_chat_footer_transparency", -1)
    private val configHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val liveListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "floating_chat_footer_enabled" || key == "floating_chat_footer_transparency") configHandler.post {
            backgroundTransparency = livePrefs.getInt("floating_chat_footer_transparency", -1)
            refreshVisible()
        }
    }
    init { livePrefs.registerOnSharedPreferenceChangeListener(liveListener) }
    private val hooked = HashSet<Member>()
    private val panelOriginalHeights = WeakHashMap<View, Int>()
    private val panelBodyOriginalHeights = WeakHashMap<View, Int>()
    private val states = WeakHashMap<View, FooterState>()
    private val listOwners = WeakHashMap<View, View>()
    private val newMessageMargins = WeakHashMap<View, Int>()
    private val preDrawListeners = WeakHashMap<View, ViewTreeObserver.OnPreDrawListener>()
    private val footerTrackers = WeakHashMap<View, FooterTracker>()
    private val methodPrefs = DexMethodCache.prefs(
        context.hostContext(),
        "Hchat_floating_chat_footer_method_cache"
    )
    private var structureFailureReported = false
    private val panelStates = WeakHashMap<View, Int>()
    private fun panelState(footer: View): Int = panelStates[footer] ?: PANEL_STATE_NONE
    private var switchPanelMethod: Method? = null
    private var installed = false

    fun refreshVisible() {
        footerTrackers.keys.toList().forEach { if (isEnabled()) requestFooterApply(it) else disposeFooter(it) }
        val root = t.mma.hooks.api.core.WeChatApis.currentActivity()?.currentActivity()?.window?.decorView ?: return
        fun visit(view: View) {
            if (view.javaClass.name == FOOTER_CLASS) { if (isEnabled()) trackFooter(view) }
            else if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
    }
    @Synchronized
    fun install(): Boolean {
        if (installed) return true
        val footerClass = KavaReflector.loadClass(FOOTER_CLASS, context.hostClassLoader())
        if (footerClass == null) {
            HLog.e("[Hchat:$TAG] 功能未生效: 未找到目标类 $FOOTER_CLASS")
            return false
        }
        if (!View::class.java.isAssignableFrom(footerClass)) {
            HLog.e("[Hchat:$TAG] 功能未生效: 目标类不是 View: $FOOTER_CLASS")
            return false
        }

        // Concrete 8.0.78 override: update padding before native list layout.
        KavaReflector.loadClass(CHATTING_RECYCLER_CLASS, context.hostClassLoader())?.let { listClass ->
            KavaReflector.findDeclaredMethod(listClass, "onLayout", Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)?.let { method ->
                HookRegistry.get().hook(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val list = param.thisObject as? View ?: return
                        val footer = listOwners[list] ?: return
                        val state = states[footer] ?: return
                        if (isEnabled()) applyChatListPadding(footer, state.listExtra, state, inListLayout = true)
                    }
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val list = param.thisObject as? View ?: return
                        val footer = listOwners[list] ?: return
                        states[footer]?.bottomInset?.afterLayout(list)
                    }
                })
            }
        }
        var count = 0
        // 8.0.78 ChatFooter.u0(int, boolean): false uses w()+settleInputModeSwitchViews,
        // avoiding the native G/p crossfade exposed by our transparent button surfaces.
        val verifiedInputSwitch = runCatching {
            val host = context.hostContext()
            host.packageManager.getPackageInfo(host.packageName, 0).versionName == "8.0.78"
        }.getOrDefault(false)
        if (verifiedInputSwitch) {
            installSendAnimationSettlement(footerClass)
            KavaReflector.findDeclaredMethod(footerClass, "u0", Int::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!)?.takeIf { it.returnType == Void.TYPE }?.let { method ->
                HookRegistry.get().hook(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val footer = param.thisObject as? View ?: return
                        if (!isEnabled() || states[footer]?.reparented != true) return
                        val mode = param.args[0] as? Int ?: return
                        if (mode == 1 || mode == 2) param.args[1] = false
                    }
                })
            }
        }
        KavaReflector.findDeclaredMethod(footerClass, "onAttachedToWindow")?.let { method ->
            count += hookFooter(method, footerClass) { footer -> trackFooter(footer) }
        }
        KavaReflector.findDeclaredMethod(footerClass, "onDetachedFromWindow")?.let { method ->
            count += hookFooter(method, footerClass) { footer -> disposeFooter(footer) }
        }
        KavaReflector.findMethodRecursive(footerClass, "onLayout", Boolean::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)?.let { method ->
            hookFooter(method, footerClass) { footer ->
                if (isEnabled()) {
                    FloatingFooterInputSurfaces.apply(footer)
                    val tracker = footerTrackers[footer]
                    if (tracker?.failed != true) {
                        val layout = findAncestorChatLayout(footer)
                        if (layout != null && isReadyChatLayout(layout)) {
                            val current = tracker ?: trackerForFooter(footer, layout)
                            current.layout = layout
                            if (current.ready) {
                                val state = states[footer] ?: return@hookFooter
                                refreshMeasuredInputHeight(footer, state)
                                // FORCE_LAYOUT can remain set until View.layout returns.
                                applyChatListPadding(footer, state.listExtra, state, inListLayout = true)
                            }
                            scheduleFooterApply(footer, current, requestLayout = false)
                        }
                    }
                }
            }
        }
        locateRefreshBottomHeight(footerClass)?.let { method ->
            count += hookFooter(method, footerClass) { footer -> requestFooterApply(footer) }
        }
        locateSwitchPanel(footerClass)?.let { method ->
            switchPanelMethod = method
            count += hookPanelState(method, footerClass)
        }
        locateEnterKeyboardState(footerClass)?.let { method ->
            count += hookEnterKeyboardState(method, footerClass)
        }
        hookPanelDragIndicator()
        locateScrollContentTo()?.let { method ->
            count += hookScrollContent(method)
        }
        installed = count >= REQUIRED_HOOK_COUNT
        if (installed) configHandler.post { refreshVisible() }
        if (!installed) {
            HLog.e(
                "[Hchat:$TAG] 功能未生效: 入口不完整 count=$count/$REQUIRED_HOOK_COUNT " +
                    "target=$FOOTER_CLASS"
            )
        }
        return installed
    }

    /** 8.0.78 verified callbacks only: never run from layout, scroll or pre-draw. */
    private fun installSendAnimationSettlement(footerClass: Class<*>) {
        val buttonField = KavaReflector.findDeclaredField(footerClass, "n") ?: return
        val attachField = KavaReflector.findDeclaredField(footerClass, "z") ?: return
        val canSendField = KavaReflector.findDeclaredField(footerClass, "l4") ?: return
        val modeField = KavaReflector.findDeclaredField(footerClass, "r2") ?: return
        if (buttonField.type != android.widget.Button::class.java || !android.widget.ImageButton::class.java.isAssignableFrom(attachField.type) || modeField.type != Int::class.javaPrimitiveType || canSendField.type != Boolean::class.javaPrimitiveType) return
        fun settle(footer: View) {
            SendButtonSettlement.schedule(footer) {
                if (!isEnabled() || states[footer]?.reparented != true) return@schedule null
                val canSend = KavaReflector.readField(canSendField, footer) as? Boolean ?: return@schedule null
                if (KavaReflector.readField(modeField, footer) != 1) return@schedule null
                val send = KavaReflector.readField(buttonField, footer) as? View ?: return@schedule null
                val attach = KavaReflector.readField(attachField, footer) as? View ?: return@schedule null
                if ((send.parent as? View)?.isShown != true) return@schedule null
                SendButtonSettlement.settleControls(send, attach, canSend)
            }
        }
        // B() also has a zero-width legacy-animation branch without h0 callbacks.
        for (name in listOf("A", "B")) {
            KavaReflector.findDeclaredMethod(footerClass, name)?.let { method ->
                hookFooter(method, footerClass) { footer ->
                    if (isEnabled() && states[footer]?.reparented == true) settle(footer)
                }
            }
        }
        for (name in listOf("h0", "f0")) {
            val callbackClass = KavaReflector.loadClass("com.tencent.mm.pluginsdk.ui.chat.$name", context.hostClassLoader()) ?: continue
            val ownerField = KavaReflector.findDeclaredField(callbackClass, "e") ?: continue
            if (ownerField.type != footerClass) continue
            for (event in listOf("onAnimationStart", "onAnimationEnd", "onAnimationCancel")) {
                val method = KavaReflector.findDeclaredMethod(callbackClass, event, android.animation.Animator::class.java) ?: continue
                hookFooterSendCallback(method) { callback, animator ->
                    val footer = KavaReflector.readField(ownerField, callback) as? View ?: return@hookFooterSendCallback
                    if (!isEnabled() || states[footer]?.reparented != true || !footer.isAttachedToWindow) return@hookFooterSendCallback
                    if (event == "onAnimationStart") {
                        SendButtonSettlement.started(footer, animator)
                        return@hookFooterSendCallback
                    }
                    SendButtonSettlement.finished(footer, animator)
                    // Native cancel/end callbacks may start a newer animation synchronously.
                    // Coalesce and read current can-send/animation state in the next frame.
                    settle(footer)
                }
            }
        }
    }
    private fun hookFooterSendCallback(method: Method, after: (Any, Any) -> Unit) {
        if (!hooked.add(method)) return
        runCatching {
            HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    runCatching { after(param.thisObject, param.args[0] ?: return) }.onFailure { HLog.e("[Hchat:$TAG] 发送按钮动画收尾失败", it) }
                }
            })
        }.onFailure { hooked.remove(method); HLog.e("[Hchat:$TAG] 发送按钮动画回调安装失败", it) }
    }
    private fun locateRefreshBottomHeight(footerClass: Class<*>): Method? {
        val runtimeKey = DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader())
        DexMethodCache.load(
            methodPrefs,
            runtimeKey,
            context.hostClassLoader(),
            CACHE_REFRESH_BOTTOM_HEIGHT
        )?.takeIf { isRefreshBottomHeight(it, footerClass) }?.let { return it }

        val candidates = runCatching {
            context.dexKitBridge().findMethod(
                FindMethod().apply {
                    matcher(
                        MethodMatcher().apply {
                            usingEqStrings(REFRESH_LOG_TAG, REFRESH_LOG_ANCHOR)
                        }
                    )
                }
            ).mapNotNull { data ->
                runCatching { data.getMethodInstance(context.hostClassLoader()) }.getOrNull()
            }.filter { isRefreshBottomHeight(it, footerClass) }
                .distinctBy { it.toGenericString() }
        }.getOrElse {
            HLog.e("[Hchat:$TAG] 定位底部高度刷新入口失败", it)
            emptyList()
        }

        val method = candidates.singleOrNull()
        if (method != null) {
            DexMethodCache.save(methodPrefs, runtimeKey, CACHE_REFRESH_BOTTOM_HEIGHT, method)

        } else {
            DexMethodCache.clear(methodPrefs, runtimeKey, CACHE_REFRESH_BOTTOM_HEIGHT)
            HLog.e(
                "[Hchat:$TAG] 功能未生效: 底部高度刷新入口候选数量=${candidates.size} " +
                    "methods=${candidates.joinToString { it.toGenericString() }}"
            )
        }
        return method
    }

    private fun locateSwitchPanel(footerClass: Class<*>): Method? {
        val runtimeKey = DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader())
        DexMethodCache.load(
            methodPrefs,
            runtimeKey,
            context.hostClassLoader(),
            CACHE_SWITCH_PANEL
        )?.takeIf { isSwitchPanel(it, footerClass) }?.let { return it }
        val candidates = runCatching {
            context.dexKitBridge().findMethod(
                FindMethod().apply {
                    matcher(MethodMatcher().apply { usingEqStrings(REFRESH_LOG_TAG, SWITCH_LOG_ANCHOR) })
                }
            ).mapNotNull { data ->
                runCatching { data.getMethodInstance(context.hostClassLoader()) }.getOrNull()
            }.filter { isSwitchPanel(it, footerClass) }
                .distinctBy { it.toGenericString() }
        }.getOrElse {
            HLog.e("[Hchat:$TAG] 定位面板状态入口失败", it)
            emptyList()
        }
        val method = candidates.singleOrNull()
        if (method != null) {
            DexMethodCache.save(methodPrefs, runtimeKey, CACHE_SWITCH_PANEL, method)
        } else {
            DexMethodCache.clear(methodPrefs, runtimeKey, CACHE_SWITCH_PANEL)
            HLog.e("[Hchat:$TAG] 功能未生效: 面板状态入口候选数量=${candidates.size}")
        }
        return method
    }

    private fun locateEnterKeyboardState(footerClass: Class<*>): Method? {
        val runtimeKey = DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader())
        DexMethodCache.load(
            methodPrefs,
            runtimeKey,
            context.hostClassLoader(),
            CACHE_ENTER_KEYBOARD_STATE
        )?.takeIf { isEnterKeyboardState(it, footerClass) }?.let { return it }
        val candidates = runCatching {
            context.dexKitBridge().findMethod(
                FindMethod().apply {
                    matcher(MethodMatcher().apply {
                        usingEqStrings(REFRESH_LOG_TAG, ENTER_KEYBOARD_LOG_ANCHOR)
                    })
                }
            ).mapNotNull { data ->
                runCatching { data.getMethodInstance(context.hostClassLoader()) }.getOrNull()
            }.filter { isEnterKeyboardState(it, footerClass) }
                .distinctBy { it.toGenericString() }
        }.getOrElse {
            HLog.e("[Hchat:$TAG] 定位键盘状态入口失败", it)
            emptyList()
        }
        val method = candidates.singleOrNull()
        if (method != null) {
            DexMethodCache.save(methodPrefs, runtimeKey, CACHE_ENTER_KEYBOARD_STATE, method)

        } else {
            DexMethodCache.clear(methodPrefs, runtimeKey, CACHE_ENTER_KEYBOARD_STATE)
            HLog.e("[Hchat:$TAG] 功能未生效: 键盘状态入口候选数量=${candidates.size}")
        }
        return method
    }

    private fun isEnterKeyboardState(method: Method, footerClass: Class<*>): Boolean =
        method.declaringClass == footerClass &&
            !KavaReflector.isStatic(method) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.isEmpty()

    private fun hookEnterKeyboardState(targetMethod: Member, footerClass: Class<*>): Int {
        if (!hooked.add(targetMethod)) return 1
        return runCatching {
            HookRegistry.get().hook(targetMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!isEnabled() || !footerClass.isInstance(param.thisObject)) return
                    val footer = param.thisObject as? View ?: return
                    val panel = states[footer]?.panel ?: find(footer) { it.javaClass.name == FOOTER_BOTTOM_CLASS } ?: return
                    if (!isPanelState(panelState(footer)) || panel.visibility != View.VISIBLE) return
                    val switchMethod = switchPanelMethod ?: return
                    switchMethod.invoke(footer, PANEL_STATE_NONE, true, -1)
                    param.result = null
                }
            })
            1
        }.getOrElse {
            hooked.remove(targetMethod)
            HLog.e("[Hchat:$TAG] 键盘状态 Hook 安装失败", it)
            0
        }
    }

    private fun locateScrollContentTo(): Method? {
        val runtimeKey = DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader())
        DexMethodCache.load(
            methodPrefs,
            runtimeKey,
            context.hostClassLoader(),
            CACHE_SCROLL_CONTENT_TO
        )?.takeIf(::isScrollContentTo)?.let { return it }
        val candidates = runCatching {
            context.dexKitBridge().findMethod(
                FindMethod().apply {
                    matcher(MethodMatcher().apply {
                        usingEqStrings(SCROLL_LOG_TAG, SCROLL_LOG_ANCHOR)
                    })
                }
            ).mapNotNull { data ->
                runCatching { data.getMethodInstance(context.hostClassLoader()) }.getOrNull()
            }.filter(::isScrollContentTo).distinctBy { it.toGenericString() }
        }.getOrElse {
            HLog.e("[Hchat:$TAG] 定位会话滚动入口失败", it)
            emptyList()
        }
        val method = candidates.singleOrNull()
        if (method != null) {
            DexMethodCache.save(methodPrefs, runtimeKey, CACHE_SCROLL_CONTENT_TO, method)

        } else {
            DexMethodCache.clear(methodPrefs, runtimeKey, CACHE_SCROLL_CONTENT_TO)
            HLog.e("[Hchat:$TAG] 功能未生效: 会话滚动入口候选数量=${candidates.size}")
        }
        return method
    }

    private fun isScrollContentTo(method: Method): Boolean =
        method.declaringClass.name == CHATTING_SCROLL_LAYOUT_CLASS &&
            !KavaReflector.isStatic(method) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(
                arrayOf(
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
            )

    private fun hookPanelDragIndicator() {
        // Verified native raw-Y drag controller, independent of chat scrolling.
        val type = KavaReflector.loadClass(
            "com.tencent.mm.view.EmojiPanelSlideIndicatorView", context.hostClassLoader()
        ) ?: return
        val method = KavaReflector.findDeclaredMethod(type, "onTouchEvent", android.view.MotionEvent::class.java)
            ?: return
        if (!hooked.add(method)) return
        runCatching {
            HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!isEnabled()) return
                    var node = param.thisObject as? View ?: return
                    var panel: View? = null
                    while (node.javaClass.name != FOOTER_CLASS) {
                        if (node.javaClass.name == FOOTER_BOTTOM_CLASS) panel = node
                        node = node.parent as? View ?: return
                    }
                    val state = states[node] ?: return
                    if (!state.reparented || state.panel !== panel || state.panel.visibility != View.VISIBLE) return
                    // Consume the entire sequence before native drag, fling or rebound.
                    param.result = true
                }
            })
        }.onFailure {
            hooked.remove(method)
            HLog.e("[Hchat:$TAG] 表情面板拖动指示条 Hook 安装失败", it)
        }
    }

    private fun hookScrollContent(targetMethod: Member): Int {
        if (!hooked.add(targetMethod)) return 1
        return runCatching {
            HookRegistry.get().hook(targetMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!isEnabled()) return
                    val scrollLayout = param.thisObject as? ViewGroup ?: return
                    val footer = (0 until scrollLayout.childCount)
                        .asSequence()
                        .map(scrollLayout::getChildAt)
                        .firstOrNull { it.javaClass.name == FOOTER_CLASS }
                        ?: return
                    val panel = find(footer) { it.javaClass.name == FOOTER_BOTTOM_CLASS } ?: return
                    val imeHeight = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        scrollLayout.rootWindowInsets
                            ?.getInsets(android.view.WindowInsets.Type.ime())?.bottom ?: 0
                    } else 0
                    val nativeTarget = param.args[0]
                    if (panel.visibility == View.VISIBLE) {
                        param.args[0] = imeHeight
                    }
                    ChatBottomTrace.event(footer, "native-scroll-target") {
                        "native=$nativeTarget effective=${param.args[0]} ime=$imeHeight panel=${panel.visibility} " +
                            "panelState=${panelState(footer)} panelH=${panel.height} footerTy=${footer.translationY} " +
                            "reparented=${states[footer]?.reparented} args=${param.args.joinToString()}"
                    }
                }
            })
            1
        }.getOrElse {
            hooked.remove(targetMethod)
            HLog.e("[Hchat:$TAG] 会话滚动 Hook 安装失败", it)
            0
        }
    }

    private fun isSwitchPanel(method: Method, footerClass: Class<*>): Boolean {
        return method.declaringClass == footerClass &&
            !KavaReflector.isStatic(method) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(
                arrayOf(
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
            )
    }

    private fun isRefreshBottomHeight(method: Method, footerClass: Class<*>): Boolean {
        return method.declaringClass == footerClass &&
            !KavaReflector.isStatic(method) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
    }

    private fun hookPanelState(targetMethod: Member, footerClass: Class<*>): Int {
        if (!hooked.add(targetMethod)) return 1
        return runCatching {
            HookRegistry.get().hook(targetMethod, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!isEnabled() || !footerClass.isInstance(param.thisObject)) return
                    val state = param.args.firstOrNull() as? Int ?: return
                    val footer = param.thisObject as? View ?: return
                    panelStates[footer] = state
                    val panel = find(footer) { it.javaClass.name == FOOTER_BOTTOM_CLASS } ?: return
                    if (!isPanelState(state)) panel.visibility = View.GONE
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!isEnabled() || !footerClass.isInstance(param.thisObject)) return
                    val state = param.args.firstOrNull() as? Int ?: return
                    val footer = param.thisObject as? View ?: return
                    val panel = find(footer) { it.javaClass.name == FOOTER_BOTTOM_CLASS } ?: return
                    panelStates[footer] = state
                    panel.visibility = if (isPanelState(state)) View.VISIBLE else View.GONE
                    if (isPanelState(state)) applyCompactPanelHeight(footer, panel)
                    runCatching {
                        if (states[footer]?.reparented == true) apply(footer)
                        requestFooterApply(footer)
                    }
                        .onFailure { HLog.e("[Hchat:$TAG] 面板状态更新失败", it) }
                }
            })
            1
        }.getOrElse {
            hooked.remove(targetMethod)
            HLog.e("[Hchat:$TAG] 面板状态 Hook 安装失败", it)
            0
        }
    }

    private fun hookFooter(
        targetMethod: Member,
        footerClass: Class<*>,
        after: (View) -> Unit
    ): Int {
        if (!hooked.add(targetMethod)) return 1
        return runCatching {
            HookRegistry.get().hook(targetMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val receiver = param.thisObject
                    if (!footerClass.isInstance(receiver)) return
                    val footer = receiver as? View ?: return
                    runCatching { after(footer) }
                        .onFailure { HLog.e("[Hchat:$TAG] 输入区布局更新失败", it) }
                }
            })
            1
        }.getOrElse {
            hooked.remove(targetMethod)
            HLog.e("[Hchat:$TAG] Hook 安装失败: ${targetMethod.name}", it)
            0
        }
    }

    private fun trackFooter(footer: View) {
        if (!isEnabled()) { restore(footer); return }
        val layout = findAncestorChatLayout(footer) ?: return
        val tracker = trackerForFooter(footer, layout)
        if (!isReadyChatLayout(layout)) return
        if (!tracker.ready && !tracker.failed) footer.visibility = View.INVISIBLE
        scheduleFooterApply(footer, tracker)
    }

    private fun requestFooterApply(footer: View) {
        if (!isEnabled()) { restore(footer); return }
        val layout = findAncestorChatLayout(footer) ?: return
        val tracker = trackerForFooter(footer, layout)
        if (!isReadyChatLayout(layout)) return
        scheduleFooterApply(footer, tracker)
    }

    private fun trackerForFooter(footer: View, layout: ViewGroup): FooterTracker {
        val tracker = footerTrackers.getOrPut(footer) {
            FooterTracker(layout, footer.visibility).also { created ->
                val listener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                        requestFooterApply(footer)
                    }
                }
                created.layoutListener = listener
                footer.addOnLayoutChangeListener(listener)
                val observer = footer.viewTreeObserver
                val global = ViewTreeObserver.OnGlobalLayoutListener {
                    if (!created.ready && footer.isAttachedToWindow && isEnabled() && isReadyChatLayout(created.layout)) {
                        scheduleFooterApply(footer, created, requestLayout = false)
                    }
                }
                created.globalObserver = observer
                created.globalListener = global
                observer.addOnGlobalLayoutListener(global)
            }
        }
        tracker.layout = layout
        return tracker
    }

    private fun scheduleFooterApply(footer: View, tracker: FooterTracker, requestLayout: Boolean = false) {
        if (tracker.failed) return
        if (tracker.ready && tracker.settlePasses == 0) {
            val state = states[footer] ?: return
            refreshMeasuredInputHeight(footer, state)
            if (applyChatListPadding(footer, state.listExtra, state)) return
            tracker.settlePasses = 3
        }
        if (tracker.preDraw != null || !footer.isAttachedToWindow) return
        val observer = footer.viewTreeObserver
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (observer.isAlive) observer.removeOnPreDrawListener(this)
                if (tracker.preDraw === this) tracker.preDraw = null
                if (!footer.isAttachedToWindow || !isReadyChatLayout(tracker.layout)) {
                    if (!tracker.ready && !tracker.failed && footer.visibility == View.INVISIBLE) footer.visibility = tracker.visibility
                    return true
                }
                if (!isEnabled()) {
                    footer.visibility = tracker.visibility
                    restore(footer)
                    return true
                }
                // Padding settlement must never reparent or roll back the input hierarchy.
                if (tracker.settlePasses > 0) {
                    val state = states[footer]
                    if (state != null) refreshMeasuredInputHeight(footer, state)
                    val settled = state != null && applyChatListPadding(footer, state.listExtra, state)
                    if (!settled && --tracker.settlePasses > 0) {
                        scheduleFooterApply(footer, tracker)
                        footer.rootView.postInvalidateOnAnimation()
                        return false
                    }
                    tracker.settlePasses = 0
                    footer.visibility = tracker.visibility
                    ChatBottomTrace.event(footer, "footer-settled") { "paddingComplete=$settled ready=${tracker.ready} " +
                            (states[footer]?.chatRecycler?.let(ChatBottomTrace::listGeometry) ?: "list=missing") }
                    return true
                }
                val wasReady = tracker.ready
                val applied = apply(footer)
                ChatBottomTrace.event(footer, if (applied) "footer-ready" else "footer-wait") {
                    "wasReady=$wasReady attempts=${tracker.attempts} panelState=${panelState(footer)}"
                }
                if (applied) {
                    tracker.attempts = 0
                    tracker.failureReported = false
                    tracker.ready = true
                    if (wasReady) footer.visibility = tracker.visibility
                    if (!wasReady) {
                        removeGlobalTracking(tracker)
                    }
                } else if (tracker.attempts++ < INITIAL_LAYOUT_RETRY_COUNT) {
                    // Keep the incomplete entry transaction off screen. A bounded
                    // retry finishes native layout and list padding before first draw.
                    // apply() already requests layout for actual geometry/padding changes.
                    // Requesting it again here keeps isLayoutRequested true on every pass.
                    scheduleFooterApply(footer, tracker, requestLayout = false)
                    footer.rootView.postInvalidateOnAnimation()
                    return false
                } else {
                    // Stop ALL layout/global callbacks from restarting a failed transaction.
                    tracker.failed = true
                    removeGlobalTracking(tracker)
                    // A failed apply may already have moved the native panel. Roll it back.
                    ChatBottomTrace.event(footer, "footer-rollback") { "attempts=${tracker.attempts}" }
                    restore(footer)
                    footer.visibility = tracker.visibility
                    if (!tracker.failureReported) {
                        tracker.failureReported = true
                        HLog.w("[Hchat:$TAG] 初始布局未就绪，已恢复原始输入区和面板，本实例停止重试")
                    }
                }
                // Hierarchy/margins/padding changed: finish the native relayout
                // before revealing the first frame instead of drawing intermediate bounds.
                if (applied && !wasReady) {
                    tracker.settlePasses = 3
                    scheduleFooterApply(footer, tracker)
                    // Returning false alone does not explicitly request the next draw.
                    footer.rootView.postInvalidateOnAnimation()
                    return false
                }
                return true
            }
        }
        tracker.preDraw = listener
        observer.addOnPreDrawListener(listener)
        if (requestLayout) footer.requestLayout()
    }

    private fun removeGlobalTracking(tracker: FooterTracker) {
        tracker.globalListener?.let { if (tracker.globalObserver?.isAlive == true) tracker.globalObserver?.removeOnGlobalLayoutListener(it) }
        tracker.globalListener = null
        tracker.globalObserver = null
    }

    private fun findAncestorChatLayout(view: View): ViewGroup? {
        var current = view.parent as? View
        while (current != null) {
            if (current.javaClass.name == CHATTING_UI_LAYOUT_CLASS && current is ViewGroup) return current
            current = current.parent as? View
        }
        return null
    }

    private fun isReadyChatLayout(layout: View): Boolean {
        // The footer may still be below the viewport while the chat hierarchy is
        // being entered. Waiting for screen intersection caused the first card
        // to appear only after a later global layout. Attachment and measurement
        // are sufficient for the guarded pre-draw reparenting below.
        return layout.isAttachedToWindow && layout.width > 0 && layout.height > 0 &&
            layout.visibility == View.VISIBLE
    }

    private fun disposeFooter(footer: View) {
        footerTrackers.remove(footer)?.let { tracker ->
            removeGlobalTracking(tracker)

            tracker.layoutListener?.let(footer::removeOnLayoutChangeListener)
            tracker.preDraw?.let { listener ->
                runCatching { footer.viewTreeObserver.removeOnPreDrawListener(listener) }
            }
            if (!tracker.ready) footer.visibility = tracker.visibility
        }
        restore(footer)
    }

    private fun apply(footer: View): Boolean {
        if (!isEnabled()) {
            restore(footer)
            return false
        }
        if (!footer.isAttachedToWindow) return false
        val layout = findAncestorChatLayout(footer) ?: return false
        if (!isReadyChatLayout(layout)) return false
        val marginParams = footer.layoutParams as? ViewGroup.MarginLayoutParams ?: return false
        val panel = states[footer]?.panel ?: find(footer) { it.javaClass.name == FOOTER_BOTTOM_CLASS }
        if (panel == null) {
            reportStructureFailureOnce("未找到 $FOOTER_BOTTOM_CLASS")
            return false
        }
        if (states[footer] == null) {
            t.mma.hooks.items.backgroundbeauty.ChatInputMessageUnderlay.releaseForFloatingFooter(footer)
        }
        val state = states[footer] ?: createState(footer, panel, marginParams) ?: return false
        if (isPanelState(panelState(footer))) applyCompactPanelHeight(footer, state.panel)
        val measuredInputHeight = visibleHeightExcludingPanel(state.inputColumn, state.panel)
        val stableInputHeight = measuredInputHeight.takeIf { it > 0 }
            ?: state.baseInputHeight.takeIf { it > 0 }
            ?: footer.height.takeIf { it > 0 }
            ?: return false
        // The toolbar can be inserted after the first native measurement.
        // Refresh the measured input stack instead of freezing its initial height.
        state.baseInputHeight = stableInputHeight
        val inputHeight = stableInputHeight

        // Do not mutate hierarchy until native input height is available.
        if (!state.reparented && !reparentPanel(state)) return false
        val side = dp(SIDE_MARGIN_DP)
        val gap = dp(BOTTOM_GAP_DP)
        // Read the adjusted panel height for input anchoring and list avoidance.
        val panelHeight = if (state.panel.visibility == View.VISIBLE) {
            state.panel.layoutParams?.height?.takeIf { it > 0 }
                ?: state.panel.measuredHeight.coerceAtLeast(0)
        } else 0
        val occupiedHeight = inputHeight + panelHeight
        val targetTop = -(occupiedHeight + gap)
        var layoutChanged = false
        if (marginParams.leftMargin != side) {
            marginParams.leftMargin = side
            layoutChanged = true
        }
        if (marginParams.rightMargin != side) {
            marginParams.rightMargin = side
            layoutChanged = true
        }
        if (marginParams.topMargin != targetTop) {
            marginParams.topMargin = targetTop
            layoutChanged = true
        }
        if (marginParams.bottomMargin != gap) {
            marginParams.bottomMargin = gap
            layoutChanged = true
        }
        if (layoutChanged) {
            footer.layoutParams = marginParams
            footer.requestLayout()
        }

        if (footer.outlineProvider !== FloatingFooterOutlineProvider) footer.outlineProvider = FloatingFooterOutlineProvider
        if (!footer.clipToOutline) footer.clipToOutline = true
        if (footer.elevation != 0f) footer.elevation = 0f
        if (footer.translationZ != 0f) footer.translationZ = 0f
        FloatingChatCardVisuals.apply(footer, CORNER_RADIUS_DP, translucent = true, borderless = true, transparency = backgroundTransparency)
        FloatingFooterInputSurfaces.apply(footer)
        state.listExtra = occupiedHeight + gap
        applyChatListPadding(footer, state.listExtra, state)
        ensurePreDrawTracking(footer)
        // Structural success is independent of pending native RecyclerView layout.
        return true

    }

    private fun createState(
        footer: View,
        panel: View,
        marginParams: ViewGroup.MarginLayoutParams
    ): FooterState? {
        val inputColumn = panel.parent as? ViewGroup
        val root = inputColumn?.parent as? RelativeLayout
        if (inputColumn == null || root == null || root.parent !== footer) {
            reportStructureFailureOnce(
                "布局结构不匹配 panelParent=${panel.parent?.javaClass?.name} " +
                    "root=${inputColumn?.parent?.javaClass?.name}"
            )
            return null
        }
        val panelIndex = inputColumn.indexOfChild(panel)
        val panelParams = panel.layoutParams
        if (panelIndex < 0 || panelParams == null) {
            reportStructureFailureOnce("面板索引或布局参数无效")
            return null
        }
        return FooterState(
            panel = panel,
            inputColumn = inputColumn,
            root = root,
            panelIndex = panelIndex,
            panelLayoutParams = copyLayoutParams(panelParams),
            panelVisibility = panel.visibility,
            leftMargin = marginParams.leftMargin,
            rightMargin = marginParams.rightMargin,
            topMargin = marginParams.topMargin,
            bottomMargin = marginParams.bottomMargin,
            elevation = footer.elevation,
            translationZ = footer.translationZ,
            outlineProvider = footer.outlineProvider,
            clipToOutline = footer.clipToOutline,
            baseInputHeight = (0 until inputColumn.childCount)
                .asSequence()
                .map(inputColumn::getChildAt)
                .filter { it !== panel && it.visibility != View.GONE }
                .sumOf { it.height }
        ).also { states[footer] = it }
    }

    private fun reparentPanel(state: FooterState): Boolean {
        val panel = state.panel
        return runCatching {
            // Keep the native input column above its accessory panel. The footer's
            // negative top margin lifts the complete stack by its occupied height.
            if (state.inputColumn.id == View.NO_ID) state.inputColumn.id = View.generateViewId()
            state.inputColumn.removeView(panel)
            val columnParams = state.inputColumn.layoutParams as? RelativeLayout.LayoutParams
                ?: error("输入列缺少 RelativeLayout 参数")
            state.changedRules[state.inputColumn] = columnParams.rules.copyOf()
            columnParams.removeRule(RelativeLayout.BELOW)
            columnParams.removeRule(RelativeLayout.ABOVE)
            columnParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            state.inputColumn.layoutParams = columnParams
            state.root.addView(
                panel,
                RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    state.panelLayoutParams.height
                ).apply { addRule(RelativeLayout.BELOW, state.inputColumn.id) }
            )
            panel.visibility = if (isPanelState(panelState(state.root.parent as? View ?: return false))) View.VISIBLE else View.GONE
            state.reparented = true

            true
        }.getOrElse { throwable ->
            runCatching {
                state.changedRules.forEach { (view, rules) ->
                    val params = view.layoutParams as? RelativeLayout.LayoutParams ?: return@forEach
                    for (verb in params.rules.indices) params.removeRule(verb)
                    rules.forEachIndexed { verb, anchor ->
                        if (anchor != 0) params.addRule(verb, anchor)
                    }
                    view.layoutParams = params
                }
                state.changedRules.clear()
                (panel.parent as? ViewGroup)?.removeView(panel)
                if (state.inputColumn.indexOfChild(panel) < 0) {
                    state.inputColumn.addView(
                        panel,
                        state.panelIndex.coerceAtMost(state.inputColumn.childCount),
                        copyLayoutParams(state.panelLayoutParams)
                    )
                }
                panel.visibility = state.panelVisibility
            }.onFailure { rollbackError ->
                HLog.e("[Hchat:$TAG] 输入区结构重排回滚失败", rollbackError)
            }
            HLog.e("[Hchat:$TAG] 输入区结构重排失败", throwable)
            false
        }
    }

    private fun applyCompactPanelHeight(footer: View, panel: View) {
        val lp = panel.layoutParams ?: return
        val natural = panelOriginalHeights[panel]
            ?: panel.height.takeIf { it > 0 }
            ?: lp.height.takeIf { it > 0 }
            ?: dp(MAX_PANEL_HEIGHT_DP)
            .also { panelOriginalHeights[panel] = it }
        if (!panelOriginalHeights.containsKey(panel)) panelOriginalHeights[panel] = natural
        val hostHeight = (footer.parent as? View)?.height?.takeIf { it > 0 }
            ?: footer.resources.displayMetrics.heightPixels
        val target = minOf(
            natural,
            dp(MAX_PANEL_HEIGHT_DP),
            (hostHeight * MAX_PANEL_HEIGHT_RATIO).roundToInt()
        ).coerceAtLeast(dp(MIN_PANEL_HEIGHT_DP)) + dp(PANEL_EXTRA_HEIGHT_DP)
        if (lp.height != target) {
            lp.height = target
            panel.layoutParams = lp
        }
        val group = panel as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            val childLp = child.layoutParams ?: continue
            if (childLp.height != natural) continue
            panelBodyOriginalHeights.getOrPut(child) { childLp.height }
            if (childLp.height != target) {
                childLp.height = target
                child.layoutParams = childLp
            }
        }
    }

    private fun restorePanelHeight(panel: View) {
        val group = panel as? ViewGroup
        if (group != null) {
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                val height = panelBodyOriginalHeights.remove(child) ?: continue
                child.layoutParams?.let { lp ->
                    lp.height = height
                    child.layoutParams = lp
                }
            }
        }
        val height = panelOriginalHeights.remove(panel) ?: return
        panel.layoutParams?.let { lp ->
            lp.height = height
            panel.layoutParams = lp
        }
    }

    private fun visibleHeightExcludingPanel(root: ViewGroup, panel: View): Int {
        var total = 0
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child !== panel && child.visibility != View.GONE) {
                total += child.measuredHeight.takeIf { it > 0 }
                    ?: child.height.takeIf { it > 0 }
                    ?: child.layoutParams?.height?.takeIf { it > 0 }
                    ?: 0
            }
        }
        return total
    }

    private fun refreshMeasuredInputHeight(footer: View, state: FooterState) {
        // A late toolbar can grow descendants while footer bounds stay unchanged.
        // Reuse existing state; never reparent or roll back for padding settlement.
        val height = visibleHeightExcludingPanel(state.inputColumn, state.panel)
        val panelHeight = if (state.panel.visibility == View.VISIBLE)
            state.panel.layoutParams?.height?.takeIf { it > 0 } ?: state.panel.measuredHeight else 0
        if (height > 0 && height + panelHeight + dp(BOTTOM_GAP_DP) != state.listExtra) {
            apply(footer)
        }
    }
    private fun applyChatListPadding(footer: View, extra: Int, state: FooterState, inListLayout: Boolean = false): Boolean {
        val recycler = state.chatRecycler?.takeIf { it.isAttachedToWindow }
            ?: findChatRecycler(footer)?.also { state.chatRecycler = it }
            ?: return false
        listOwners[recycler] = footer
        // Parent layout can still move both views after recycler.onLayout.
        // Do not capture a baseline or combine coordinates from different passes.
        var ancestor = recycler.parent as? View
        while (ancestor != null) {
            if (ancestor.isLayoutRequested) return false
            ancestor = ancestor.parent as? View
        }
        if (state.recyclerPaddingBottom == null) state.recyclerPaddingBottom = recycler.paddingBottom
        val base = state.recyclerPaddingBottom ?: return false
        // Native content may already end above the input bar. Reserve only real overlap,
        // never add the whole input/panel height a second time.
        if ((!inListLayout && (recycler.isLayoutRequested || footer.isLayoutRequested)) || recycler.height <= 0) return false
        val listLocation = state.listLocation.also(recycler::getLocationOnScreen)
        val footerLocation = state.footerLocation.also(footer::getLocationOnScreen)
        val overlap = (listLocation[1] + recycler.height - footerLocation[1]).coerceIn(0, extra)
        val target = base + overlap
        ChatBottomTrace.event(footer, "list-padding") {
            "extra=$extra listY=${listLocation[1]} listH=${recycler.height} footerY=${footerLocation[1]} base=$base current=${recycler.paddingBottom} target=$target overlap=$overlap " + ChatBottomTrace.listGeometry(recycler)
        }
        if (recycler.paddingBottom == target) {
            liftNewMessageBubble(footer)
            return true
        }
        state.bottomInset.beforePadding(recycler, target)
        recycler.setPadding(recycler.paddingLeft, recycler.paddingTop, recycler.paddingRight, target)
        liftNewMessageBubble(footer)
        // setPadding schedules native list layout; do not expose the old row bounds.
        return false
    }

    private fun ensurePreDrawTracking(footer: View) {
        if (preDrawListeners[footer] != null) return
        val listener = ViewTreeObserver.OnPreDrawListener {
            if (isEnabled() && footer.isAttachedToWindow) {
                run {
                    // Recheck cached surfaces only; do not scan the tree or request layout.
                    if (footer.elevation != 0f) footer.elevation = 0f
                    if (footer.translationZ != 0f) footer.translationZ = 0f
                    FloatingChatCardVisuals.apply(footer, CORNER_RADIUS_DP, translucent = true, borderless = true, transparency = backgroundTransparency)
                    FloatingFooterInputSurfaces.maintain(footer)
                    liftNewMessageBubble(footer)
                }
            }
            true

        }
        preDrawListeners[footer] = listener
        footer.viewTreeObserver.addOnPreDrawListener(listener)
    }

    private fun liftNewMessageBubble(footer: View) {
        val state = states[footer] ?: return
        val content = state.chatContent?.takeIf { it.isAttachedToWindow }
            ?: (footer.parent as? ViewGroup)?.let { parent ->
                (0 until parent.childCount).asSequence().map(parent::getChildAt)
                    .firstOrNull { it.javaClass.name == CHATTING_CONTENT_CLASS } as? ViewGroup
            }?.also { state.chatContent = it }
            ?: return
        for (index in 0 until content.childCount) {
            val candidate = content.getChildAt(index)
            if (!isNewMessageBubble(candidate)) continue
            val lp = candidate.layoutParams as? FrameLayout.LayoutParams ?: continue
            if (lp.gravity and Gravity.VERTICAL_GRAVITY_MASK != Gravity.BOTTOM) continue
            val footerLocation = state.footerLocation.also(footer::getLocationOnScreen)
            val contentLocation = state.contentLocation.also(content::getLocationOnScreen)
            val cardTop = footerLocation[1] - contentLocation[1]
            val overlap = (content.height - cardTop).coerceAtLeast(0)
            val target = maxOf(originalNewMessageMargin(candidate), overlap + dp(8))
            if (lp.bottomMargin != target) {
                lp.bottomMargin = target
                candidate.layoutParams = lp
            }
            return
        }
    }

    private fun originalNewMessageMargin(view: View): Int {
        val lp = view.layoutParams as? FrameLayout.LayoutParams ?: return 0
        return newMessageMargins.getOrPut(view) { lp.bottomMargin }
    }

    private fun isNewMessageBubble(view: View): Boolean {
        val group = view as? ViewGroup ?: return false
        val linear = group as? android.widget.LinearLayout ?: return false
        if (linear.gravity and Gravity.VERTICAL_GRAVITY_MASK != Gravity.CENTER_VERTICAL) return false
        var hasText = false
        var hasIcon = false
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            if (child is TextView) hasText = true
            if (child.javaClass.name == "com.tencent.mm.ui.widget.imageview.WeImageView") hasIcon = true
        }
        return hasText && hasIcon
    }

    private fun findChatRecycler(footer: View): View? {
        val scrollLayout = footer.parent as? View ?: return null
        if (scrollLayout.javaClass.name != CHATTING_SCROLL_LAYOUT_CLASS) return null
        val listHost = find(scrollLayout) { it.javaClass.name == CHATTING_LIST_CLASS } ?: return null
        return find(listHost) { view ->
            view.javaClass.name == SCROLL_RECYCLER_CLASS ||
                view.javaClass.name == CHATTING_RECYCLER_CLASS
        }
    }

    private fun restore(footer: View?) {
        footer ?: return
        panelStates.remove(footer)
        SendButtonSettlement.cancel(footer)
        preDrawListeners.remove(footer)?.let { listener ->
            runCatching { footer.viewTreeObserver.removeOnPreDrawListener(listener) }
        }
        FloatingFooterInputSurfaces.restore(footer)
        val state = states.remove(footer) ?: return
        runCatching {
            restorePanelHeight(state.panel)
            state.chatRecycler?.let { recycler ->
                listOwners.remove(recycler)
                state.bottomInset.clear()
                state.recyclerPaddingBottom?.let { bottom ->
                    recycler.setPadding(
                        recycler.paddingLeft,
                        recycler.paddingTop,
                        recycler.paddingRight,
                        bottom
                    )
                }
            }
            if (state.reparented) {
                state.changedRules.forEach { (view, rules) ->
                    val params = view.layoutParams as? RelativeLayout.LayoutParams ?: return@forEach
                    for (verb in params.rules.indices) params.removeRule(verb)
                    rules.forEachIndexed { verb, anchor ->
                        if (anchor != 0) params.addRule(verb, anchor)
                    }
                    view.layoutParams = params
                }
                (state.panel.parent as? ViewGroup)?.removeView(state.panel)
                state.inputColumn.addView(
                    state.panel,
                    state.panelIndex.coerceAtMost(state.inputColumn.childCount),
                    copyLayoutParams(state.panelLayoutParams)
                )
                state.panel.visibility = state.panelVisibility
            }
            val params = footer.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                params.leftMargin = state.leftMargin
                params.rightMargin = state.rightMargin
                params.topMargin = state.topMargin
                params.bottomMargin = state.bottomMargin
                footer.layoutParams = params
            }
            footer.elevation = state.elevation
            footer.translationZ = state.translationZ
            footer.outlineProvider = state.outlineProvider
            footer.clipToOutline = state.clipToOutline
            FloatingChatCardVisuals.restore(footer)
            footer.invalidateOutline()
        }.onFailure { HLog.e("[Hchat:$TAG] 恢复微信输入区结构失败", it) }
    }

    fun destroy() {
        livePrefs.unregisterOnSharedPreferenceChangeListener(liveListener)
        configHandler.removeCallbacksAndMessages(null)
        footerTrackers.keys.toList().forEach(::disposeFooter)
        footerTrackers.clear()
        states.keys.toList().forEach(::restore)
        newMessageMargins.forEach { (view, margin) ->
            val lp = view.layoutParams as? FrameLayout.LayoutParams ?: return@forEach
            lp.bottomMargin = margin
            view.layoutParams = lp
        }
        newMessageMargins.clear()
        panelBodyOriginalHeights.forEach { (view, height) ->
            view.layoutParams?.let { lp ->
                lp.height = height
                view.layoutParams = lp
            }
        }
        panelBodyOriginalHeights.clear()
        panelOriginalHeights.forEach { (view, height) ->
            view.layoutParams?.let { lp ->
                lp.height = height
                view.layoutParams = lp
            }
        }
        panelOriginalHeights.clear()
    }

    private fun isPanelState(state: Int): Boolean =
        state == PANEL_STATE_SMILEY || state == PANEL_STATE_APP

    private fun isEnabled(): Boolean = context.configStore().getGlobalBoolean(
        "${FloatingChatFooterFeature.ID}_enabled",
        false
    )

    private fun dp(value: Int): Int =
        (value * context.hostContext().resources.displayMetrics.density).roundToInt()

    private fun find(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        val group = root as? ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            find(group.getChildAt(index), predicate)?.let { return it }
        }
        return null
    }

    private fun copyLayoutParams(params: ViewGroup.LayoutParams): ViewGroup.LayoutParams =
        when (params) {
            is RelativeLayout.LayoutParams -> RelativeLayout.LayoutParams(params)
            is ViewGroup.MarginLayoutParams -> ViewGroup.MarginLayoutParams(params)
            else -> ViewGroup.LayoutParams(params)
        }

    private fun reportStructureFailureOnce(reason: String) {
        if (structureFailureReported) return
        structureFailureReported = true
        HLog.e("[Hchat:$TAG] 功能未生效: $reason")
    }

    private class FooterTracker(
        var layout: ViewGroup,
        val visibility: Int,
        var ready: Boolean = false,
        var failed: Boolean = false,
        var settlePasses: Int = 0,
        var preDraw: ViewTreeObserver.OnPreDrawListener? = null,
        var layoutListener: View.OnLayoutChangeListener? = null,
        var attempts: Int = 0,
        var failureReported: Boolean = false,
        var globalObserver: ViewTreeObserver? = null,
        var globalListener: ViewTreeObserver.OnGlobalLayoutListener? = null,
    )

    private data class FooterState(
        val panel: View,
        val inputColumn: ViewGroup,
        val root: RelativeLayout,
        val panelIndex: Int,
        val panelLayoutParams: ViewGroup.LayoutParams,
        val panelVisibility: Int,
        val leftMargin: Int,
        val rightMargin: Int,
        val topMargin: Int,
        val bottomMargin: Int,
        val elevation: Float,
        val translationZ: Float,
        val outlineProvider: ViewOutlineProvider?,
        val clipToOutline: Boolean,
        val changedRules: MutableMap<View, IntArray> = LinkedHashMap(),
        var reparented: Boolean = false,
        val footerLocation: IntArray = IntArray(2),
        val contentLocation: IntArray = IntArray(2),
        val listLocation: IntArray = IntArray(2),
        var chatContent: ViewGroup? = null,
        var chatRecycler: View? = null,
        var recyclerPaddingBottom: Int? = null,
        val bottomInset: ChatListBottomInset = ChatListBottomInset(),
        var listExtra: Int = 0,
        var baseInputHeight: Int = 0
    )

    private object FloatingFooterOutlineProvider : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val radius = CORNER_RADIUS_DP * view.resources.displayMetrics.density
            outline.setRoundRect(0, 0, view.width, view.height.coerceAtLeast(1), radius)
        }
    }

    companion object {
        private const val TAG = "悬浮输入框"
        private const val FOOTER_CLASS = "com.tencent.mm.pluginsdk.ui.chat.ChatFooter"
        private const val FOOTER_BOTTOM_CLASS = "com.tencent.mm.pluginsdk.ui.chat.ChatFooterBottom"
        private const val CHATTING_SCROLL_LAYOUT_CLASS = "com.tencent.mm.pluginsdk.ui.chat.ChattingScrollLayout"
        private const val CHATTING_UI_LAYOUT_CLASS = "com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout"
        private const val CHATTING_CONTENT_CLASS = "com.tencent.mm.pluginsdk.ui.chat.ChattingContent"
        private const val CHATTING_LIST_CLASS = "com.tencent.mm.ui.chatting.view.MMChattingListView"
        private const val SCROLL_RECYCLER_CLASS = "com.tencent.mm.pluginsdk.ui.tools.ScrollControlRecyclerView"
        private const val CHATTING_RECYCLER_CLASS = "com.tencent.mm.pluginsdk.ui.tools.ChattingRecyclerView"
        private const val CACHE_REFRESH_BOTTOM_HEIGHT = "refresh_bottom_height"
        private const val CACHE_SWITCH_PANEL = "switch_panel"
        private const val CACHE_SCROLL_CONTENT_TO = "scroll_content_to"
        private const val CACHE_ENTER_KEYBOARD_STATE = "enter_keyboard_state"
        private const val REFRESH_LOG_TAG = "MicroMsg.ChatFooter"
        private const val REFRESH_LOG_ANCHOR = "[refreshBottomHeight] keyborPx:%d"
        private const val SWITCH_LOG_ANCHOR = "switchPanel: %s, %s"
        private const val ENTER_KEYBOARD_LOG_ANCHOR = "isScrolling!! pass this event!"
        private const val SCROLL_LOG_TAG = "MicroMsg.ChattingScrollLayout"
        private const val SCROLL_LOG_ANCHOR =
            "scrollContentTo: y:%s, targetScroll:%s, alwaysScroll:%s"
        private const val PANEL_STATE_NONE = 0
        private const val PANEL_STATE_SMILEY = 2
        private const val PANEL_STATE_APP = 3
        private const val MAX_PANEL_HEIGHT_DP = 265
        private const val MIN_PANEL_HEIGHT_DP = 180
        private const val MAX_PANEL_HEIGHT_RATIO = 0.30f
        private const val PANEL_EXTRA_HEIGHT_DP = 50
        private const val SIDE_MARGIN_DP = 12
        private const val BOTTOM_GAP_DP = 8
        private const val CORNER_RADIUS_DP = 24
        private const val INITIAL_LAYOUT_RETRY_COUNT = 3
        private const val REQUIRED_HOOK_COUNT = 6
    }
}
