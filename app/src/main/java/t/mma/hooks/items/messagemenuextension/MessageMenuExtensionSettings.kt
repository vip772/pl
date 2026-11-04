package t.mma.hooks.items.messagemenuextension

import android.content.Context
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

object MessageMenuExtensionSettings {
    const val ID = "message_menu_extension"
    const val PREFS_NAME = "Hchat_message_menu_extension_config"
    const val KEY_ENABLE = "message_menu_extension_enable"
    const val KEY_COLUMNS = "message_menu_extension_columns"
    const val KEY_MAX_ROWS = "message_menu_extension_max_rows"
    const val DEFAULT_COLUMNS = 5
    const val DEFAULT_MAX_ROWS = 3
    fun columns(prefs: android.content.SharedPreferences) =
        prefs.getInt(KEY_COLUMNS, DEFAULT_COLUMNS).coerceIn(5, 8)
    fun maxRows(prefs: android.content.SharedPreferences) =
        prefs.getInt(KEY_MAX_ROWS, DEFAULT_MAX_ROWS).coerceIn(3, 5)
    fun preferences(context: Context) = HchatStorage.preferences(context, PREFS_NAME)
}

class MessageMenuExtensionSettingsProvider : SimpleFeatureSettingsProvider(
    MessageMenuExtensionSettings.ID,
    "消息菜单扩展",
    "自定义每排5至8个、最多3至5排，适配当前窗口",
    FeatureSettingsProvider.CATEGORY_ENHANCE,
    true
)
