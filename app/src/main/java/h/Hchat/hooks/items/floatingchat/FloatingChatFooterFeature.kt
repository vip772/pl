package h.Hchat.hooks.items.floatingchat

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.FeatureContext
import java.util.WeakHashMap
import kotlin.math.roundToInt

class FloatingChatFooterFeature : BaseFeature() {
    private var runtime: FloatingChatFooterRuntime? = null
    override fun featureId() = ID
    override fun name() = "悬浮输入框"
    override fun onFeatureInit(context: FeatureContext) = registerSettingsProvider(FloatingChatFooterSettingsProvider())
    override fun onFeatureInstall(context: FeatureContext) {
        runtime = FloatingChatFooterRuntime(context.hostContext())
        DexInstallScheduler.schedule(ID, name()) { runtime?.install() ?: false }
        trackSubscription(WeChatApis.lifecycle()?.subscribe { event ->
            if (event.isResume() && event.isChatting()) runtime?.apply(event.activity)
            if (event.isDestroy()) runtime?.restore(event.activity)
        })
    }
    override fun onFeatureDestroy(context: FeatureContext) { runtime?.restoreAll(); runtime = null }
    companion object { const val ID = "floating_chat_footer" }
}

private class FloatingChatFooterRuntime(private val context: android.content.Context) {
    private val parents = WeakHashMap<View, ViewGroup>()
    private val params = WeakHashMap<View, ViewGroup.LayoutParams>()
    fun install() = true
    fun apply(activity: Activity) {
        val root = activity.window.decorView as? ViewGroup ?: return
        val footer = find(root) { it.javaClass.name == "com.tencent.mm.pluginsdk.ui.chat.ChatFooterBottom" } ?: return
        val target = root.findViewById<View>(android.R.id.content) as? ViewGroup ?: root
        if (footer.parent !== target) {
            val parent = footer.parent as? ViewGroup ?: return
            val oldParams = footer.layoutParams
            parents[footer] = parent; params[footer] = oldParams
            parent.removeView(footer)
            target.addView(footer, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, oldParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(12); rightMargin = dp(12); bottomMargin = dp(8); gravity = android.view.Gravity.BOTTOM })
        }
        footer.translationZ = dp(4).toFloat()
        FloatingChatCardVisuals.apply(footer, 24)
    }
    fun restore(activity: Activity) { restoreRoot(activity.window.decorView as? ViewGroup) }
    fun restoreAll() { parents.keys.toList().forEach(::restore) }
    private fun restoreRoot(root: ViewGroup?) {
        root ?: return
        root.findViews { parents.containsKey(it) }.forEach { view -> restore(view) }
    }
    private fun restore(view: View) { val parent = parents.remove(view) ?: return; val old = params.remove(view); (view.parent as? ViewGroup)?.removeView(view); parent.addView(view, old); FloatingChatCardVisuals.restore(view) }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
    private fun find(root: View, predicate: (View) -> Boolean): View? = if (predicate(root)) root else (root as? ViewGroup)?.let { group -> (0 until group.childCount).asSequence().mapNotNull { find(group.getChildAt(it), predicate) }.firstOrNull() }
    private fun ViewGroup.findViews(predicate: (View) -> Boolean): List<View> = buildList { fun walk(v: View) { if (predicate(v)) add(v); (v as? ViewGroup)?.let { g -> for (i in 0 until g.childCount) walk(g.getChildAt(i)) } }; walk(this) }
}
