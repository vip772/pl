package h.Hchat.hooks.items.floatingchat

import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.RelativeLayout
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
    private val methodPrefs = DexMethodCache.prefs(
        context.hostContext(),
        "Hchat_floating_chat_footer_method_cache"
    )
    private var appliedReported = false
    private var structureFailureReported = false
    private var panelState = PANEL_STATE_NONE
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
            count += hookPanelState(method, footerClass)
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
                    runCatching {
                        apply(footer)
                        if (isPanelState(state)) neutralizeHostTranslation(footer)
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
        val inputHeight = visibleHeightExcludingPanel(state.panel)
        if (inputHeight <= 0) return

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
        if (isPanelState(panelState) && state.panel.visibility == View.VISIBLE) {
            neutralizeHostTranslation(footer)
        }

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
            clipToOutline = footer.clipToOutline
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
        if (recycler.paddingBottom == target) return
        val wasAtBottom = !recycler.canScrollVertically(1)
        val old = recycler.paddingBottom
        recycler.setPadding(recycler.paddingLeft, recycler.paddingTop, recycler.paddingRight, target)
        if (wasAtBottom && target > old) recycler.scrollBy(0, target - old)
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
        val state = states.remove(footer) ?: return
        runCatching {
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
    }

    private fun isPanelState(state: Int): Boolean =
        state == PANEL_STATE_SMILEY || state == PANEL_STATE_APP

    private fun neutralizeHostTranslation(footer: View) {
        val host = footer.parent as? View ?: return
        if (host.translationY != 0f) host.translationY = 0f
    }

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
        var recyclerPaddingBottom: Int? = null
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
        private const val CHATTING_LIST_CLASS = "com.tencent.mm.ui.chatting.view.MMChattingListView"
        private const val SCROLL_RECYCLER_CLASS = "com.tencent.mm.pluginsdk.ui.tools.ScrollControlRecyclerView"
        private const val CHATTING_RECYCLER_CLASS = "com.tencent.mm.pluginsdk.ui.tools.ChattingRecyclerView"
        private const val CACHE_REFRESH_BOTTOM_HEIGHT = "refresh_bottom_height"
        private const val CACHE_SWITCH_PANEL = "switch_panel"
        private const val REFRESH_LOG_TAG = "MicroMsg.ChatFooter"
        private const val REFRESH_LOG_ANCHOR = "[refreshBottomHeight] keyborPx:%d"
        private const val SWITCH_LOG_ANCHOR = "switchPanel: %s, %s"
        private const val PANEL_STATE_NONE = 0
        private const val PANEL_STATE_SMILEY = 2
        private const val PANEL_STATE_APP = 3
        private const val SIDE_MARGIN_DP = 12
        private const val BOTTOM_GAP_DP = 8
        private const val ELEVATION_DP = 4
        private const val CORNER_RADIUS_DP = 24
        private const val REQUIRED_HOOK_COUNT = 4
    }
}
