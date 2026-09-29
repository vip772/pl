package t.mma.hooks.items.messagetextcolor

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class MessageTextColorSettingsProvider : SimpleFeatureSettingsProvider(
    MessageTextColorFeature.ID,
    "消息文本颜色",
    "自定义聊天文本消息左右侧颜色",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
