package h.Hchat.hooks.items.conversationanalysis

import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.core.ConversationMenuExtension
import h.Hchat.hooks.core.ConversationMenuExtensionRegistry
import h.Hchat.hooks.core.ConversationMenuExtensionTarget
import h.Hchat.hooks.core.FeatureContext

/** Native runtime for the report entry and its asynchronous report screen. */
internal class ConversationAnalysisRuntime(
    private val context: FeatureContext,
    private val logger: (String, Throwable?) -> Unit
) {
    private val menuExtension = object : ConversationMenuExtension {
        override val itemId: Int = MENU_ITEM_ID
        override val order: Int = 135
        override fun title(target: ConversationMenuExtensionTarget): String = MENU_TITLE
        override fun isVisible(target: ConversationMenuExtensionTarget): Boolean {
            return isEnabled(target.activity) && isGroup(target.talker)
        }
        override fun onClick(target: ConversationMenuExtensionTarget) {
            ConversationAnalysisReportDialog.show(target.activity, target.talker, logger)
        }
    }

    fun install(): Boolean {
        ConversationMenuExtensionRegistry.register(menuExtension)
        logger("群聊分析原生运行时已安装", null)
        return true
    }

    fun destroy() {
        ConversationMenuExtensionRegistry.unregister(menuExtension)
    }

    private fun isEnabled(activity: android.app.Activity): Boolean =
        ConversationAnalysisSettings.isEnabled(activity)

    private fun isGroup(talker: String): Boolean = runCatching {
        val value = talker.trim()
        value.endsWith("@chatroom") || value.endsWith("@im.chatroom") ||
            (WeChatApis.contacts()?.isGroup(value) == true)
    }.getOrDefault(false)

    companion object {
        private const val MENU_ITEM_ID = 0x4843414E
        private const val MENU_TITLE = "群聊分析"
    }
}

