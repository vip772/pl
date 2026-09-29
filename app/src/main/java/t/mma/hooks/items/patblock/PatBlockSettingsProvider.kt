package t.mma.hooks.items.patblock

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class PatBlockSettingsProvider : SimpleFeatureSettingsProvider(
    PatBlockFeature.ID,
    "禁止拍一拍",
    "双击聊天头像时不发送拍一拍",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
