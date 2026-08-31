package h.Hchat.hooks.items.floatingheader

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import de.robv.android.xposed.XC_MethodHook
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.KavaReflector
import java.lang.reflect.Method
import java.util.WeakHashMap

class FloatingChatHeaderFeature : BaseFeature() {
    private var runtime: FloatingChatHeaderRuntime? = null

    override fun featureId(): String = ID
    override fun name(): String = "悬浮标题栏"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(FloatingChatHeaderSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        runtime = FloatingChatHeaderRuntime(context, ::logError).also { it.install() }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }

    companion object { const val ID = "floating_chat_header" }
}

private class FloatingChatHeaderRuntime(
    private val context: FeatureContext,
    private val logger: (String, Throwable?) -> Unit
) {
    private data class OriginalState(
        val background: android.graphics.drawable.Drawable?,
        val elevation: Float,
        val clipToOutline: Boolean,
        val outlineProvider: ViewOutlineProvider?,
        val leftMargin: Int?, val topMargin: Int?, val rightMargin: Int?, val bottomMargin: Int?
    )

    private val trackedLayouts = WeakHashMap<View, ViewTreeObserver.OnPreDrawListener>()
    private val headerStates = WeakHashMap<View, OriginalState>()
    private var installed = false

    @Synchronized fun install(): Boolean {
        if (installed) return true
        val clazz = KavaReflector.loadClass(CHATTING_LAYOUT, context.hostClassLoader())
            ?: return fail("未找到聊天布局类: $CHATTING_LAYOUT")
        val constructors = KavaReflector.declaredConstructors(clazz).filter { constructor ->
            constructor.parameterTypes.firstOrNull()?.let { Context::class.java.isAssignableFrom(it) } == true
        }
        val onLayout = findOnLayout(clazz) ?: return fail("未找到 ChattingUILayout.onLayout 入口")
        return runCatching {
            constructors.forEach { constructor ->
                HookRegistry.get().hook(constructor, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (param.thisObject as? View)?.let(::trackLayout)
                    }
                })
            }
            HookRegistry.get().hook(onLayout, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    (param.thisObject as? View)?.let(::trackLayout)
                }
            })
            installed = true
            true
        }.getOrElse { logger("安装悬浮标题栏 Hook 失败", it); false }
    }

    fun destroy() {
        trackedLayouts.entries.toList().forEach { (layout, listener) ->
            if (layout.viewTreeObserver.isAlive) layout.viewTreeObserver.removeOnPreDrawListener(listener)
        }
        trackedLayouts.clear()
        headerStates.entries.toList().forEach { (header, state) -> restore(header, state) }
        headerStates.clear()
        installed = false
    }

    private fun findOnLayout(clazz: Class<*>): Method? {
        var current: Class<*>? = clazz
        while (current != null && View::class.java.isAssignableFrom(current)) {
            KavaReflector.findDeclaredMethod(
                current, "onLayout", Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!
            )?.let { return it }
            current = current.superclass
        }
        return null
    }

    private fun trackLayout(layout: View) {
        if (trackedLayouts.containsKey(layout)) return
        val listener = ViewTreeObserver.OnPreDrawListener { reconcile(layout); true }
        trackedLayouts[layout] = listener
        layout.viewTreeObserver.addOnPreDrawListener(listener)
        layout.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                trackedLayouts.remove(v)?.let { old ->
                    if (v.viewTreeObserver.isAlive) v.viewTreeObserver.removeOnPreDrawListener(old)
                }
            }
        })
        layout.post { reconcile(layout) }
    }

    private fun reconcile(layout: View) {
        val header = findHeader(layout) ?: return
        if (!headerStates.containsKey(header)) saveAndStyle(header)
    }

    private fun findHeader(layout: View): View? {
        findDescendant(layout) { it.javaClass.name == ACTION_BAR_CONTAINER }?.let { return it }
        if (!isStandaloneChat(layout)) return null
        return findDescendant(layout.rootView) { it.javaClass.name == ACTION_BAR_CONTAINER }
    }

    private fun isStandaloneChat(layout: View): Boolean {
        var current: Context? = layout.context
        while (current is android.content.ContextWrapper) {
            if (current is android.app.Activity) return current.javaClass.name == CHATTING_UI
            current = current.baseContext
        }
        return current is android.app.Activity && current.javaClass.name == CHATTING_UI
    }

    private fun findDescendant(root: View?, predicate: (View) -> Boolean): View? {
        if (root == null) return null
        if (predicate(root)) return root
        val group = root as? ViewGroup ?: return null
        for (i in 0 until group.childCount) findDescendant(group.getChildAt(i), predicate)?.let { return it }
        return null
    }

    private fun saveAndStyle(header: View) {
        val margins = header.layoutParams as? ViewGroup.MarginLayoutParams
        headerStates[header] = OriginalState(
            header.background, header.elevation, header.clipToOutline, header.outlineProvider,
            margins?.leftMargin, margins?.topMargin, margins?.rightMargin, margins?.bottomMargin
        )
        val density = header.resources.displayMetrics.density
        val radius = FloatingChatHeaderSettings.radiusDp(context.hostContext()) * density
        val horizontal = (FloatingChatHeaderSettings.horizontalMarginDp(context.hostContext()) * density).toInt()
        val vertical = (FloatingChatHeaderSettings.verticalMarginDp(context.hostContext()) * density).toInt()
        val alpha = (FloatingChatHeaderSettings.opacity(context.hostContext()) * 255f).toInt()
        val color = if (isDark(header)) Color.argb(alpha, 38, 38, 38) else Color.argb(alpha, 255, 255, 255)
        header.background = GradientDrawable().apply { cornerRadius = radius; setColor(color) }
        margins?.let {
            it.leftMargin += horizontal; it.rightMargin += horizontal
            it.topMargin += vertical; it.bottomMargin += vertical
            header.layoutParams = it
        }
        header.elevation = FloatingChatHeaderSettings.elevationDp(context.hostContext()) * density
        header.clipToOutline = true
        header.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        header.invalidateOutline()
    }

    private fun restore(header: View, state: OriginalState) = runCatching {
        header.background = state.background
        header.elevation = state.elevation
        header.clipToOutline = state.clipToOutline
        header.outlineProvider = state.outlineProvider
        (header.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            state.leftMargin?.let { value -> it.leftMargin = value }
            state.topMargin?.let { value -> it.topMargin = value }
            state.rightMargin?.let { value -> it.rightMargin = value }
            state.bottomMargin?.let { value -> it.bottomMargin = value }
            header.layoutParams = it
        }
        header.requestLayout()
    }.getOrNull()

    private fun isDark(view: View): Boolean =
        (view.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun fail(message: String): Boolean { logger(message, null); return false }

    companion object {
        private const val CHATTING_LAYOUT = "com.tencent.mm.pluginsdk.ui.chat.ChattingUILayout"
        private const val CHATTING_UI = "com.tencent.mm.ui.chatting.ChattingUI"
        private const val ACTION_BAR_CONTAINER = "androidx.appcompat.widget.ActionBarContainer"
    }
}
