package t.mma.hooks.items.musicorder

import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.items.script.ScriptSendButtonHook

class QQMusicOrderFeature : BaseFeature() {
    private var runtime: QQMusicOrderRuntime? = null

    override fun featureId(): String = ID

    override fun name(): String = "QQ点歌"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(QQMusicOrderSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        scheduleSendButtonHook(context)
        subscribe(Events.DexReady::class.java) { scheduleSendButtonHook(context) }
        val track = subscriptionRegistrar()
        val installed = QQMusicOrderRuntime(context.hostContext(), ::logError)
        runtime = installed
        trackResource { installed.destroy(); if (runtime === installed) runtime = null }
        installed.install(track)
    }

    private fun scheduleSendButtonHook(context: FeatureContext) {
        DexInstallScheduler.schedule("shared:send_button", "聊天发送按钮", stage = DexInstallScheduler.Stage.WARMUP) {
            ScriptSendButtonHook.install(context)
        }
    }

    companion object {
        const val ID = "qq_music_order"
    }
}
