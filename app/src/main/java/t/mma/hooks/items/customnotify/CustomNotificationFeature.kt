package t.mma.hooks.items.customnotify
import t.mma.utils.HLog

import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext

class CustomNotificationFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "自定义通知"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(CustomNotificationSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        CustomNotificationRuntime.install(context)
        subscribe(Events.DexReady::class.java) {
            val observe = WeChatApis.message().observe()
            if (observe == null) {
                HLog.e("$TAG 消息观察 API 未就绪")
                return@subscribe
            }
            trackSubscription(observe.subscribe { message ->
                CustomNotificationRuntime.handleMessage(context.hostContext(), message)
            })
        }
    }

    companion object {
        const val ID = "custom_notification"
        private const val TAG = "[Hchat:CustomNotification]"
    }
}
