package t.mma.hooks.items.moments

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class MomentsAutoForwardSettingsProvider : SimpleFeatureSettingsProvider(
    MomentsAutoForwardFeature.ID,
    "朋友圈自动转发",
    "按好友和内容规则静默转发朋友圈",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
