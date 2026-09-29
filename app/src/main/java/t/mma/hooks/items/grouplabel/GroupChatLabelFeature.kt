package t.mma.hooks.items.grouplabel

import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext

class GroupChatLabelFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "群聊标签"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(GroupChatLabelSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) = Unit

    companion object {
        const val ID = "group_chat_label"
    }
}
