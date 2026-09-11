package h.Hchat.hooks.items.floatingchat

import android.graphics.Outline
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.RelativeLayout
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
    private val headersByLayout = WeakHashMap<View, View>()
    private var reparentFailureReported = false
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
        if (!isEnabled()) {
            restore(layout)
            return
        }
        val header = headersByLayout[layout]?.takeIf { it.isAttachedToWindow }
            ?: find(layout) { it.javaClass.name == headerClass }?.also { headersByLayout[layout] = it }
            ?: return
        val state = states[header] ?: createState(header) ?: return
        if (!state.reparented && !reparentHeader(layout, header, state)) return
        val marginLp = header.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val originalMargins = state.originalLayoutParams as? ViewGroup.MarginLayoutParams
        val side = dp(SIDE_MARGIN_DP)
        val left = (originalMargins?.leftMargin ?: 0) + side
        val right = (originalMargins?.rightMargin ?: 0) + side
        val top = (originalMargins?.topMargin ?: 0) + dp(TOP_GAP_DP)
        if (marginLp.leftMargin != left || marginLp.rightMargin != right || marginLp.topMargin != top) {
            marginLp.leftMargin = left
            marginLp.rightMargin = right
            marginLp.topMargin = top
            header.layoutParams = marginLp
        }
        val elevation = dp(ELEVATION_DP).toFloat()
        if (header.elevation != elevation) header.elevation = elevation
        header.outlineProvider = FloatingHeaderOutlineProvider
        header.clipToOutline = true
        FloatingChatCardVisuals.apply(header, CORNER_RADIUS_DP)
        header.bringToFront()
        header.invalidateOutline()
        (header.parent as? View)?.invalidate()
    }

    private fun createState(header: View): HeaderState? {
        val parent = header.parent as? ViewGroup ?: return null
        val index = parent.indexOfChild(header)
        if (index < 0) return null
        val params = header.layoutParams ?: return null
        return HeaderState(
            originalParent = parent,
            originalIndex = index,
            originalLayoutParams = copyLayoutParams(params),
            elevation = header.elevation,
            outlineProvider = header.outlineProvider,
            clipToOutline = header.clipToOutline
        ).also { states[header] = it }
    }

    private fun reparentHeader(layout: ViewGroup, header: View, state: HeaderState): Boolean {
        if (header.parent !== layout) {
            if (!reparentFailureReported) {
                reparentFailureReported = true
                HLog.e(
                    "[Hchat:$TAG] 标题栏未重排: parent=${header.parent?.javaClass?.name} " +
                        "layout=${layout.javaClass.name}"
                )
            }
            return false
        }
        val root = layout.parent as? RelativeLayout
        if (root == null) {
            if (!reparentFailureReported) {
                reparentFailureReported = true
                HLog.e("[Hchat:$TAG] 标题栏未重排: 外层不是 RelativeLayout")
            }
            return false
        }
        return runCatching {
            val height = header.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
            layout.removeView(header)
            val params = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            root.addView(header, params)
            state.reparented = true
            HLog.i("[Hchat:$TAG] 标题栏结构重排完成: root=${root.javaClass.name}")
            true
        }.getOrElse {
            HLog.e("[Hchat:$TAG] 标题栏结构重排失败", it)
            false
        }
    }

    private fun restore(layout: ViewGroup) {
        headersByLayout.remove(layout)?.let(::restoreView)
    }
    private fun restoreView(view: View) {
        val state = states.remove(view) ?: return
        runCatching {
            if (state.reparented) {
                (view.parent as? ViewGroup)?.removeView(view)
                state.originalParent.addView(
                    view,
                    state.originalIndex.coerceAtMost(state.originalParent.childCount),
                    copyLayoutParams(state.originalLayoutParams)
                )
            } else {
                view.layoutParams = copyLayoutParams(state.originalLayoutParams)
            }
            view.elevation = state.elevation
            view.outlineProvider = state.outlineProvider
            view.clipToOutline = state.clipToOutline
            FloatingChatCardVisuals.restore(view)
            view.invalidateOutline()
        }.onFailure { HLog.e("[Hchat:$TAG] 恢复微信标题栏结构失败", it) }
    }
    fun destroy() {
        states.keys.toList().forEach(::restoreView)
        headersByLayout.clear()
    }
    private fun isEnabled() = context.configStore().getGlobalBoolean("${FloatingChatHeaderFeature.ID}_enabled", false)
    private fun dp(value: Int) = (value * context.hostContext().resources.displayMetrics.density).roundToInt()
    private fun find(root: View, predicate: (View) -> Boolean): View? = if (predicate(root)) root else (root as? ViewGroup)?.let { group -> (0 until group.childCount).asSequence().mapNotNull { find(group.getChildAt(it), predicate) }.firstOrNull() }
    private fun copyLayoutParams(params: ViewGroup.LayoutParams): ViewGroup.LayoutParams =
        when (params) {
            is RelativeLayout.LayoutParams -> RelativeLayout.LayoutParams(params)
            is ViewGroup.MarginLayoutParams -> ViewGroup.MarginLayoutParams(params)
            else -> ViewGroup.LayoutParams(params)
        }
    private data class HeaderState(
        val originalParent: ViewGroup,
        val originalIndex: Int,
        val originalLayoutParams: ViewGroup.LayoutParams,
        val elevation: Float = 0f,
        val outlineProvider: ViewOutlineProvider?,
        val clipToOutline: Boolean,
        var reparented: Boolean = false
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
    companion object {
        private const val TAG = "悬浮标题栏"
        private const val SIDE_MARGIN_DP = 12
        private const val TOP_GAP_DP = 4
        private const val ELEVATION_DP = 4
        private const val CORNER_RADIUS_DP = 24
    }
}