package t.mma.hooks.items.backgroundbeauty

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class MeEntryVisibilitySettingsProvider : SimpleFeatureSettingsProvider(
    MeEntryVisibilityFeature.ID,
    "隐藏条目",
    "独立隐藏我页面的收藏、朋友圈、作品、小店与卡包、表情",
    FeatureSettingsProvider.CATEGORY_PRACTICAL,
    true
)
