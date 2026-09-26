package h.Hchat.hooks.items.conversationanalysis

import h.Hchat.event.Events
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.FeatureContext

/** Native entry point for the standalone group analysis feature. */
class ConversationAnalysisFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "群聊分析"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(ConversationAnalysisSettingsProvider())
    }

    private var runtime: ConversationAnalysisRuntime? = null
    private var messageMenuRuntime: ConversationAnalysisMessageMenuRuntime? = null

    override fun onFeatureInstall(context: FeatureContext) {
        if (!ConversationAnalysisSettings.isEnabled(context.hostContext())) {
            logInfo("功能未启用，跳过安装")
            return
        }
        runtime = ConversationAnalysisRuntime(context, ::logRuntimeError).also { it.install() }
        messageMenuRuntime = ConversationAnalysisMessageMenuRuntime(context, ::logRuntimeError)
        scheduleMessageMenuInstall()
        subscribe(Events.DexReady::class.java) { scheduleMessageMenuInstall() }
    }

    private fun scheduleMessageMenuInstall() {
        DexInstallScheduler.schedule(ID, "群聊分析消息菜单") {
            messageMenuRuntime?.install() == true
        }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        messageMenuRuntime?.destroy()
        messageMenuRuntime = null
        runtime?.destroy()
        runtime = null
    }

    private fun logRuntimeError(message: String, throwable: Throwable?) {
        if (throwable == null) logInfo(message) else logError(message, throwable)
    }

    companion object {
        const val ID = "conversation_analysis"
    }
}
