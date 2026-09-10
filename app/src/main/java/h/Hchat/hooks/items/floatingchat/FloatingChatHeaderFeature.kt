package h.Hchat.hooks.items.floatingchat

import android.graphics.Outline
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import de.robv.android.xposed.XC_MethodHook
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.HLog
import h.Hchat.utils.KavaReflector
import java.lang.reflect.Member
import java.util.WeakHashMap
import kotlin.math.roundToInt

class FloatingChatHeaderFeature : BaseFeature() {
    private var runtime: HeaderRuntime? = null
    override fun featureId(): String = ID
    override fun name(): String = "悬浮标题栏"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(FloatingChatHeaderSettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        runtime = HeaderRuntime(context)
        DexInstallScheduler.schedule(ID, name()) { runtime?.install() == true }
    }
    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }
    companion object { const val ID = "floating_chat_header" }
}

private class HeaderRuntime(private val context: FeatureContext) {
    private val hooked = HashSet<Member>()
    private val states = WeakHashMap<View, HeaderState>()
    private var installed = false
    private val headerClass = "androidx.appcompat.widget.ActionBarContainer"
    private val layoutClass = "com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout"

    fun install(): Boolean {
        if (installed) return true
        val clazz = KavaReflector.loadClass(layoutClass, context.hostClassLoader())
        if (clazz == null) {
            HLog.e("[Hchat:$TAG] 功能未生效: 未找到目标类 $layoutClass")
            return false
        }
        var count = 0
        KavaReflector.findMethodRecursive(clazz, "onLayout", Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)?.let {
            count += hook(it) { param -> (param.thisObject as? ViewGroup)?.let { layout -> layout.postOnAnimation { apply(layout) } } }
        }
        KavaReflector.findMethodRecursive(clazz, "fitSystemWindows", Rect::class.java)?.let {
            count += hook(it) { param -> (param.thisObject as? ViewGroup)?.let { layout -> layout.postOnAnimation { apply(layout) } } }
        }
        installed = count > 0
        if (!installed) HLog.e("[Hchat:$TAG] 功能未生效: 未找到 ChattingUILayout 生命周期方法")
        return installed
    }

    private fun hook(method: Member, after: (XC_MethodHook.MethodHookParam) -> Unit): Int {
        if (!hooked.add(method)) return 1
        return runCatching {
            HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    runCatching { after(param) }
                        .onFailure { HLog.e("[Hchat:$TAG] 布局更新失败", it) }
                }
            })
            1
        }.getOrElse { hooked.remove(method); HLog.e("[Hchat:$TAG] Hook 安装失败", it); 0 }
    }

    private fun apply(layout: ViewGroup) {
        if (!isEnabled()) { restore(layout); return }
        find(layout) { it.javaClass.name == headerClass }?.let { header ->
            val parent = header.parent as? ViewGroup ?: return
            val lp = header.layoutParams
            val marginLp = lp as? ViewGroup.MarginLayoutParams
            val state = states[header] ?: HeaderState(
                backgroundLayers = backgroundLayers(header, layout),
                leftMargin = marginLp?.leftMargin,
                rightMargin = marginLp?.rightMargin,
                topMargin = marginLp?.topMargin,
                elevation = header.elevation,
                outlineProvider = header.outlineProvider,
                clipToOutline = header.clipToOutline
            ).also {
                states[header] = it
                it.backgroundLayers.forEach(FloatingChatCardVisuals::makeTransparent)
            }
            state.backgroundLayers.forEach(FloatingChatCardVisuals::ensureTransparent)
            if (marginLp != null) {
                val left = (state.leftMargin ?: marginLp.leftMargin) + dp(12)
                val right = (state.rightMargin ?: marginLp.rightMargin) + dp(12)
                val top = (state.topMargin ?: marginLp.topMargin) + dp(4)
                if (marginLp.leftMargin != left || marginLp.rightMargin != right || marginLp.topMargin != top) {
                    marginLp.leftMargin = left
                    marginLp.rightMargin = right
                    marginLp.topMargin = top
                    header.layoutParams = marginLp
                }
            }
            val elevation = dp(4).toFloat()
            if (header.elevation != elevation) header.elevation = elevation
            header.outlineProvider = FloatingHeaderOutlineProvider
            header.clipToOutline = true
            FloatingChatCardVisuals.apply(header, 24)
            header.invalidateOutline()
            parent.invalidate()
        }
    }

    private fun restore(layout: ViewGroup) {
        findAll(layout) { states.containsKey(it) }.forEach { restoreView(it) }
    }
    private fun restoreView(view: View) {
        val state = states.remove(view) ?: return
        view.layoutParams?.let { params ->
            (params as? ViewGroup.MarginLayoutParams)?.let {
                state.leftMargin?.let { value -> it.leftMargin = value }
                state.rightMargin?.let { value -> it.rightMargin = value }
                state.topMargin?.let { value -> it.topMargin = value }
            }
            view.layoutParams = params
        }
        view.elevation = state.elevation
        view.outlineProvider = state.outlineProvider
        view.clipToOutline = state.clipToOutline
        FloatingChatCardVisuals.restore(view)
        state.backgroundLayers.asReversed().forEach(FloatingChatCardVisuals::restoreTransparent)
        view.invalidateOutline()
    }
    fun destroy() { states.keys.toList().forEach(::restoreView) }
    private fun isEnabled() = context.configStore().getGlobalBoolean("${FloatingChatHeaderFeature.ID}_enabled", false)
    private fun dp(value: Int) = (value * context.hostContext().resources.displayMetrics.density).roundToInt()
    private fun find(root: View, predicate: (View) -> Boolean): View? = if (predicate(root)) root else (root as? ViewGroup)?.let { group -> (0 until group.childCount).asSequence().mapNotNull { find(group.getChildAt(it), predicate) }.firstOrNull() }
    private fun findAll(root: View, predicate: (View) -> Boolean): List<View> = buildList {
        fun walk(view: View) {
            if (predicate(view)) add(view)
            (view as? ViewGroup)?.let { group -> for (index in 0 until group.childCount) walk(group.getChildAt(index)) }
        }
        walk(root)
    }
    private fun backgroundLayers(card: View, boundary: View): List<View> = buildList {
        var current = card.parent as? View
        while (current != null && current !== boundary) {
            add(current)
            current = current.parent as? View
        }
    }
    private data class HeaderState(
        val backgroundLayers: List<View>,
        val leftMargin: Int?,
        val rightMargin: Int?,
        val topMargin: Int?,
        val elevation: Float = 0f,
        val outlineProvider: ViewOutlineProvider?,
        val clipToOutline: Boolean
    )
    private object FloatingHeaderOutlineProvider : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(
                0,
                0,
                view.width,
                view.height.coerceAtLeast(1),
                dpStatic(view, 24)
            )
        }
        private fun dpStatic(view: View, value: Int): Float =
            value * view.resources.displayMetrics.density
    }
    companion object { private const val TAG = "悬浮标题栏" }
}