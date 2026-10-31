package t.mma.hooks.items.payment.core

import t.mma.event.Events
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext

/**
 * 自动抢红包功能入口。
 */
class AutoRedPacketFeature : BaseFeature() {
    private var hooker: RedPacketHooker? = null

    override fun featureId(): String = ID

    override fun name(): String = "自动抢红包"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(RedPacketSettingsProvider())

        subscribe(Events.RedPacketGrabbed::class.java) {
            // 预留给跨功能通知、统计等逻辑。
        }
    }

    @Throws(Throwable::class)
    override fun onFeatureInstall(context: FeatureContext) {
        val settings = RedPacketSettings(context.hostClassLoader(), context.hostContext())
        val prefs = settings.getHostPreferences() ?: return
        val keys = setOf(RedPacketSettings.KEY_ENABLE, RedPacketSettings.KEY_FAKE_PACKET_ENABLE,
            RedPacketSettings.KEY_BLOCK_NEW_GROUP_ENABLE)
        var schedule: (() -> Unit)? = null
        val owner = PreferenceRuntimeOwner(prefs, keys,
            enabled = { keys.any { prefs.getBoolean(it, false) } },
            create = { resources ->
                val current = RedPacketHooker(context.hostContext(), context.hostClassLoader(), context.dexFinder())
                hooker = current
                resources.register { current.destroy(); if (hooker === current) hooker = null }
                val ticket = DexInstallScheduler.installation(ID, name(), DexInstallScheduler.Stage.WARMUP)
                resources.register { ticket.close(); schedule = null }
                schedule = {
                    ticket.schedule {
                        if (resources.isClosed()) false else {
                            current.hookAll()
                            current.isDexReadyEnough()
                        }
                    }
                }
                schedule?.invoke()
            }, onError = { logError("红包资源启停失败", it) }, restartOnChange = true)
        trackResource { owner.close() }
        owner.start()
        subscribe(Events.DexReady::class.java) { owner.refresh(); schedule?.invoke() }
    }
    companion object {
        const val ID = "auto_redpacket"
    }
}
