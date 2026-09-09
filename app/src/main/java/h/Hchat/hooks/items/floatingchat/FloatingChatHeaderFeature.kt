package h.Hchat.hooks.items.floatingchat

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
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
        val clazz = KavaReflector.loadClass(layoutClass, context.hostClassLoader()) ?: return false
        var count = 0
        KavaReflector.findMethodRecursive(clazz, "onLayout", Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)?.let {
            count += hook(it) { param -> (param.thisObject as? ViewGroup)?.let { layout -> layout.postOnAnimation { apply(layout) } } }
        }
        KavaReflector.findMethodRecursive(clazz, "fitSystemWindows", Rect::class.java)?.let {
            count += hook(it) { param -> (param.thisObject as? ViewGroup)?.let { layout -> layout.postOnAnimation { apply(layout) } } }
        }
        installed = count > 0
        if (!installed) HLog.e("[Hchat:$TAG] 未找到 ChattingUILayout 生命周期方法")
        return installed
    }

    private fun hook(method: Member, after: (XC_MethodHook.MethodHookParam) -> Unit): Int {
        if (!hooked.add(method)) return 1
        return runCatching {
            HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) = runCatching { after(param) }.onFailure { HLog.e("[Hchat:$TAG] 布局更新失败", it) }
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
                parent = parent,
                params = lp,
                leftMargin = marginLp?.leftMargin,
                rightMargin = marginLp?.rightMargin,
                topMargin = marginLp?.topMargin,
                elevation = header.elevation
            ).also { states[header] = it }
            if (marginLp != null) {
                marginLp.leftMargin = (state.leftMargin ?: marginLp.leftMargin) + dp(12)
                marginLp.rightMargin = (state.rightMargin ?: marginLp.rightMargin) + dp(12)
                marginLp.topMargin = (state.topMargin ?: marginLp.topMargin) + dp(4)
                header.layoutParams = marginLp
            }
            header.elevation = dp(4).toFloat()
            FloatingChatCardVisuals.apply(header, 24)
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
        FloatingChatCardVisuals.restore(view)
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
    private data class HeaderState(
        val parent: ViewGroup,
        val params: ViewGroup.LayoutParams?,
        val leftMargin: Int?,
        val rightMargin: Int?,
        val topMargin: Int?,
        val elevation: Float = 0f
    )
    companion object { private const val TAG = "悬浮标题栏" }
}