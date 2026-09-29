package t.mma.hooks.items.floatingchat
import t.mma.utils.HLog

import android.content.Context
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
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView

import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import HLog
import t.mma.utils.KavaReflector
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
    private val windowOverlayStates = WeakHashMap<View, Boolean>()
    private val statusWrapperBackgrounds = WeakHashMap<View, Drawable?>()
    private val statusWrapperColors = WeakHashMap<View, Int?>()
    private val statusWrappersByLayout = WeakHashMap<ViewGroup, View>()
    private val contentCardTranslations = WeakHashMap<View, Float>()

    private val diagnosedLayouts = WeakHashMap<View, Boolean>()
    private val diagnosedStatusWrappers = WeakHashMap<View, Boolean>()
    private val tipsGroups = WeakHashMap<View, View>()
    private val tipsAttachListeners = WeakHashMap<View, View.OnAttachStateChangeListener>()
    private val tipsLayoutListeners = WeakHashMap<View, View.OnLayoutChangeListener>()
    private val diagnosedTipsGroups = WeakHashMap<View, Boolean>()
    private val tipsCardStates = WeakHashMap<View, TipsCardState>()
    private val tipsDimStates = WeakHashMap<View, TipsDimState>()
    private val tipsGroupElevations = WeakHashMap<View, Float>()
    private val tipsRecyclerMargins = WeakHashMap<View, IntArray>()
    private val tipsRecyclerRules = WeakHashMap<View, IntArray>()
    private val tipsRecyclerBackgrounds = WeakHashMap<View, Drawable?>()
    private val tipsContentBackgrounds = WeakHashMap<View, Drawable?>()
    private val layoutTrackers = WeakHashMap<ViewGroup, HeaderLayoutTracker>()
    private val tipsCardBottoms = WeakHashMap<ViewGroup, Int>()
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
        KavaReflector.findMethodRecursive(clazz, "onAttachedToWindow")?.let {
            count += hook(it) { param ->
                (param.thisObject as? ViewGroup)?.let { layout ->
                    scheduleApply(layout)
                }
            }
        }
        KavaReflector.findMethodRecursive(clazz, "onDetachedFromWindow")?.let {
            count += hook(it) { param ->
                (param.thisObject as? ViewGroup)?.let { layout ->
                    disposeLayoutTracker(layout)
                }
            }
        }
        KavaReflector.findMethodRecursive(clazz, "onLayout", Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)?.let {
            count += hook(it) { param -> (param.thisObject as? ViewGroup)?.let(::scheduleApply) }
        }
        KavaReflector.findMethodRecursive(clazz, "fitSystemWindows", Rect::class.java)?.let {
            count += hook(it) { param -> (param.thisObject as? ViewGroup)?.let(::scheduleApply) }
        }
        installTipsGroupTracking()?.let { count += it }
        installed = count > 0
        if (!installed) HLog.e("[Hchat:$TAG] 功能未生效: 未找到 ChattingUILayout 生命周期方法")
        return installed
    }

    private fun installTipsGroupTracking(): Int? {
        val clazz = KavaReflector.loadClass(TIPS_BAR_GROUP_CLASS, context.hostClassLoader()) ?: return null
        val constructors = clazz.declaredConstructors.filter { ctor ->
            ctor.parameterTypes.firstOrNull() == Context::class.java
        }
        if (constructors.isEmpty()) return null
        var count = 0
        constructors.forEach { constructor ->
            count += hook(constructor) { param ->
                val group = param.thisObject as? View ?: return@hook
                ensureTipsAttachListener(group)
            }
        }
        return count
    }

    private fun ensureTipsAttachListener(group: View) {
        if (tipsAttachListeners[group] != null) return
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                val sourceLayout = findAncestorLayout(view) ?: visibleChatLayoutFromRoot(view) ?: return
                val layout = visibleChatLayout(sourceLayout) ?: return
                tipsGroups[layout] = view
                scheduleApply(layout)
            }
            override fun onViewDetachedFromWindow(view: View) {
                tipsGroups.entries.removeAll { it.value === view }
            }
        }
        tipsAttachListeners[group] = listener
        group.addOnAttachStateChangeListener(listener)
        val layoutListener = View.OnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (left == oldLeft && top == oldTop && right == oldRight && bottom == oldBottom) return@OnLayoutChangeListener
            val sourceLayout = findAncestorLayout(view) ?: visibleChatLayoutFromRoot(view) ?: return@OnLayoutChangeListener
            val layout = visibleChatLayout(sourceLayout) ?: return@OnLayoutChangeListener
            tipsGroups[layout] = view
            scheduleApply(layout)
        }
        tipsLayoutListeners[group] = layoutListener
        group.addOnLayoutChangeListener(layoutListener)
        if (group.isAttachedToWindow) listener.onViewAttachedToWindow(group)
    }

    private fun findAncestorLayout(view: View): ViewGroup? {
        var current = view.parent as? View
        while (current != null) {
            if (current.javaClass.name == layoutClass && current is ViewGroup) return current
            current = current.parent as? View
        }
        return null
    }

    private fun visibleChatLayoutFromRoot(view: View): ViewGroup? {
        val root = view.rootView
        val source = find(root) { it.javaClass.name == layoutClass } as? ViewGroup ?: return null
        return visibleChatLayout(source)
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

    private fun scheduleApply(layout: ViewGroup) {
        val tracker = layoutTrackers.getOrPut(layout) { HeaderLayoutTracker() }
        if (tracker.preDraw != null || !layout.isAttachedToWindow) return
        val observer = layout.viewTreeObserver
        val listener = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (observer.isAlive) observer.removeOnPreDrawListener(this)
                if (tracker.preDraw === this) tracker.preDraw = null
                if (layout.isAttachedToWindow && layout.isShown) apply(layout)
                return true
            }
        }
        tracker.preDraw = listener
        observer.addOnPreDrawListener(listener)
    }

    private fun disposeLayoutTracker(layout: ViewGroup) {
        layoutTrackers.remove(layout)?.preDraw?.let { listener ->
            runCatching { layout.viewTreeObserver.removeOnPreDrawListener(listener) }
        }
        tipsCardBottoms.remove(layout)
    }

    private fun apply(sourceLayout: ViewGroup) {
        if (!isEnabled()) {
            restore(sourceLayout)
            visibleChatLayout(sourceLayout)?.takeIf { it !== sourceLayout }?.let(::restore)
            return
        }
        val layout = visibleChatLayout(sourceLayout) ?: return
        applyChatStatusWrapper(layout)
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
        val top = statusBarInset(layout) + dp(TOP_GAP_DP)
        if (marginLp.leftMargin != left || marginLp.rightMargin != right || marginLp.topMargin != top) {
            marginLp.leftMargin = left
            marginLp.rightMargin = right
            marginLp.topMargin = top
            header.layoutParams = marginLp
        }
        applyHeaderZoneCards(layout, header)
        tipsCardBottoms.remove(layout)
        applyTrackedTipsGroup(layout, header)
        applyContentZoneCards(layout, header)
        diagnoseHeaderLayout(layout, header)
        val elevation = dp(ELEVATION_DP).toFloat()
        if (header.elevation != elevation) header.elevation = elevation
        header.outlineProvider = FloatingHeaderOutlineProvider
        header.clipToOutline = true
        FloatingChatCardVisuals.apply(header, CORNER_RADIUS_DP)
        header.bringToFront()
        header.invalidateOutline()
        (header.parent as? View)?.invalidate()
    }

    private fun visibleChatLayout(source: ViewGroup): ViewGroup? {
        val screenWidth = source.resources.displayMetrics.widthPixels
        val candidates = ArrayList<ViewGroup>()
        collect(source.rootView) { view ->
            if (view.javaClass.name == layoutClass && view is ViewGroup) candidates += view
        }
        return candidates.firstOrNull { layout ->
            if (!layout.isShown || layout.width <= 0 || layout.height <= 0) return@firstOrNull false
            val location = IntArray(2).also(layout::getLocationOnScreen)
            location[0] < screenWidth && location[0] + layout.width > 0
        }
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

    private fun applyTrackedTipsGroup(layout: View, header: View) {
        val group = tipsGroups[layout]?.takeIf {
            it.isAttachedToWindow && it.visibility == View.VISIBLE && it.width > 0 && it.height > 0
        }
            ?: tipsGroups.entries.firstOrNull { (_, view) ->
                view.isAttachedToWindow && view.visibility == View.VISIBLE &&
                    view.width > 0 && view.height > 0 && findAncestorLayout(view) === layout
            }?.value
            ?: return
        val headerLocation = IntArray(2).also(header::getLocationOnScreen)
        val groupLocation = IntArray(2).also(group::getLocationOnScreen)
        val base = contentCardTranslations.getOrPut(group) { group.translationY }
        val baseTop = groupLocation[1] - group.translationY.roundToInt() + base.roundToInt()
        val targetTop = headerLocation[1] + header.height + dp(CARD_GAP_DP)
        group.translationY = base + targetTop - baseTop
        val layoutGroup = layout as? ViewGroup
        if (layoutGroup != null) {
            val layoutLocation = IntArray(2).also(layoutGroup::getLocationOnScreen)
            tipsCardBottoms[layoutGroup] = targetTop - layoutLocation[1] + group.height
        }
        val lp = group.layoutParams as? ViewGroup.MarginLayoutParams
        val side = dp(SIDE_MARGIN_DP)
        if (lp != null && (lp.leftMargin != side || lp.rightMargin != side)) {
            lp.leftMargin = side
            lp.rightMargin = side
            group.layoutParams = lp
        }
        suppressTipsGroupDim(group)
        if (!tipsGroupElevations.containsKey(group)) tipsGroupElevations[group] = group.elevation
        group.elevation = 0f
        styleTipsCardBody(group)
        group.bringToFront()
        if (diagnosedTipsGroups.put(group, true) == null) {
            HLog.i(
                "[Hchat:$TAG] 公告卡定位: class=${group.javaClass.name} " +
                    "from=${groupLocation[0]},${groupLocation[1]} targetTop=$targetTop " +
                    "size=${group.width}x${group.height} parent=${group.parent?.javaClass?.name}"
            )
        }
    }

    private fun suppressTipsGroupDim(group: View) {
        collect(group) { view ->
            if (view === group || view.javaClass.name != "android.view.View") return@collect
            val lp = view.layoutParams ?: return@collect
            if (lp.width != ViewGroup.LayoutParams.MATCH_PARENT ||
                lp.height != ViewGroup.LayoutParams.MATCH_PARENT
            ) return@collect
            if (!tipsDimStates.containsKey(view)) {
                tipsDimStates[view] = TipsDimState(view.visibility, view.alpha, view.background)
            }
            view.visibility = View.GONE
            view.alpha = 0f
            view.background = null
        }
    }

    private fun styleTipsCardBody(group: View) {
        val body = find(group) { it !== group && it is RelativeLayout } ?: return
        if (!tipsCardStates.containsKey(body)) {
            tipsCardStates[body] = TipsCardState(
                body.elevation,
                body.outlineProvider,
                body.clipToOutline,
                body.paddingLeft,
                body.paddingTop,
                body.paddingRight,
                body.paddingBottom
            )
        }
        FloatingChatCardVisuals.apply(body, CARD_CORNER_RADIUS_DP)
        body.outlineProvider = FloatingTipsCardOutlineProvider
        body.clipToOutline = true
        body.elevation = 0f
        val recycler = find(group) { it.javaClass.name == TIPS_RECYCLER_CLASS }
        val recyclerLp = recycler?.layoutParams as? RelativeLayout.LayoutParams
        if (recycler != null && recyclerLp != null) {
            if (!tipsRecyclerBackgrounds.containsKey(recycler)) {
                tipsRecyclerBackgrounds[recycler] = recycler.background
            }
            recycler.background = null
            val recyclerGroup = recycler as? ViewGroup
            if (recyclerGroup != null) {
                for (index in 0 until recyclerGroup.childCount) {
                    clearTipsItemBackground(recyclerGroup.getChildAt(index))
                }
            }
            if (!tipsRecyclerMargins.containsKey(recycler)) {
                tipsRecyclerRules[recycler] = recyclerLp.rules.copyOf()
            }
            if (!tipsRecyclerMargins.containsKey(recycler)) {
                tipsRecyclerMargins[recycler] = intArrayOf(
                    recyclerLp.leftMargin,
                    recyclerLp.topMargin,
                    recyclerLp.rightMargin,
                    recyclerLp.bottomMargin
                )
            }
            val rules = tipsRecyclerRules[recycler]
            if (rules != null) {
                for (verb in recyclerLp.rules.indices) recyclerLp.removeRule(verb)
                recyclerLp.addRule(RelativeLayout.CENTER_VERTICAL)
                recycler.layoutParams = recyclerLp
            }
            val originalMargins = tipsRecyclerMargins[recycler]
            if (originalMargins != null && originalMargins[1] != originalMargins[3]) {
                val vertical = (originalMargins[1] + originalMargins[3]) / 2
                recyclerLp.topMargin = vertical
                recyclerLp.bottomMargin = vertical
                recycler.layoutParams = recyclerLp
            }
        }
        val original = tipsCardStates[body]
        if (original != null && original.paddingTop != original.paddingBottom) {
            val vertical = (original.paddingTop + original.paddingBottom) / 2
            body.setPadding(original.paddingLeft, vertical, original.paddingRight, vertical)
        }
        body.invalidateOutline()
    }

    private fun clearTipsItemBackground(item: View) {
        if (item.background != null && !tipsContentBackgrounds.containsKey(item)) {
            tipsContentBackgrounds[item] = item.background
            item.background = null
        }
        val group = item as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            if (child is TextView || child.javaClass.name == WE_IMAGE_VIEW_CLASS) continue
            if (child.background != null && !tipsContentBackgrounds.containsKey(child)) {
                tipsContentBackgrounds[child] = child.background
                child.background = null
            }
        }
    }

    private fun findVisibleChatContent(layout: View, header: View): ViewGroup? {
        val headerLocation = IntArray(2).also(header::getLocationOnScreen)
        val screenWidth = layout.resources.displayMetrics.widthPixels
        val root = layout.rootView
        val candidates = ArrayList<ViewGroup>()
        collect(root) { view ->
            if (view.javaClass.name == CHATTING_CONTENT_CLASS && view is ViewGroup) candidates += view
        }
        return candidates.firstOrNull { content ->
            if (!content.isShown || content.width <= 0 || content.height <= 0) return@firstOrNull false
            val location = IntArray(2).also(content::getLocationOnScreen)
            val overlapsHeader = location[0] < headerLocation[0] + header.width &&
                location[0] + content.width > headerLocation[0]
            val onScreen = location[0] < screenWidth && location[0] + content.width > 0
            overlapsHeader && onScreen
        }
    }

    private fun collect(root: View, visitor: (View) -> Unit) {
        visitor(root)
        val group = root as? ViewGroup ?: return
        for (index in 0 until group.childCount) collect(group.getChildAt(index), visitor)
    }

    private fun applyContentZoneCards(layout: View, header: View) {
        val content = findVisibleChatContent(layout, header) ?: return
        val density = layout.resources.displayMetrics.density
        val gap = (CARD_GAP_DP * density).roundToInt()
        val contentLocation = IntArray(2).also(content::getLocationOnScreen)
        val headerLocation = IntArray(2).also(header::getLocationOnScreen)
        val targetTop = (headerLocation[1] - contentLocation[1] + header.height + gap).coerceAtLeast(0)
        var nextTop = targetTop
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            if (child.visibility != View.VISIBLE || child.height <= 0 || !isFloatingContentCard(child, content)) continue
            val baseTranslation = contentCardTranslations.getOrPut(child) { child.translationY }
            val currentTop = child.top + baseTranslation.roundToInt()
            val delta = nextTop - currentTop
            if (delta != 0) child.translationY = baseTranslation + delta
            FloatingChatCardVisuals.apply(child, CARD_CORNER_RADIUS_DP)
            nextTop += child.height + gap
        }
        val layoutGroup = layout as? ViewGroup
        val layoutLocation = IntArray(2).also(layout::getLocationOnScreen)
        val tipsBottomInContent = layoutGroup?.let { tipsCardBottoms[it] }
            ?.let { it + layoutLocation[1] - contentLocation[1] + gap }
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            if (!isTopNewMessageChip(child)) continue
            val chipTop = maxOf(nextTop, tipsBottomInContent ?: 0)
            placeContentView(child, content, chipTop)
            nextTop = chipTop + child.height + gap
        }
    }

    private fun placeContentView(view: View, content: View, targetTop: Int) {
        val contentLocation = IntArray(2).also(content::getLocationOnScreen)
        val viewLocation = IntArray(2).also(view::getLocationOnScreen)
        val baseTranslation = contentCardTranslations.getOrPut(view) { view.translationY }
        val currentTop = viewLocation[1] - contentLocation[1] - view.translationY.roundToInt() +
            baseTranslation.roundToInt()
        view.translationY = baseTranslation + targetTop - currentTop
    }

    private fun isTopNewMessageChip(view: View): Boolean {
        if (view.visibility != View.VISIBLE || !isNewMessageChip(view)) return false
        val lp = view.layoutParams as? FrameLayout.LayoutParams ?: return false
        return lp.gravity and android.view.Gravity.VERTICAL_GRAVITY_MASK == android.view.Gravity.TOP
    }

    private fun isNewMessageChip(view: View): Boolean {
        val group = view as? LinearLayout ?: return false
        var hasText = false
        var hasIcon = false
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            if (child is TextView) hasText = true
            if (child.javaClass.name == WE_IMAGE_VIEW_CLASS) hasIcon = true
        }
        return hasText && hasIcon
    }

    private fun isFloatingContentCard(view: View, content: ViewGroup): Boolean {
        if (view.javaClass.name == CHATTING_SCROLL_LAYOUT_CLASS) return false
        if (view.javaClass.name == TIPS_BAR_GROUP_CLASS && tipsGroups.containsValue(view)) return false
        if (find(view) { it.javaClass.name == CHATTING_SCROLL_LAYOUT_CLASS } != null) return false
        if (view.javaClass.name == "android.view.ViewStub") return false
        if (view.height >= content.height / 2 || view.width <= 0 || view.height <= 0) return false
        if (isNewMessageChip(view)) return false
        val name = view.javaClass.name
        if (name == TIPS_BAR_GROUP_CLASS || name.contains("Tips", true) ||
            name.contains("Notice", true) || name.contains("File", true)
        ) return true
        val lp = view.layoutParams as? FrameLayout.LayoutParams ?: return false
        val vertical = lp.gravity and android.view.Gravity.VERTICAL_GRAVITY_MASK
        return lp.height == ViewGroup.LayoutParams.WRAP_CONTENT || vertical == android.view.Gravity.TOP
    }

    private fun applyChatStatusWrapper(layout: ViewGroup) {
        var current: View? = layout
        var wrapper: View? = null
        while (current != null) {
            if (current.javaClass.name == STATUS_WRAPPER_CLASS) {
                wrapper = current
                break
            }
            current = current.parent as? View
        }
        if (wrapper == null) {
            wrapper = find(layout.rootView) { it.javaClass.name == STATUS_WRAPPER_CLASS }
        }
        val target = wrapper ?: return
        statusWrappersByLayout[layout] = target
        if (!statusWrapperBackgrounds.containsKey(target)) {
            statusWrapperBackgrounds[target] = target.background
            statusWrapperColors[target] = readStatusBarColor(target)
        }
        target.background = null
        invokeStatusBarColor(target, Color.TRANSPARENT)
        if (diagnosedStatusWrappers[target] != true) {
            diagnosedStatusWrappers[target] = true
            HLog.i(
                "[Hchat:$TAG] 聊天状态区目标: wrapper=${target.javaClass.name} " +
                    "parent=${target.parent?.javaClass?.name} size=${target.width}x${target.height} " +
                    "background=${target.background?.javaClass?.name} " +
                    "setStatusBarBackgroundColor=${hasStatusBarMethod(target, "setStatusBarBackgroundColor")} " +
                    "setStatusBarColor=${hasStatusBarMethod(target, "setStatusBarColor")}"
            )
        }
    }

    private fun findStatusBarMethod(view: View, name: String): Method? {
        var type: Class<*>? = view.javaClass
        while (type != null) {
            type.declaredMethods.firstOrNull {
                it.name == name && it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
            }?.let {
                it.isAccessible = true
                return it
            }
            type = type.superclass
        }
        return null
    }

    private fun hasStatusBarMethod(view: View, name: String): Boolean =
        findStatusBarMethod(view, name) != null

    private fun readStatusBarColor(view: View): Int? =
        (view.background as? android.graphics.drawable.ColorDrawable)?.color

    private fun invokeStatusBarColor(view: View, color: Int) {
        runCatching {
            findStatusBarMethod(view, "setStatusBarBackgroundColor")?.invoke(view, color)
            findStatusBarMethod(view, "setStatusBarColor")?.invoke(view, color)
        }
    }

    private fun restoreChatStatusWrapper(layout: ViewGroup) {
        val wrapper = statusWrappersByLayout.remove(layout)
            ?: statusWrapperBackgrounds.keys.firstOrNull { saved ->
            var current: View? = layout
            while (current != null) {
                if (current === saved) return@firstOrNull true
                current = current.parent as? View
            }
            false
        } ?: return
        wrapper.background = statusWrapperBackgrounds.remove(wrapper)
        statusWrapperColors.remove(wrapper)?.let { originalColor ->
            if (originalColor != null) invokeStatusBarColor(wrapper, originalColor)
        }
    }

    private fun diagnoseHeaderLayout(layout: View, header: View) {
        if (layout.width <= 0 || layout.height <= 0 || header.width <= 0 || header.height <= 0) return
        if (diagnosedLayouts.put(layout, true) != null) return
        val content = find(layout) { it.javaClass.name == CHATTING_CONTENT_CLASS } as? ViewGroup
        val headerLoc = IntArray(2).also(header::getLocationOnScreen)
        val children = if (content == null) {
            "ChattingContent=missing"
        } else {
            (0 until content.childCount).joinToString(" | ") { index ->
                val child = content.getChildAt(index)
                val lp = child.layoutParams
                val loc = IntArray(2).also(child::getLocationOnScreen)
                "#$index ${child.javaClass.name} size=${child.width}x${child.height} " +
                    "screen=${loc[0]},${loc[1]} vis=${child.visibility} " +
                    "lp=${lp?.javaClass?.name}:${lp?.width}x${lp?.height} " +
                    "translation=${child.translationX},${child.translationY}"
            }
        }
        HLog.i(
            "[Hchat:$TAG] 布局诊断: layout=${layout.javaClass.name} size=${layout.width}x${layout.height} " +
                "padding=${layout.paddingLeft},${layout.paddingTop},${layout.paddingRight},${layout.paddingBottom} " +
                "statusInset=${statusBarInset(layout)} header=${header.javaClass.name} " +
                "headerScreen=${headerLoc[0]},${headerLoc[1]} headerSize=${header.width}x${header.height} " +
                "windowBar=${isWindowBar(header, layout)} children=[$children]"
        )
    }

    private fun restore(layout: ViewGroup) {
        restoreChatStatusWrapper(layout)
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
        statusWrapperBackgrounds.entries.toList().forEach { (view, background) ->
            view.background = background
            statusWrapperColors.remove(view)?.let { originalColor ->
                if (originalColor != null) invokeStatusBarColor(view, originalColor)
            }
        }
        statusWrapperBackgrounds.clear()
        statusWrapperColors.clear()
        statusWrappersByLayout.clear()
        headersByLayout.clear()
        contentCardTranslations.forEach { (view, translation) -> view.translationY = translation }
        contentCardTranslations.clear()
        tipsCardStates.forEach { (view, state) ->
            view.elevation = state.elevation
            view.outlineProvider = state.outlineProvider
            view.clipToOutline = state.clipToOutline
            view.setPadding(state.paddingLeft, state.paddingTop, state.paddingRight, state.paddingBottom)
            FloatingChatCardVisuals.restore(view)
            view.invalidateOutline()
        }
        tipsCardStates.clear()
        tipsDimStates.forEach { (view, state) ->
            view.visibility = state.visibility
            view.alpha = state.alpha
            view.background = state.background
        }
        tipsDimStates.clear()
        tipsGroupElevations.forEach { (view, elevation) -> view.elevation = elevation }
        tipsGroupElevations.clear()
        tipsRecyclerMargins.forEach { (view, margins) ->
            val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return@forEach
            lp.leftMargin = margins[0]
            lp.topMargin = margins[1]
            lp.rightMargin = margins[2]
            lp.bottomMargin = margins[3]
            view.layoutParams = lp
        }
        tipsRecyclerMargins.clear()
        tipsRecyclerRules.forEach { (view, rules) ->
            val lp = view.layoutParams as? RelativeLayout.LayoutParams ?: return@forEach
            for (verb in lp.rules.indices) lp.removeRule(verb)
            rules.forEachIndexed { verb, anchor -> if (anchor != 0) lp.addRule(verb, anchor) }
            view.layoutParams = lp
        }
        tipsRecyclerRules.clear()
        tipsRecyclerBackgrounds.forEach { (view, background) -> view.background = background }
        tipsRecyclerBackgrounds.clear()
        tipsContentBackgrounds.forEach { (view, background) -> view.background = background }
        tipsContentBackgrounds.clear()
        layoutTrackers.forEach { (layout, tracker) ->
            tracker.preDraw?.let { listener ->
                runCatching { layout.viewTreeObserver.removeOnPreDrawListener(listener) }
            }
        }
        layoutTrackers.clear()
        tipsCardBottoms.clear()
        tipsAttachListeners.forEach { (view, listener) -> view.removeOnAttachStateChangeListener(listener) }
        tipsLayoutListeners.forEach { (view, listener) -> view.removeOnLayoutChangeListener(listener) }
        tipsAttachListeners.clear()
        tipsLayoutListeners.clear()
        tipsGroups.clear()
        diagnosedTipsGroups.clear()
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
    private class HeaderLayoutTracker(
        var preDraw: android.view.ViewTreeObserver.OnPreDrawListener? = null
    )

    private data class TipsCardState(
        val elevation: Float,
        val outlineProvider: ViewOutlineProvider?,
        val clipToOutline: Boolean,
        val paddingLeft: Int,
        val paddingTop: Int,
        val paddingRight: Int,
        val paddingBottom: Int
    )

    private data class TipsDimState(
        val visibility: Int,
        val alpha: Float,
        val background: Drawable?
    )

    private data class HeaderZoneState(
        val originalMargins: MutableMap<View, IntArray> = WeakHashMap()
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
    private object FloatingTipsCardOutlineProvider : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(
                0,
                0,
                view.width,
                view.height.coerceAtLeast(1),
                CARD_CORNER_RADIUS_DP * view.resources.displayMetrics.density
            )
        }
    }

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
        private const val CARD_GAP_DP = 8
        private const val CARD_CORNER_RADIUS_DP = 24
        private const val CHATTING_CONTENT_CLASS = "com.tencent.mm.pluginsdk.ui.chat.ChattingContent"
        private const val STATUS_WRAPPER_CLASS = "com.tencent.mm.ui.widget.EdgeToEdgeWrapperLayout"
        private const val TIPS_BAR_GROUP_CLASS = "com.tencent.mm.ui.tipsbar.ChatTipsBarGroup"
        private const val TIPS_RECYCLER_CLASS = "com.tencent.mm.view.recyclerview.MaxHeightWxRecyclerView"
        private const val WE_IMAGE_VIEW_CLASS = "com.tencent.mm.ui.widget.imageview.WeImageView"
        private const val CHATTING_SCROLL_LAYOUT_CLASS = "com.tencent.mm.pluginsdk.ui.chat.ChattingScrollLayout"
        private const val ELEVATION_DP = 4
        private const val CORNER_RADIUS_DP = 24
    }
}