package t.mma.hooks.items.moments

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class SnsAntiRecallSettingsProvider : SimpleFeatureSettingsProvider(
    SnsAntiRecallFeature.ID,
    "朋友圈防撤回",
    "已缓存的朋友圈在对方删除或限制可见范围后继续显示",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
