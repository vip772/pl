package t.mma.hooks.items.messagemenuextension

import android.content.Context
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

object MessageMenuExtensionSettings {
    const val ID = "message_menu_extension"
    const val PREFS_NAME = "Hchat_message_menu_extension_config"
    const val KEY_ENABLE = "message_menu_extension_enable"
    fun preferences(context: Context) = HchatStorage.preferences(context, PREFS_NAME)
}

class MessageMenuExtensionSettingsProvider : SimpleFeatureSettingsProvider(
    MessageMenuExtensionSettings.ID,
    "消息菜单扩展",
    "目标每排6个、最多5排；当前为实机定位诊断版",
    FeatureSettingsProvider.CATEGORY_ENHANCE,
    true
)
