package t.mma.hooks.items.conversationgroup

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class ConversationGroupSettingsProvider : SimpleFeatureSettingsProvider(
    ConversationGroupFeature.ID,
    "聊天分组",
    "按自定义分组整理聊天，支持多级分类",
    FeatureSettingsProvider.CATEGORY_ENHANCE
)
