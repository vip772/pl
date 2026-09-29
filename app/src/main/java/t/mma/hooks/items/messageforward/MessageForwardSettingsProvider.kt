package t.mma.hooks.items.messageforward

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class MessageForwardSettingsProvider : SimpleFeatureSettingsProvider(
    MessageForwardFeature.ID,
    "转发",
    "从消息、收藏和朋友圈菜单使用转发功能",
    FeatureSettingsProvider.CATEGORY_ENHANCE
)
