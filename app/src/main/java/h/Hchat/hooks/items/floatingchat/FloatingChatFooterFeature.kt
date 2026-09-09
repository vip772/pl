package h.Hchat.hooks.items.floatingchat

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
    private var installed = false
    private val footerBottomClass = "com.tencent.mm.pluginsdk.ui.chat.ChatFooterBottom"


    fun install(): Boolean {
        if (installed) return true
        val clazz = KavaReflector.loadClass(
            "com.tencent.mm.pluginsdk.ui.chat.ChatFooter",
            context.hostClassLoader()
        ) ?: return false
        var count = 0
        KavaReflector.findMethodRecursive(clazz, "onAttachedToWindow")?.let {
            count += hook(it) { param ->
                val footer = param.thisObject as? View ?: return@hook
                footer.postOnAnimation { apply(footer) }
            }
        }
        KavaReflector.findMethodRecursive(clazz, "onDetachedFromWindow")?.let {
            count += hook(it) { param -> restore(param.thisObject as? View) }
        }
        KavaReflector.findMethodRecursive(clazz, "onLayout", Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)?.let {
            count += hook(it) { param ->
                val footer = param.thisObject as? View ?: return@hook
                footer.postOnAnimation { apply(footer) }
            }
        }
        installed = count > 0
        if (!installed) HLog.e("[Hchat:$TAG] 未找到 ChatFooter 生命周期方法")
        return installed
    }

    private fun hook(method: Member, after: (XC_MethodHook.MethodHookParam) -> Unit): Int {
        if (!hooked.add(method)) return 1
        return runCatching {
            HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    runCatching { after(param) }.onFailure { HLog.e("[Hchat:$TAG] 输入框布局更新失败", it) }
                }
            })
            1
        }.getOrElse { hooked.remove(method); HLog.e("[Hchat:$TAG] Hook 安装失败", it); 0 }
    }

    private fun apply(footer: View) {
        if (!isEnabled()) { restoreAll(footer); return }
        if (!footer.isAttachedToWindow) return
        findAll(footer) { it.javaClass.name == footerBottomClass }.forEach { applyCard(it) }
    }

    private fun applyCard(view: View) {
        val params = view.layoutParams
        val marginParams = params as? ViewGroup.MarginLayoutParams
        val state = states[view] ?: FooterState(
            params,
            marginParams?.leftMargin,
            marginParams?.rightMargin,
            marginParams?.bottomMargin,
            view.paddingLeft,
            view.paddingTop,
            view.paddingRight,
            view.paddingBottom,
            view.elevation
        ).also { states[view] = it }
        if (marginParams != null) {
            val left = (state.leftMargin ?: marginParams.leftMargin) + dp(12)
            val right = (state.rightMargin ?: marginParams.rightMargin) + dp(12)
            val bottom = (state.bottomMargin ?: marginParams.bottomMargin) + dp(8)
            if (marginParams.leftMargin != left || marginParams.rightMargin != right || marginParams.bottomMargin != bottom) {
                marginParams.leftMargin = left
                marginParams.rightMargin = right
                marginParams.bottomMargin = bottom
                view.layoutParams = marginParams
            }
        }
        val elevation = dp(4).toFloat()
        if (view.elevation != elevation) view.elevation = elevation
        FloatingChatCardVisuals.apply(view, 24)
    }

    private fun restore(view: View?) {
        view ?: return
        restoreAll(view)
    }

    private fun restoreAll(root: View) {
        findAll(root) { states.containsKey(it) }.forEach { restoreCard(it) }
    }

    private fun restoreCard(view: View) {
        val state = states.remove(view) ?: return
        view.layoutParams?.let { params ->
            (params as? ViewGroup.MarginLayoutParams)?.let {
                state.leftMargin?.let { value -> it.leftMargin = value }
                state.rightMargin?.let { value -> it.rightMargin = value }
                state.bottomMargin?.let { value -> it.bottomMargin = value }
            }
            view.layoutParams = params
        }
        view.setPadding(state.paddingLeft, state.paddingTop, state.paddingRight, state.paddingBottom)
        view.elevation = state.elevation
        FloatingChatCardVisuals.restore(view)
    }

    fun destroy() { states.keys.toList().forEach(::restore) }
    private fun isEnabled() = context.configStore().getGlobalBoolean("${FloatingChatFooterFeature.ID}_enabled", false)
    private fun dp(value: Int) = (value * context.hostContext().resources.displayMetrics.density).roundToInt()
    private fun findAll(root: View, predicate: (View) -> Boolean): List<View> = buildList {
        fun walk(view: View) {
            if (predicate(view)) add(view)
            (view as? ViewGroup)?.let { group ->
                for (index in 0 until group.childCount) walk(group.getChildAt(index))
            }
        }
        walk(root)
    }
    private data class FooterState(
        val params: ViewGroup.LayoutParams?,
        val leftMargin: Int?,
        val rightMargin: Int?,
        val bottomMargin: Int?,
        val paddingLeft: Int,
        val paddingTop: Int,
        val paddingRight: Int,
        val paddingBottom: Int,
        val elevation: Float
    )
    companion object { private const val TAG = "悬浮输入框" }
}