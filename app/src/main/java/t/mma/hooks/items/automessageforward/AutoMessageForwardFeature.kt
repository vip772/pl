package t.mma.hooks.items.automessageforward
import t.mma.utils.HLog

import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.preferences.HchatStorage
import t.mma.hooks.core.FeatureContext

class AutoMessageForwardFeature : BaseFeature() {
    private var runtime: AutoMessageForwardRuntime? = null

    override fun featureId(): String = ID

    override fun name(): String = "消息自动转发"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(AutoMessageForwardSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), AutoMessageForwardSettings.PREFS_NAME)
        var ready = false
        val owner = PreferenceRuntimeOwner(prefs, setOf(AutoMessageForwardSettings.KEY_ENABLED),
            enabled = { ready && AutoMessageForwardSettings.isEnabled(context.hostContext()) },
            create = { resources ->
                val observe = WeChatApis.message().observe() ?: error("消息观察 API 未就绪")
                val installed = AutoMessageForwardRuntime(context.hostContext()) { message, error -> HLog.e("$TAG $message", error) }
                runtime = installed
                resources.register { installed.shutdown(); if (runtime === installed) runtime = null }
                val subscription = observe.subscribe { if (!resources.isClosed()) installed.handleMessage(it) }
                resources.register { subscription?.unsubscribe() }
                val recalled = context.eventBus().subscribe(Events.MessageRecalled::class.java, { event ->
                    if (!resources.isClosed()) installed.handleRecall(event)
                }, 0)
                resources.register { context.eventBus().unsubscribe(recalled) }
            }, onError = { logError("自动转发热启停失败", it) })
        trackResource { owner.close() }
        subscribe(Events.DexReady::class.java) { ready = true; owner.refresh() }
        ready = WeChatApis.message().observe() != null
        owner.start()
    }
    companion object {
        const val ID = "auto_message_forward"
        private const val TAG = "[Hchat:AutoMessageForward]"
    }
}
