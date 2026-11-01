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
        val prefs = MessageMenuExtensionSettings.preferences(context.hostContext())
        var schedule: (() -> Unit)? = null
        val owner = t.mma.hooks.core.PreferenceRuntimeOwner(prefs,
            setOf(MessageMenuExtensionSettings.KEY_ENABLE),
            enabled = { prefs.getBoolean(MessageMenuExtensionSettings.KEY_ENABLE, false) },
            create = { resources ->
                val next = MessageMenuGridRuntime(context)
                runtime = next
                resources.register { next.destroy(); if (runtime === next) runtime = null }
                val ticket = DexInstallScheduler.installation(featureId(), name())
                val install = { ticket.schedule { if (resources.isClosed()) true else next.install() } }
                schedule = install
                resources.register { if (schedule === install) schedule = null }
                resources.register { ticket.close() }
                install()
            }, onError = { logError("消息菜单扩展启停失败", it) })
        trackResource { owner.close() }
        owner.start()
        subscribe(Events.DexReady::class.java) { schedule?.invoke() }
    }
}
