package t.mma.hooks.items.moments

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class RemoveMomentsAdsSettingsProvider : SimpleFeatureSettingsProvider(
    RemoveMomentsAdsFeature.ID,
    "去除朋友圈广告",
    "阻止朋友圈广告信息解析和展示",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
