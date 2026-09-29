package t.mma.hooks.items.automessageforward

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class AutoMessageForwardSettingsProvider : SimpleFeatureSettingsProvider(
    AutoMessageForwardFeature.ID,
    "消息自动转发",
    "按会话、消息类型和关键词筛选或替换后自动转发",
    FeatureSettingsProvider.CATEGORY_ENHANCE
)
