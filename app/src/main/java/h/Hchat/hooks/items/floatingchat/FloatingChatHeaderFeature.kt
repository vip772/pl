package h.Hchat.hooks.items.floatingchat

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.ui.WeChatLifecycleApi
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.utils.HLog
import java.util.WeakHashMap
import kotlin.math.roundToInt

class FloatingChatHeaderFeature : BaseFeature() {
    private var runtime: FloatingChatHeaderRuntime? = null
    override fun featureId() = ID
    override fun name() = "悬浮标题栏"
    override fun onFeatureInit(context: FeatureContext) = registerSettingsProvider(FloatingChatHeaderSettingsProvider())
    override fun onFeatureInstall(context: FeatureContext) {
        runtime = FloatingChatHeaderRuntime(context.hostContext())
        DexInstallScheduler.schedule(ID, name()) { runtime?.install() ?: false }
        trackSubscription(WeChatApis.lifecycle()?.subscribe { event ->
            if (event.isResume() && event.isChatting()) runtime?.apply(event.activity)
            if (event.isDestroy()) runtime?.restore(event.activity)
        })
    }
    override fun onFeatureDestroy(context: FeatureContext) { runtime?.restoreAll(); runtime = null }
    companion object { const val ID = "floating_chat_header" }
}

private class FloatingChatHeaderRuntime(private val context: android.content.Context) {
    private val parents = WeakHashMap<View, ViewGroup>()
    private val params = WeakHashMap<View, ViewGroup.LayoutParams>()
    fun install() = true
    fun apply(activity: Activity) {
        val root = activity.window.decorView as? ViewGroup ?: return
        val header = find(root) { it.javaClass.name == "androidx.appcompat.widget.ActionBarContainer" } ?: return
        val target = root.findViewById<View>(android.R.id.content) as? ViewGroup ?: root
        if (header.parent !== target) {
            val parent = header.parent as? ViewGroup ?: return
            val oldParams = header.layoutParams
            parents[header] = parent
            params[header] = oldParams
            parent.removeView(header)
            target.addView(header, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                oldParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(12); rightMargin = dp(12); topMargin = dp(4) })
        }
        header.translationZ = dp(4).toFloat()
        FloatingChatCardVisuals.apply(header, 24)
    }
    fun restore(activity: Activity) { restoreRoot(activity.window.decorView as? ViewGroup) }
    fun restoreAll() { parents.keys.toList().forEach { restore(it) } }
    private fun restoreRoot(root: ViewGroup?) {
        root ?: return
        root.findViews { parents.containsKey(it) }.forEach { view -> restore(view) }
    }
    private fun restore(view: View) {
        val parent = parents.remove(view) ?: return
        val old = params.remove(view)
        (view.parent as? ViewGroup)?.removeView(view)
        parent.addView(view, old)
        FloatingChatCardVisuals.restore(view)
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
    private fun find(root: View, predicate: (View) -> Boolean): View? = if (predicate(root)) root else (root as? ViewGroup)?.let { group -> (0 until group.childCount).asSequence().mapNotNull { find(group.getChildAt(it), predicate) }.firstOrNull() }
    private fun ViewGroup.findViews(predicate: (View) -> Boolean): List<View> = buildList { fun walk(v: View) { if (predicate(v)) add(v); (v as? ViewGroup)?.let { g -> for (i in 0 until g.childCount) walk(g.getChildAt(i)) } }; walk(this) }
}
