package t.mma.hooks.items.specialmessage

import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

/** Special-message settings container; message security is handled by SendSecureMessageFeature. */
class SpecialMessageFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "特殊消息"

    override fun isEnabled(context: FeatureContext): Boolean = true

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(
            object : SimpleFeatureSettingsProvider(
                ID,
                name(),
                "安全消息",
                FeatureSettingsProvider.CATEGORY_PRACTICAL
            ) {}
        )
    }

    override fun onFeatureInstall(context: FeatureContext) {
        logInfo("特殊消息设置入口已安装，安全消息链路由独立功能负责")
    }

    companion object {
        const val ID = "special_message"
        const val PREFS = "hchat_special_message"
    }
}
