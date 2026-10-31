package t.mma.hooks.items.atallnotify
import t.mma.utils.HLog

import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.preferences.HchatStorage
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.items.customnotify.CustomNotificationRuntime

class AtAllNotificationBlockFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "屏蔽艾特所有人"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(AtAllNotificationBlockSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        CustomNotificationRuntime.install(context)
        val prefs = HchatStorage.preferences(context.hostContext(), AtAllNotificationBlockSettings.PREFS_NAME)
        var dexReady = WeChatApis.message().observe()?.isAvailable == true
        val owner = PreferenceRuntimeOwner(prefs, setOf(AtAllNotificationBlockSettings.KEY_ENABLE),
            enabled = { dexReady && prefs.getBoolean(AtAllNotificationBlockSettings.KEY_ENABLE, AtAllNotificationBlockSettings.DEFAULT_ENABLE) },
            create = { resources ->
                val observe = WeChatApis.message().observe() ?: error("消息观察 API 未就绪")
                val subscription = observe.subscribe { message ->
                    if (!resources.isClosed()) AtAllNotificationBlockRuntime.record(context.hostContext(), message)
                }
                resources.register { subscription.unsubscribe() }
            }, onError = { logError("通知订阅启停失败", it) })
        trackResource { owner.close() }
        owner.start()
        subscribe(Events.DexReady::class.java) { dexReady = true; owner.refresh() }
    }
    override fun onFeatureDestroy(context: FeatureContext) {
        AtAllNotificationBlockRuntime.clear()
    }

    companion object {
        const val ID = "block_at_all_notification"
        private const val TAG = "[Hchat:BlockAtAllNotification]"
    }
}
