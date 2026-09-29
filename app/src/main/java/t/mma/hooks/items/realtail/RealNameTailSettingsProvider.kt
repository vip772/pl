package t.mma.hooks.items.realtail

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class RealNameTailSettingsProvider : SimpleFeatureSettingsProvider(
    RealNameTailFeature.ID,
    "实名尾字",
    "群聊里自动补查并显示实名尾字",
    FeatureSettingsProvider.CATEGORY_ENTERTAINMENT
)
