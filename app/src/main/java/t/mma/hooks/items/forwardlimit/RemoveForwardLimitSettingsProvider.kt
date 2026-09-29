package t.mma.hooks.items.forwardlimit

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class RemoveForwardLimitSettingsProvider : SimpleFeatureSettingsProvider(
    RemoveForwardLimitFeature.ID,
    "移除转发限制",
    "允许微信原生转发选择超过 9 个会话",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
