package t.mma.hooks.items.keywordnotify
import t.mma.utils.HLog

import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.preferences.HchatStorage
import t.mma.hooks.core.FeatureContext

class KeywordNotificationFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "关键词通知"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(KeywordNotificationSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), KeywordNotificationSettings.PREFS_NAME)
        var ready = false
        val owner = PreferenceRuntimeOwner(prefs, setOf(KeywordNotificationSettings.KEY_ENABLE),
            enabled = { ready && prefs.getBoolean(KeywordNotificationSettings.KEY_ENABLE, KeywordNotificationSettings.DEFAULT_ENABLE) },
            create = { resources ->
                val observe = WeChatApis.message().observe() ?: error("消息观察 API 未就绪")
                KeywordNotificationRuntime.start()
                resources.register { KeywordNotificationRuntime.stop() }
                val messageSubscription = observe.subscribe { message ->
                    if (!resources.isClosed()) KeywordNotificationRuntime.handleMessage(context.hostContext(), message)
                }
                resources.register { messageSubscription?.unsubscribe() }
                val blocked = context.eventBus().subscribe(Events.MessageBlocked::class.java, { event ->
                    if (!resources.isClosed()) KeywordNotificationRuntime.handleBlockedMessage(context.hostContext(), event)
                }, 0)
                resources.register { context.eventBus().unsubscribe(blocked) }
            }, onError = { logError("关键词通知热启停失败", it) })
        trackResource { owner.close() }
        subscribe(Events.DexReady::class.java) { ready = true; owner.refresh() }
        ready = WeChatApis.message().observe() != null
        owner.start()
    }
    companion object {
        const val ID = "keyword_notification"
        private const val TAG = "[Hchat:KeywordNotification]"
    }
}
