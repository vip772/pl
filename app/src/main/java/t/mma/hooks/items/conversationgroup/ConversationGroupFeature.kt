package t.mma.hooks.items.conversationgroup

import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext

class ConversationGroupFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "聊天分组"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(ConversationGroupSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        DexInstallScheduler.schedule(ID, name(), stage = DexInstallScheduler.Stage.BRIDGE) {
            ConversationGroupRuntime.install(context)
        }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        ConversationGroupRuntime.destroy()
    }

    companion object {
        const val ID = "conversation_group"
    }
}
