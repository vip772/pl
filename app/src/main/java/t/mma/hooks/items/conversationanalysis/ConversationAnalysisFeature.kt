package t.mma.hooks.items.conversationanalysis

import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext

/** Native entry point for the standalone group analysis feature. */
class ConversationAnalysisFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "群聊分析"

    /**
     * 消息菜单 Hook 必须在开关关闭时也完成安装，菜单运行时再按配置决定是否显示。
     * 否则用户首次打开设置开关时，FeatureManager 已经跳过了本功能的安装流程。
     */
    override fun isEnabled(context: FeatureContext): Boolean = true

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(ConversationAnalysisSettingsProvider())
    }

    private var runtime: ConversationAnalysisRuntime? = null
    private var messageMenuRuntime: ConversationAnalysisMessageMenuRuntime? = null

    override fun onFeatureInstall(context: FeatureContext) {
        // Hook 需要在关闭状态下先安装；具体是否展示菜单由运行时实时检查开关。
        val installed = ConversationAnalysisRuntime(context, ::logRuntimeError)
        runtime = installed
        installed.install()
        messageMenuRuntime = ConversationAnalysisMessageMenuRuntime(context, ::logRuntimeError)
        scheduleMessageMenuInstall()
        subscribe(Events.DexReady::class.java) { scheduleMessageMenuInstall() }
        logInfo(
            if (ConversationAnalysisSettings.isEnabled(context.hostContext())) {
                "功能已启用，已调度消息菜单 Hook"
            } else {
                "功能当前关闭，已安装受开关控制的消息菜单 Hook"
            }
        )
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
