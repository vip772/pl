package t.mma.hooks.items.hotupdate

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class DisableHotUpdateSettingsProvider : SimpleFeatureSettingsProvider(
    DisableHotUpdateFeature.ID,
    "屏蔽热更新",
    "阻止微信加载和应用热更新补丁",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
