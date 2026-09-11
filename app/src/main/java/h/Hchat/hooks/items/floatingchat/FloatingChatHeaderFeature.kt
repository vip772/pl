package h.Hchat.hooks.items.floatingchat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.RelativeLayout

import de.robv.android.xposed.XC_MethodHook
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.HLog
import h.Hchat.utils.KavaReflector
import java.lang.reflect.Member
import java.lang.reflect.Method
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
    private val headerZoneStates = WeakHashMap<View, HeaderZoneState>()
    private val wrapperStates = WeakHashMap<View, WrapperState>()
    private val windowOverlayStates = WeakHashMap<View, Boolean>()
    private val contentCardTranslations = WeakHashMap<View, Float>()
    private val windowStates = WeakHashMap<Activity, WindowState>()
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
        applyTopTransparency(layout)
        val header = headersByLayout[layout]?.takeIf { it.isAttachedToWindow }
            ?: find(layout) { it.javaClass.name == headerClass }?.also { headersByLayout[layout] = it }
            ?: find(layout.rootView) { it.javaClass.name == headerClass }?.also { headersByLayout[layout] = it }
            ?: return
        val state = states[header] ?: createState(header) ?: return
        if (isWindowBar(header, layout)) {
            enableWindowOverlay(header)
        } else if (!state.reparented && !reparentHeader(layout, header, state)) return
        val marginLp = header.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val side = dp(SIDE_MARGIN_DP)
        val left = side
        val right = side
        val top = if (isWindowBar(header, layout)) {
            statusBarInset(layout) + dp(TOP_GAP_DP)
        } else {
            state.topOffset + dp(TOP_GAP_DP)
        }
        if (marginLp.leftMargin != left || marginLp.rightMargin != right || marginLp.topMargin != top) {
            marginLp.leftMargin = left
            marginLp.rightMargin = right
            marginLp.topMargin = top
            header.layoutParams = marginLp
        }
        applyHeaderZoneCards(layout, header)
        applyContentZoneCards(layout, header)
        clearStatusBarWrapper(layout)
        val elevation = dp(ELEVATION_DP).toFloat()
        if (header.elevation != elevation) header.elevation = elevation
        header.outlineProvider = FloatingHeaderOutlineProvider
        header.clipToOutline = true
        FloatingChatCardVisuals.apply(header, CORNER_RADIUS_DP)
        header.bringToFront()
        header.invalidateOutline()
        (header.parent as? View)?.invalidate()
    }

    private fun isWindowBar(header: View, layout: View): Boolean {
        var current = header.parent as? View
        while (current != null && current !== layout) {
            if (current.javaClass.name == "androidx.appcompat.widget.ActionBarOverlayLayout") return true
            current = current.parent as? View
        }
        return false
    }

    private fun enableWindowOverlay(header: View) {
        var current = header.parent as? View
        while (current != null) {
            if (current.javaClass.name == "androidx.appcompat.widget.ActionBarOverlayLayout") {
                if (windowOverlayStates[current] != true) {
                    runCatching {
                        current.javaClass.getMethod("setOverlayMode", Boolean::class.javaPrimitiveType).invoke(current, true)
                        windowOverlayStates[current] = true
                    }
                }
                return
            }
            current = current.parent as? View
        }
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
            val original = header.layoutParams
            val height = original?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
            val rootLocation = IntArray(2).also(root::getLocationOnScreen)
            val layoutLocation = IntArray(2).also(layout::getLocationOnScreen)
            state.topOffset = layoutLocation[1] + layout.paddingTop - rootLocation[1]
            layout.removeView(header)
            val params = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            params.leftMargin = 0
            params.rightMargin = 0
            params.topMargin = 0
            root.addView(header, params)
            state.reparented = true
            HLog.i("[Hchat:$TAG] 标题栏结构重排完成: root=${root.javaClass.name}")
            true
        }.getOrElse {
            HLog.e("[Hchat:$TAG] 标题栏结构重排失败", it)
            false
        }
    }

    private fun applyHeaderZoneCards(layout: ViewGroup, header: View) {
        val hostIndex = (0 until layout.childCount).firstOrNull { index ->
            find(layout.getChildAt(index)) { view ->
                view.javaClass.name == "com.tencent.mm.pluginsdk.ui.chat.ChattingScrollLayout"
            } != null
        } ?: return
        val density = layout.resources.displayMetrics.density
        val side = (SIDE_MARGIN_DP * density).roundToInt()
        val gap = (CARD_GAP_DP * density).roundToInt()
        val state = headerZoneStates.getOrPut(layout) { HeaderZoneState() }
        var first = true
        for (index in 0 until hostIndex) {
            val child = layout.getChildAt(index)
            if (child === header || child.visibility != View.VISIBLE) continue
            val lp = child.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
            if (!state.originalMargins.containsKey(child)) {
                state.originalMargins[child] = intArrayOf(lp.leftMargin, lp.topMargin, lp.rightMargin, lp.bottomMargin)
            }
            val top = if (first) header.height + dp(TOP_GAP_DP) + gap else gap
            first = false
            if (lp.leftMargin != side || lp.rightMargin != side || lp.topMargin != top) {
                lp.leftMargin = side
                lp.rightMargin = side
                lp.topMargin = top
                child.layoutParams = lp
            }
            FloatingChatCardVisuals.apply(child, CARD_CORNER_RADIUS_DP)
        }
    }

    private fun applyContentZoneCards(layout: View, header: View) {
        val content = find(layout) { it.javaClass.name == CHATTING_CONTENT_CLASS } as? ViewGroup ?: return
        val density = layout.resources.displayMetrics.density
        val gap = (CARD_GAP_DP * density).roundToInt()
        val contentLocation = IntArray(2).also(content::getLocationOnScreen)
        val headerLocation = IntArray(2).also(header::getLocationOnScreen)
        val targetTop = (headerLocation[1] - contentLocation[1] + header.height + gap).coerceAtLeast(0)
        var nextTop = targetTop
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            if (child.visibility != View.VISIBLE || child.height <= 0 || !isFloatingContentCard(child)) continue
            val lp = child.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
            val baseTranslation = contentCardTranslations.getOrPut(child) { child.translationY }
            val currentTop = child.top + baseTranslation.roundToInt()
            val delta = nextTop - currentTop
            if (delta != 0) child.translationY = baseTranslation + delta
            FloatingChatCardVisuals.apply(child, CARD_CORNER_RADIUS_DP)
            nextTop += child.height + gap
        }
    }

    private fun isFloatingContentCard(view: View): Boolean {
        if (view.javaClass.name == CHATTING_SCROLL_LAYOUT_CLASS) return false
        if (find(view) { it.javaClass.name == CHATTING_SCROLL_LAYOUT_CLASS } != null) return false
        if (view.javaClass.name == "android.view.ViewStub") return false
        if (view is FrameLayout && view.width > 0 && view.height > 0) return true
        return view.javaClass.name.contains("Tips", true) ||
            view.javaClass.name.contains("Notice", true) ||
            view.javaClass.name.contains("File", true)
    }

    private fun clearStatusBarWrapper(layout: View) {
        var current: View? = layout
        while (current != null) {
            if (current.javaClass.name == EDGE_TO_EDGE_WRAPPER_CLASS) {
                val state = wrapperStates.getOrPut(current) {
                    WrapperState(current.background, current.paddingLeft, current.paddingTop, current.paddingRight, current.paddingBottom)
                }
                current.setBackgroundColor(Color.TRANSPARENT)
                runCatching {
                    val setter = findMethod(current.javaClass, "setStatusBarStrategy", 1)
                    val strategy = setter?.parameterTypes?.singleOrNull()?.enumConstants
                        ?.firstOrNull { (it as Enum<*>).name == "ALWAYS_HIDE" }
                    if (setter != null && strategy != null) setter.invoke(current, strategy)
                    findMethod(current.javaClass, "setStatusBarColor", 1)
                        ?.takeIf { it.parameterTypes[0] == Int::class.javaPrimitiveType }
                        ?.invoke(current, Color.TRANSPARENT)
                    findMethod(current.javaClass, "setStatusBarBackgroundColor", 1)
                        ?.takeIf { it.parameterTypes[0] == Int::class.javaPrimitiveType }
                        ?.invoke(current, Color.TRANSPARENT)
                    if (current.paddingTop != 0) {
                        current.setPadding(current.paddingLeft, 0, current.paddingRight, current.paddingBottom)
                    }
                    current.requestApplyInsets()
                }
                state.applied = true
                return
            }
            current = current.parent as? View
        }
    }

    private fun restoreWrappers() {
        wrapperStates.forEach { (view, state) ->
            if (!state.applied) return@forEach
            view.background = state.background
            view.setPadding(state.left, state.top, state.right, state.bottom)
            runCatching {
                view.javaClass.methods.firstOrNull {
                    it.name == "setStatusBarBackgroundColor" && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
                }?.invoke(view, state.backgroundColor)
            }
        }
        wrapperStates.clear()
    }

    private fun restore(layout: ViewGroup) {
        headersByLayout.remove(layout)?.let(::restoreView)
        headerZoneStates.remove(layout)?.originalMargins?.forEach { (view, margins) ->
            val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return@forEach
            lp.leftMargin = margins[0]
            lp.topMargin = margins[1]
            lp.rightMargin = margins[2]
            lp.bottomMargin = margins[3]
            view.layoutParams = lp
            FloatingChatCardVisuals.restore(view)
        }
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
        headerZoneStates.keys.toList().forEach { layout -> restore(layout as? ViewGroup ?: return@forEach) }
        headersByLayout.clear()
        restoreWrappers()
        contentCardTranslations.forEach { (view, translation) -> view.translationY = translation }
        contentCardTranslations.clear()
        restoreWindowStates()
    }
    private fun applyTopTransparency(layout: View) {
        val activity = layout.context.activityOrNull() ?: return
        val state = windowStates.getOrPut(activity) {
            val window = activity.window
            WindowState(window.decorView.systemUiVisibility, window.statusBarColor)
        }
        val window = activity.window
        window.decorView.systemUiVisibility =
            window.decorView.systemUiVisibility or TOP_IMMERSIVE_FLAGS
        window.statusBarColor = Color.TRANSPARENT
        state.applied = true
    }

    private fun restoreWindowStates() {
        windowStates.forEach { (activity, state) ->
            if (!state.applied) return@forEach
            val window = activity.window
            window.decorView.systemUiVisibility =
                (window.decorView.systemUiVisibility and TOP_IMMERSIVE_FLAGS.inv()) or
                    (state.systemUiVisibility and TOP_IMMERSIVE_FLAGS)
            if (window.statusBarColor == Color.TRANSPARENT) {
                window.statusBarColor = state.statusBarColor
            }
        }
        windowStates.clear()
    }

    private fun Context.activityOrNull(): Activity? {
        var current: Context? = this
        val visited = HashSet<Context>()
        while (current != null && visited.add(current)) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return null
    }

    private fun findMethod(type: Class<*>, name: String, parameterCount: Int): Method? {
        var current: Class<*>? = type
        while (current != null) {
            current.declaredMethods.firstOrNull { it.name == name && it.parameterTypes.size == parameterCount }?.let {
                it.isAccessible = true
                return it
            }
            current = current.superclass
        }
        return null
    }

    private fun statusBarInset(layout: View): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
        return layout.rootWindowInsets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0
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
    private data class WindowState(
        val systemUiVisibility: Int,
        val statusBarColor: Int,
        var applied: Boolean = false
    )

    private data class HeaderZoneState(
        val originalMargins: MutableMap<View, IntArray> = WeakHashMap()
    )

    private data class WrapperState(
        val background: Drawable?,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val backgroundColor: Int = (background as? android.graphics.drawable.ColorDrawable)?.color ?: Color.TRANSPARENT,
        var applied: Boolean = false
    )

    private data class HeaderState(
        val originalParent: ViewGroup,
        val originalIndex: Int,
        val originalLayoutParams: ViewGroup.LayoutParams,
        val elevation: Float = 0f,
        val outlineProvider: ViewOutlineProvider?,
        val clipToOutline: Boolean,
        var topOffset: Int = 0,
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
        private const val TOP_IMMERSIVE_FLAGS =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        private const val SIDE_MARGIN_DP = 12
        private const val TOP_GAP_DP = 4
        private const val CARD_GAP_DP = 8
        private const val CARD_CORNER_RADIUS_DP = 24
        private const val EDGE_TO_EDGE_WRAPPER_CLASS = "com.tencent.mm.ui.widget.EdgeToEdgeWrapperLayout"
        private const val CHATTING_CONTENT_CLASS = "com.tencent.mm.pluginsdk.ui.chat.ChattingContent"
        private const val CHATTING_SCROLL_LAYOUT_CLASS = "com.tencent.mm.pluginsdk.ui.chat.ChattingScrollLayout"
        private const val ELEVATION_DP = 4
        private const val CORNER_RADIUS_DP = 24
    }
}