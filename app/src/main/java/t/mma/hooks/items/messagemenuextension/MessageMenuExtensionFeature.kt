package t.mma.hooks.items.messagemenuextension

import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.utils.HLog

/** Native message menu grid extension. */
class MessageMenuExtensionFeature : BaseFeature() {
    private var runtime: MessageMenuGridRuntime? = null
    override fun featureId() = MessageMenuExtensionSettings.ID
    override fun name() = "消息菜单扩展"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(MessageMenuExtensionSettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        if (!MessageMenuExtensionSettings.preferences(context.hostContext())
                .getBoolean(MessageMenuExtensionSettings.KEY_ENABLE, false)) {
            return
        }
        runtime = MessageMenuGridRuntime(context)
        scheduleInstall()
        subscribe(Events.DexReady::class.java) { scheduleInstall() }
    }
    private fun scheduleInstall() {
        DexInstallScheduler.schedule(featureId(), name()) { runtime?.install() == true }
    }
    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }
}
