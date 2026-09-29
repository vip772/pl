package t.mma.hooks.items.emojisave

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class EmojiSaveSettingsProvider : SimpleFeatureSettingsProvider(
    EmojiSaveFeature.ID,
    "保存表情",
    "长按聊天表情后保存原始文件到本地",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
