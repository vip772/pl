package t.mma.hooks.items.conversationanalysis

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class ConversationAnalysisSettingsProvider : SimpleFeatureSettingsProvider(
    ConversationAnalysisFeature.ID,
    "群聊分析",
    "统计群聊消息、活跃排行、聊天作息、内容特征和分析报告",
    FeatureSettingsProvider.CATEGORY_PRACTICAL,
    true
)
