package h.Hchat.hooks.items.conversationanalysis

import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider

class ConversationAnalysisSettingsProvider : SimpleFeatureSettingsProvider(
    ConversationAnalysisFeature.ID,
    "群聊分析",
    "统计群聊消息、活跃排行、聊天作息、内容特征和分析报告",
    FeatureSettingsProvider.CATEGORY_PRACTICAL,
    true
)
