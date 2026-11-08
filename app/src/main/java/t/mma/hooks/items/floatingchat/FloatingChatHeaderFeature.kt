package t.mma.hooks.items.floatingchat

import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext

class FloatingChatHeaderFeature : BaseFeature() {
    private var runtime: HeaderRuntime? = null
    // Install lifecycle hooks even while disabled; runtime checks gate mutations.
    override fun isEnabled(context: FeatureContext): Boolean = true
    override fun onConfigChanged(context: FeatureContext, key: String?) {
        val current = runtime ?: return
        current.refreshVisibleOnMain()
    }
    override fun featureId(): String = ID
    override fun name(): String = "悬浮标题栏"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(FloatingChatHeaderSettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        val current = ChatHeaderMount.acquire(context)
        runtime = current
        trackResource { ChatHeaderMount.release(); runtime = null }
    }

    companion object { const val ID = "floating_chat_header" }
}
