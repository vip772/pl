package h.Hchat.hooks.items.floatingchat

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
import h.Hchat.dexkit.DexMethodCache
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.HLog
import h.Hchat.utils.KavaReflector
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.roundToInt

class FloatingChatFooterFeature : BaseFeature() {
    private var runtime: FooterRuntime? = null
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
    private val hooked = HashSet<Member>()
    private val states = WeakHashMap<View, FooterState>()
    private val newMessageMargins = WeakHashMap<View, Int>()
    private val diagnosedFooters = WeakHashMap<View, Boolean>()
    private val preDrawListeners = WeakHashMap<View, ViewTreeObserver.OnPreDrawListener>()
    private val panelOriginalHeights = WeakHashMap<View, Int>()
    private val panelBodyOriginalHeights = WeakHashMap<View, Int>()
    private val methodPrefs = DexMethodCache.prefs(
        context.hostContext(),
        "Hchat_floating_chat_footer_method_cache"
    )
    private var appliedReported = false
    private var structureFailureReported = false
    private var panelState = PANEL_STATE_NONE
    private var switchPanelMethod: Method? = null
    private var installed = false

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

        var count = 0
        KavaReflector.findDeclaredMethod(footerClass, "onAttachedToWindow")?.let { method ->
            count += hookFooter(method, footerClass) { footer ->
                footer.postOnAnimation { apply(footer) }
            }
        }
        KavaReflector.findDeclaredMethod(footerClass, "onDetachedFromWindow")?.let { method ->
            count += hookFooter(method, footerClass) { footer -> restore(footer) }
        }
        locateRefreshBottomHeight(footerClass)?.let { method ->
            count += hookFooter(method, footerClass) { footer -> apply(footer) }
        }
        locateSwitchPanel(footerClass)?.let { method ->
            switchPanelMethod = method
            count += hookPanelState(method, footerClass)
        }
        locateEnterKeyboardState(footerClass)?.let { method ->
            count += hookEnterKeyboardState(method, footerClass)
        }
        locateScrollContentTo()?.let { method ->
            count += hookScrollContent(method)
        }
        installed = count >= REQUIRED_HOOK_COUNT
        if (!installed) {
            HLog.e(
                "[Hchat:$TAG] 功能未生效: 入口不完整 count=$count/$REQUIRED_HOOK_COUNT " +
                    "target=$FOOTER_CLASS"
            )
        }
        return installed
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
            HLog.i("[Hchat:$TAG] 已定位底部高度刷新入口: ${method.toGenericString()}")
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
            HLog.i("[Hchat:$TAG] 已定位键盘状态入口: ${method.toGenericString()}")
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
                    val panel = find(footer) { it.javaClass.name == FOOTER_BOTTOM_CLASS } ?: return
                    if (!isPanelState(panelState) || panel.visibility != View.VISIBLE) return
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
            HLog.i("[Hchat:$TAG] 已定位会话滚动入口: ${method.toGenericString()}")
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
                    val requestedY = param.args.firstOrNull() as? Int ?: return
                    val state = states[footer]
                    val abnormalLift = !isPanelState(panelState) &&
                        state?.baseInputHeight?.takeIf { it > 0 }?.let { requestedY >= it } == true
                    if ((isPanelState(panelState) && panel.visibility == View.VISIBLE) || abnormalLift) {
                        param.args[0] = 0
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
                    panelState = state
                    val footer = param.thisObject as? View ?: return
                    val panel = find(footer) { it.javaClass.name == FOOTER_BOTTOM_CLASS } ?: return
                    if (!isPanelState(state)) panel.visibility = View.GONE
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!isEnabled() || !footerClass.isInstance(param.thisObject)) return
                    val state = param.args.firstOrNull() as? Int ?: return
                    val footer = param.thisObject as? View ?: return
                    val panel = find(footer) { it.javaClass.name == FOOTER_BOTTOM_CLASS } ?: return
                    panelState = state
                    panel.visibility = if (isPanelState(state)) View.VISIBLE else View.GONE
                    if (isPanelState(state)) applyCompactPanelHeight(footer, panel)
                    runCatching {
                        apply(footer)
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

    private fun apply(footer: View) {
        if (!isEnabled()) {
            restore(footer)
            return
        }
        if (!footer.isAttachedToWindow) return
        val marginParams = footer.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val panel = find(footer) { it.javaClass.name == FOOTER_BOTTOM_CLASS }
        if (panel == null) {
            reportStructureFailureOnce("未找到 $FOOTER_BOTTOM_CLASS")
            return
        }
        val state = states[footer] ?: createState(footer, panel, marginParams) ?: return
        if (!state.reparented && !reparentPanel(state)) return
        if (isPanelState(panelState)) applyCompactPanelHeight(footer, state.panel)
        val measuredInputHeight = visibleHeightExcludingPanel(state.panel)
        if (measuredInputHeight <= 0) return
        if (state.baseInputHeight <= 0 && !isPanelState(panelState)) state.baseInputHeight = measuredInputHeight
        val inputHeight = if (isPanelState(panelState)) {
            measuredInputHeight
        } else {
            state.baseInputHeight.takeIf { it > 0 } ?: measuredInputHeight
        }

        val side = dp(SIDE_MARGIN_DP)
        val gap = dp(BOTTOM_GAP_DP)
        val targetTop = -(inputHeight + gap)
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
        if (layoutChanged) footer.layoutParams = marginParams

        footer.outlineProvider = FloatingFooterOutlineProvider
        footer.clipToOutline = true
        footer.elevation = dp(ELEVATION_DP).toFloat()
        FloatingChatCardVisuals.apply(footer, CORNER_RADIUS_DP)
        footer.invalidateOutline()
        applyChatListPadding(footer, inputHeight + gap, state)
        ensurePreDrawTracking(footer)

        if (!appliedReported && footer.width > 0) {
            appliedReported = true
            HLog.i(
                "[Hchat:$TAG] 功能实际生效: root=${footer.javaClass.name} " +
                    "card=${footer.width}x$inputHeight panelParent=${state.panel.parent?.javaClass?.name} " +
                    "margin=${marginParams.leftMargin},${marginParams.rightMargin}," +
                    "${marginParams.topMargin},${marginParams.bottomMargin}"
            )
        }
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
            if (panel.id == View.NO_ID) panel.id = View.generateViewId()
            state.inputColumn.removeView(panel)
            state.root.addView(
                panel,
                0,
                RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    state.panelLayoutParams.height
                )
            )
            panel.visibility = View.GONE
            for (index in 0 until state.root.childCount) {
                val child = state.root.getChildAt(index)
                if (child === panel) continue
                val params = child.layoutParams as? RelativeLayout.LayoutParams ?: continue
                if (params.rules[RelativeLayout.BELOW] == 0) {
                    state.changedRules[child] = params.rules.copyOf()
                    params.addRule(RelativeLayout.BELOW, panel.id)
                    child.layoutParams = params
                }
            }
            state.reparented = true
            HLog.i(
                "[Hchat:$TAG] 输入区结构重排完成: panel=${panel.javaClass.name} " +
                    "root=${state.root.javaClass.name}"
            )
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
            ?: return
        if (!panelOriginalHeights.containsKey(panel)) panelOriginalHeights[panel] = natural
        val hostHeight = (footer.parent as? View)?.height?.takeIf { it > 0 }
            ?: footer.resources.displayMetrics.heightPixels
        val target = minOf(
            natural,
            dp(MAX_PANEL_HEIGHT_DP),
            (hostHeight * MAX_PANEL_HEIGHT_RATIO).roundToInt()
        ).coerceAtLeast(dp(MIN_PANEL_HEIGHT_DP))
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

    private fun visibleHeightExcludingPanel(panel: View): Int {
        val root = panel.parent as? ViewGroup ?: return 0
        var total = 0
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child !== panel && child.visibility != View.GONE) total += child.height
        }
        return total
    }

    private fun applyChatListPadding(footer: View, extra: Int, state: FooterState) {
        val recycler = state.chatRecycler?.takeIf { it.isAttachedToWindow }
            ?: findChatRecycler(footer)?.also { state.chatRecycler = it }
            ?: return
        if (state.recyclerPaddingBottom == null) state.recyclerPaddingBottom = recycler.paddingBottom
        val base = state.recyclerPaddingBottom ?: return
        val target = base + extra
        if (recycler.paddingBottom == target) {
            liftNewMessageBubble(footer)
            return
        }
        val wasAtBottom = !recycler.canScrollVertically(1)
        val old = recycler.paddingBottom
        recycler.setPadding(recycler.paddingLeft, recycler.paddingTop, recycler.paddingRight, target)
        if (wasAtBottom && target > old) recycler.scrollBy(0, target - old)
        liftNewMessageBubble(footer)
    }

    private fun ensurePreDrawTracking(footer: View) {
        if (preDrawListeners[footer] != null) return
        val listener = ViewTreeObserver.OnPreDrawListener {
            if (isEnabled() && footer.isAttachedToWindow) liftNewMessageBubble(footer)
            true
        }
        preDrawListeners[footer] = listener
        footer.viewTreeObserver.addOnPreDrawListener(listener)
    }

    private fun liftNewMessageBubble(footer: View) {
        val content = find(footer.rootView) {
            it.javaClass.name == CHATTING_CONTENT_CLASS
        } as? ViewGroup
        diagnoseFooterLayout(footer, content)
        content ?: return
        for (index in 0 until content.childCount) {
            val candidate = content.getChildAt(index)
            if (!isNewMessageBubble(candidate)) continue
            val lp = candidate.layoutParams as? FrameLayout.LayoutParams ?: continue
            if (lp.gravity and Gravity.VERTICAL_GRAVITY_MASK != Gravity.BOTTOM) continue
            val footerLocation = IntArray(2).also(footer::getLocationOnScreen)
            val contentLocation = IntArray(2).also(content::getLocationOnScreen)
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

    private fun diagnoseFooterLayout(footer: View, content: ViewGroup?) {
        if (diagnosedFooters.put(footer, true) != null) return
        val footerLoc = IntArray(2).also(footer::getLocationOnScreen)
        val children = if (content == null) {
            "ChattingContent=missing"
        } else {
            (0 until content.childCount).joinToString(" | ") { index ->
                val child = content.getChildAt(index)
                val lp = child.layoutParams
                val frame = lp as? FrameLayout.LayoutParams
                val loc = IntArray(2).also(child::getLocationOnScreen)
                "#$index ${child.javaClass.name} size=${child.width}x${child.height} " +
                    "screen=${loc[0]},${loc[1]} vis=${child.visibility} " +
                    "lp=${lp?.javaClass?.name}:${lp?.width}x${lp?.height} " +
                    "gravity=${frame?.gravity} margins=${frame?.leftMargin},${frame?.topMargin}," +
                    "${frame?.rightMargin},${frame?.bottomMargin} newMessage=${isNewMessageBubble(child)}"
            }
        }
        HLog.i(
            "[Hchat:$TAG] 新消息诊断: footerScreen=${footerLoc[0]},${footerLoc[1]} " +
                "footerSize=${footer.width}x${footer.height} children=[$children]"
        )
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
        preDrawListeners.remove(footer)?.let { listener ->
            runCatching { footer.viewTreeObserver.removeOnPreDrawListener(listener) }
        }
        val state = states.remove(footer) ?: return
        runCatching {
            restorePanelHeight(state.panel)
            state.chatRecycler?.let { recycler ->
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
            footer.outlineProvider = state.outlineProvider
            footer.clipToOutline = state.clipToOutline
            FloatingChatCardVisuals.restore(footer)
            footer.invalidateOutline()
        }.onFailure { HLog.e("[Hchat:$TAG] 恢复微信输入区结构失败", it) }
    }

    fun destroy() {
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
        val outlineProvider: ViewOutlineProvider?,
        val clipToOutline: Boolean,
        val changedRules: MutableMap<View, IntArray> = LinkedHashMap(),
        var reparented: Boolean = false,
        var chatRecycler: View? = null,
        var recyclerPaddingBottom: Int? = null,
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
        private const val MAX_PANEL_HEIGHT_DP = 260
        private const val MIN_PANEL_HEIGHT_DP = 180
        private const val MAX_PANEL_HEIGHT_RATIO = 0.30f
        private const val PANEL_STATE_NONE = 0
        private const val PANEL_STATE_SMILEY = 2
        private const val PANEL_STATE_APP = 3
        private const val SIDE_MARGIN_DP = 12
        private const val BOTTOM_GAP_DP = 8
        private const val ELEVATION_DP = 4
        private const val CORNER_RADIUS_DP = 24
        private const val REQUIRED_HOOK_COUNT = 6
    }
}
