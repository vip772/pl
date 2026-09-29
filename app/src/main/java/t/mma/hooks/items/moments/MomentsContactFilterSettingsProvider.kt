package t.mma.hooks.items.moments

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class MomentsContactFilterSettingsProvider : SimpleFeatureSettingsProvider(
    MomentsContactFilterFeature.ID,
    "朋友圈过滤",
    "按好友范围过滤朋友圈内容",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
