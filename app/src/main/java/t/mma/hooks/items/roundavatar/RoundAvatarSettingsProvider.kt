package t.mma.hooks.items.roundavatar

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class RoundAvatarSettingsProvider : SimpleFeatureSettingsProvider(
    RoundAvatarFeature.ID,
    "圆角头像",
    "统一设置微信头像的圆角弧度",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
