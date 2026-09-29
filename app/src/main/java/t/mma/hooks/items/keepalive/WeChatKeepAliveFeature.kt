package t.mma.hooks.items.keepalive

import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext

class WeChatKeepAliveFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "微信强保活"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(WeChatKeepAliveSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        WeChatKeepAliveRuntime.apply(context.hostContext(), context.moduleContext())
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        WeChatKeepAliveRuntime.stop(context.moduleContext())
    }

    companion object {
        const val ID = "wechat_keep_alive"
    }
}
