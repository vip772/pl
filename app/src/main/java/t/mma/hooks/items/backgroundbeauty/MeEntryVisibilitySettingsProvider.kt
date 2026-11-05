package t.mma.hooks.items.backgroundbeauty

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class MeEntryVisibilitySettingsProvider : SimpleFeatureSettingsProvider(
    MeEntryVisibilityFeature.ID,
    "隐藏条目",
    "独立隐藏我页面条目、主页电脑登录状态、输入框语音图标",
    FeatureSettingsProvider.CATEGORY_PRACTICAL,
    true
)
