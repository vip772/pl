package t.mma.hooks.items.tablet

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class WeChatTabletSettingsProvider : SimpleFeatureSettingsProvider(
    WeChatTabletFeature.ID,
    "平板模式",
    "伪装平板登录微信",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
