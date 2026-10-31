package t.mma.hooks.items.customnotify
import t.mma.utils.HLog

import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.preferences.HchatStorage
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
        val prefs = HchatStorage.preferences(context.hostContext(), CustomNotificationSettings.PREFS_NAME)
        var dexReady = WeChatApis.message().observe()?.isAvailable == true
        val owner = PreferenceRuntimeOwner(prefs, setOf(CustomNotificationSettings.KEY_ENABLE),
            enabled = { dexReady && prefs.getBoolean(CustomNotificationSettings.KEY_ENABLE, CustomNotificationSettings.DEFAULT_ENABLE) },
            create = { resources ->
                val observe = WeChatApis.message().observe() ?: error("消息观察 API 未就绪")
                val subscription = observe.subscribe { message ->
                    if (!resources.isClosed()) CustomNotificationRuntime.handleMessage(context.hostContext(), message)
                }
                resources.register { subscription.unsubscribe() }
            }, onError = { logError("通知订阅启停失败", it) })
        trackResource { owner.close() }
        owner.start()
        subscribe(Events.DexReady::class.java) { dexReady = true; owner.refresh() }
    }
    companion object {
        const val ID = "custom_notification"
        private const val TAG = "[Hchat:CustomNotification]"
    }
}
