package t.mma.hooks.items.messagebubble

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class MessageBubbleSettingsProvider : SimpleFeatureSettingsProvider(
    MessageBubbleFeature.ID,
    "消息气泡",
    "分别替换聊天左右侧气泡并适配深色模式",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
