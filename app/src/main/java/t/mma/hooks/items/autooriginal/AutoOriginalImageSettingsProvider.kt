package t.mma.hooks.items.autooriginal

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class AutoOriginalImageSettingsProvider : SimpleFeatureSettingsProvider(
    AutoOriginalImageFeature.ID,
    "自动勾选原图",
    "发送聊天图片时自动勾选原图",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
