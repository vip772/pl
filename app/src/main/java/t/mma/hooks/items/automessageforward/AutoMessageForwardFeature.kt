package t.mma.hooks.items.automessageforward
import t.mma.utils.HLog

import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext

class AutoMessageForwardFeature : BaseFeature() {
    private var runtime: AutoMessageForwardRuntime? = null

    override fun featureId(): String = ID

    override fun name(): String = "消息自动转发"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(AutoMessageForwardSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        subscribe(Events.DexReady::class.java) {
            if (runtime != null) return@subscribe
            val observe = WeChatApis.message().observe()
            if (observe == null) {
                HLog.e("$TAG 消息观察 API 未就绪")
                return@subscribe
            }
            val installed = AutoMessageForwardRuntime(context.hostContext()) { message, error ->
                HLog.e("$TAG $message", error)
            }
            runtime = installed
            trackSubscription(observe.subscribe(installed::handleMessage))
            subscribe(Events.MessageRecalled::class.java) { event -> installed.handleRecall(event) }
        }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.shutdown()
        runtime = null
    }

    companion object {
        const val ID = "auto_message_forward"
        private const val TAG = "[Hchat:AutoMessageForward]"
    }
}
