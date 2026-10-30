package t.mma.hooks.items.chattoolbar

import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext

class ChatToolbarFeature : BaseFeature() {
    private var runtime: ChatToolbarRuntime? = null

    override fun featureId(): String = ID

    override fun name(): String = "聊天工具栏"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(ChatToolbarSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val installed = ChatToolbarRuntime(context, ::logRuntimeError)
        runtime = installed
        trackResource { installed.destroy(); if (runtime === installed) runtime = null }
        installed.installBaseHooks()
        trackSubscription(
            WeChatApis.lifecycle()?.subscribe { event ->
                if (event.isDestroy) installed.onActivityDestroyed(event.activity)
            }
        )
        scheduleDexInstall()
        subscribe(Events.DexReady::class.java) { scheduleDexInstall() }
    }


    private fun scheduleDexInstall() {
        DexInstallScheduler.schedule(ID, name(), stage = DexInstallScheduler.Stage.BRIDGE) {
            runtime?.installDexHooks() == true
        }
    }

    private fun logRuntimeError(message: String, throwable: Throwable?) {
        logError(message, throwable)
    }

    companion object {
        const val ID = "chat_toolbar"
    }
}
