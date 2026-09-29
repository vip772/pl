package t.mma.hooks.items.hidemenu

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class HideChatMenuSettingsProvider : SimpleFeatureSettingsProvider(
    HideChatMenuFeature.ID,
    "隐藏聊天菜单",
    "隐藏聊天消息长按菜单中的指定项目",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
