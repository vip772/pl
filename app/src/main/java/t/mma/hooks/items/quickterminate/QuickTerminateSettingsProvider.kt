package t.mma.hooks.items.quickterminate

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class QuickTerminateSettingsProvider : SimpleFeatureSettingsProvider(
    QuickTerminateFeature.ID,
    "快捷终止",
    "从微信右上角加号菜单快速结束微信进程",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
