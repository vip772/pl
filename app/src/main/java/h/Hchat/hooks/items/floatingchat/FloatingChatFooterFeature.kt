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
    private val reportedCallbacks = HashSet<String>()
    private var applyReported = false
    private var scanReported = false
    private var targetReported = false
    private var appliedReported = false
    private var verifyScheduled = false
    private var installed = false
    private val footerBottomClass = "com.tencent.mm.pluginsdk.ui.chat.ChatFooterBottom"


    fun install(): Boolean {
        if (installed) return true
        val targetClass = "com.tencent.mm.pluginsdk.ui.chat.ChatFooter"
        val clazz = KavaReflector.loadClass(targetClass, context.hostClassLoader())
        if (clazz == null) {
            HLog.e("[Hchat:$TAG] 功能未生效: 未找到目标类 $targetClass")
            return false
        }
        if (!View::class.java.isAssignableFrom(clazz)) {
            HLog.e("[Hchat:$TAG] 功能未生效: 目标类不是 View: $targetClass")
            return false
        }
        var count = 0
        KavaReflector.findMethodRecursive(clazz, "onAttachedToWindow")?.let { method ->
            count += hook(method, clazz) { footer ->
                reportCallbackOnce(method, footer)
                footer.postOnAnimation { apply(footer) }
            }
        }
        KavaReflector.findMethodRecursive(clazz, "onDetachedFromWindow")?.let { method ->
            count += hook(method, clazz) { footer ->
                reportCallbackOnce(method, footer)
                restore(footer)
            }
        }
        KavaReflector.findMethodRecursive(clazz, "onLayout", Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)?.let { method ->
            count += hook(method, clazz) { footer ->
                reportCallbackOnce(method, footer)
                footer.postOnAnimation { apply(footer) }
            }
        }
        installed = count > 0
        if (!installed) HLog.e("[Hchat:$TAG] 功能未生效: 未找到 ChatFooter 生命周期方法")
        return installed
    }

    private fun hook(targetMethod: Member, targetClass: Class<*>, after: (View) -> Unit): Int {
        if (!hooked.add(targetMethod)) return 1
        return runCatching {
            HLog.i(
                "[Hchat:$TAG] 诊断: 安装入口 method=${targetMethod.name} " +
                    "declaringClass=${targetMethod.declaringClass.name} targetClass=${targetClass.name}"
            )
            HookRegistry.get().hook(targetMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val receiver = param.thisObject
                    if (!targetClass.isInstance(receiver)) return
                    val footer = receiver as? View ?: return
                    runCatching { after(footer) }
                        .onFailure { HLog.e("[Hchat:$TAG] 输入框布局更新失败", it) }
                }
            })
            1
        }.getOrElse { hooked.remove(targetMethod); HLog.e("[Hchat:$TAG] Hook 安装失败", it); 0 }
    }
    private fun reportCallbackOnce(method: Member, footer: View) {
        val methodName = method.name
        if (!reportedCallbacks.add(methodName)) return
        HLog.i(
            "[Hchat:$TAG] 诊断: Hook 回调首次命中 method=$methodName " +
                "instance=${footer.javaClass.name}"
        )
    }
    private fun apply(footer: View) {
        val enabled = isEnabled()
        val attached = footer.isAttachedToWindow
        if (!applyReported) {
            applyReported = true
            HLog.i("[Hchat:$TAG] 诊断: 首次执行布局处理 enabled=$enabled attached=$attached")
        }
        if (!enabled) { restoreAll(footer); return }
        if (!attached) return
        val allViews = findAll(footer) { true }
        val targets = allViews.filter { it.javaClass.name == footerBottomClass }
        if (!scanReported) {
            scanReported = true
            HLog.i(
                "[Hchat:$TAG] 诊断: View 扫描完成 root=${footer.javaClass.name} " +
                    "viewCount=${allViews.size} target=$footerBottomClass targetCount=${targets.size}"
            )
        }
        targets.forEach { applyCard(it) }
    }
    private fun applyCard(view: View) {
        val params = view.layoutParams
        val marginParams = params as? ViewGroup.MarginLayoutParams
        val parent = view.parent as? ViewGroup
        if (!targetReported) {
            targetReported = true
            HLog.i(
                "[Hchat:$TAG] 诊断: 目标 View 首次命中 class=${view.javaClass.name} " +
                    "parent=${parent?.javaClass?.name ?: "<none>"} " +
                    "layoutParams=${params?.javaClass?.name ?: "<none>"} " +
                    "size=${view.width}x${view.height} visibility=${view.visibility} " +
                    "background=${view.background?.javaClass?.name ?: "<none>"} " +
                    "clipChildren=${parent?.clipChildren} clipToPadding=${parent?.clipToPadding}"
            )
        }
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
        val beforeLeft = marginParams?.leftMargin
        val beforeRight = marginParams?.rightMargin
        val beforeBottom = marginParams?.bottomMargin
        val beforeElevation = view.elevation
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
        if (!appliedReported) {
            appliedReported = true
            val appliedMargins = view.layoutParams as? ViewGroup.MarginLayoutParams
            HLog.i(
                "[Hchat:$TAG] 诊断: 样式首次应用 " +
                    "margin=$beforeLeft,$beforeRight,$beforeBottom->" +
                    "${appliedMargins?.leftMargin},${appliedMargins?.rightMargin},${appliedMargins?.bottomMargin} " +
                    "elevation=$beforeElevation->${view.elevation} " +
                    "backgroundAfter=${view.background?.javaClass?.name ?: "<none>"}"
            )
        }
        scheduleVerification(view)
    }
    private fun scheduleVerification(view: View) {
        if (verifyScheduled) return
        verifyScheduled = true
        val expected = view.layoutParams as? ViewGroup.MarginLayoutParams
        val expectedLeft = expected?.leftMargin
        val expectedRight = expected?.rightMargin
        val expectedBottom = expected?.bottomMargin
        val expectedElevation = view.elevation
        val expectedBackground = view.background
        view.postOnAnimation {
            val current = view.layoutParams as? ViewGroup.MarginLayoutParams
            val marginsKept = current?.leftMargin == expectedLeft &&
                current?.rightMargin == expectedRight &&
                current?.bottomMargin == expectedBottom
            HLog.i(
                "[Hchat:$TAG] 诊断: 下一帧复查 attached=${view.isAttachedToWindow} " +
                    "size=${view.width}x${view.height} marginsKept=$marginsKept " +
                    "elevationKept=${view.elevation == expectedElevation} " +
                    "backgroundKept=${view.background === expectedBackground}"
            )
        }
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