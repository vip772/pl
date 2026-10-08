package t.mma.hooks.items.dynamicbadge

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class DynamicBadgeSettingsProvider : SimpleFeatureSettingsProvider(
    DynamicBadgeSettings.ID,
    "动态角标",
    "为会话列表数字角标和红点添加动态渐变效果",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
