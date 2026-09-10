package h.Hchat.hooks.items.floatingchat

import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
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

    private fun isRefreshBottomHeight(method: Method, footerClass: Class<*>): Boolean {
        return method.declaringClass == footerClass &&
            !KavaReflector.isStatic(method) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
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
        val panelHeight = panel?.layoutParams?.height?.takeIf { it > 0 }
            ?: panel?.height?.takeIf { it > 0 }
            ?: return
        val state = states[footer] ?: FooterState(
            backgroundLayers = backgroundLayers(footer),
            leftMargin = marginParams.leftMargin,
            rightMargin = marginParams.rightMargin,
            topMargin = marginParams.topMargin,
            bottomMargin = marginParams.bottomMargin,
            elevation = footer.elevation,
            outlineProvider = footer.outlineProvider,
            clipToOutline = footer.clipToOutline
        ).also {
            states[footer] = it
            it.backgroundLayers.forEach(FloatingChatCardVisuals::makeTransparent)
        }

        state.backgroundLayers.forEach(FloatingChatCardVisuals::ensureTransparent)
        val side = dp(SIDE_MARGIN_DP)
        val gap = dp(BOTTOM_GAP_DP)
        val targetBottom = -panelHeight + gap
        var layoutChanged = false
        if (marginParams.leftMargin != side) {
            marginParams.leftMargin = side
            layoutChanged = true
        }
        if (marginParams.rightMargin != side) {
            marginParams.rightMargin = side
            layoutChanged = true
        }
        if (marginParams.topMargin != state.topMargin) {
            marginParams.topMargin = state.topMargin
            layoutChanged = true
        }
        if (marginParams.bottomMargin != targetBottom) {
            marginParams.bottomMargin = targetBottom
            layoutChanged = true
        }
        if (layoutChanged) footer.layoutParams = marginParams

        footer.outlineProvider = FloatingFooterOutlineProvider
        footer.clipToOutline = true
        footer.elevation = dp(ELEVATION_DP).toFloat()
        FloatingChatCardVisuals.apply(footer, CORNER_RADIUS_DP)
        footer.invalidateOutline()

        if (!appliedReported && footer.width > 0 && footer.height > 0) {
            appliedReported = true
            HLog.i(
                "[Hchat:$TAG] 功能实际生效: root=${footer.javaClass.name} " +
                    "size=${footer.width}x${footer.height} panelHeight=$panelHeight " +
                    "margin=${marginParams.leftMargin},${marginParams.rightMargin}," +
                    "${marginParams.bottomMargin}"
            )
        }
    }

    private fun restore(footer: View?) {
        footer ?: return
        val state = states.remove(footer) ?: return
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
        state.backgroundLayers.asReversed().forEach(FloatingChatCardVisuals::restoreTransparent)
        footer.invalidateOutline()
    }

    fun destroy() {
        states.keys.toList().forEach(::restore)
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

    private fun backgroundLayers(card: View): List<View> = buildList {
        var current = card.parent as? View
        while (current != null && current.javaClass.name != CHATTING_LAYOUT_CLASS) {
            add(current)
            current = current.parent as? View
        }
    }

    private data class FooterState(
        val backgroundLayers: List<View>,
        val leftMargin: Int,
        val rightMargin: Int,
        val topMargin: Int,
        val bottomMargin: Int,
        val elevation: Float,
        val outlineProvider: ViewOutlineProvider?,
        val clipToOutline: Boolean
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
        private const val CHATTING_LAYOUT_CLASS = "com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout"
        private const val CACHE_REFRESH_BOTTOM_HEIGHT = "refresh_bottom_height"
        private const val REFRESH_LOG_TAG = "MicroMsg.ChatFooter"
        private const val REFRESH_LOG_ANCHOR = "[refreshBottomHeight] keyborPx:%d"
        private const val SIDE_MARGIN_DP = 12
        private const val BOTTOM_GAP_DP = 8
        private const val ELEVATION_DP = 4
        private const val CORNER_RADIUS_DP = 24
        private const val REQUIRED_HOOK_COUNT = 3
    }
}
