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
        runtime = QQMusicOrderRuntime(context.hostContext(), ::logError).also { musicRuntime ->
            musicRuntime.install { subscription -> trackSubscription(subscription) }
        }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
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
