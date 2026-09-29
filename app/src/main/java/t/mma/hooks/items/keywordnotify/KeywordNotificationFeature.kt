package t.mma.hooks.items.keywordnotify
import t.mma.utils.HLog

import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext

class KeywordNotificationFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "关键词通知"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(KeywordNotificationSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        subscribe(Events.DexReady::class.java) {
            val observe = WeChatApis.message().observe()
            if (observe == null) {
                HLog.e("$TAG 消息观察 API 未就绪")
                return@subscribe
            }
            trackSubscription(observe.subscribe { message ->
                KeywordNotificationRuntime.handleMessage(context.hostContext(), message)
            })
        }
        subscribe(Events.MessageBlocked::class.java) { event ->
            KeywordNotificationRuntime.handleBlockedMessage(context.hostContext(), event)
        }
    }

    companion object {
        const val ID = "keyword_notification"
        private const val TAG = "[Hchat:KeywordNotification]"
    }
}
