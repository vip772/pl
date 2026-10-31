package t.mma.hooks.items.textvoice

import t.mma.event.Events
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.items.script.ScriptSendButtonHook

class TextVoiceFeature : BaseFeature() {
    private var runtime: TextVoiceRuntime? = null

    override fun featureId(): String = ID

    override fun name(): String = "文本转语音"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(TextVoiceSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = TextVoiceSettings.preferences(context.hostContext())
        var schedule: (() -> Unit)? = null
        val owner = PreferenceRuntimeOwner(prefs,
            setOf(TextVoiceSettings.KEY_SEND_ENABLE, TextVoiceSettings.KEY_PLAY_ENABLE),
            enabled = { prefs.getBoolean(TextVoiceSettings.KEY_SEND_ENABLE, TextVoiceSettings.DEFAULT_SEND_ENABLE) ||
                prefs.getBoolean(TextVoiceSettings.KEY_PLAY_ENABLE, TextVoiceSettings.DEFAULT_PLAY_ENABLE) },
            create = { resources ->
                val current = TextVoiceRuntime(context, ::logFeatureError)
                runtime = current
                resources.register { current.destroy(); if (runtime === current) runtime = null }
                val subscription = ScriptSendButtonHook.registerHandler(ID) { text -> current.handleSendButton(text) }
                resources.register { subscription.unsubscribe() }
                val ticket = DexInstallScheduler.installation(ID, name())
                resources.register { ticket.close(); schedule = null }
                schedule = {
                    if (!resources.isClosed()) {
                        scheduleSendButtonHook(context)
                        ticket.schedule { current.installMessageMenu() }
                    }
                }
                schedule?.invoke()
            }, onError = { logFeatureError("文本转语音热启停失败", it) }, restartOnChange = true)
        trackResource { owner.close() }
        owner.start()
        subscribe(Events.DexReady::class.java) { owner.refresh(); schedule?.invoke() }
    }
    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }

    private fun scheduleSendButtonHook(context: FeatureContext) {
        DexInstallScheduler.schedule(
            "shared:send_button",
            "聊天发送按钮",
            stage = DexInstallScheduler.Stage.WARMUP
        ) {
            ScriptSendButtonHook.install(context)
        }
    }

    private fun logFeatureError(message: String, throwable: Throwable?) {
        logError(message, throwable)
    }

    companion object {
        const val ID = "text_voice"
    }
}
