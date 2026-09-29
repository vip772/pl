package t.mma.hooks.items.quickread

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class QuickMarkReadSettingsProvider : SimpleFeatureSettingsProvider(
    QuickMarkReadFeature.ID,
    "快捷已读",
    "拖拽未读角标或加号菜单一键已读",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
