package t.mma.hooks.items.moments

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class MomentsAutoRefreshSettingsProvider : SimpleFeatureSettingsProvider(
    MomentsAutoRefreshFeature.ID,
    "朋友圈自动刷新",
    "按设定间隔获取新的朋友圈内容",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
