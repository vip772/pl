package t.mma.hooks.items.dynamicbadge

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class DynamicBadgeSettingsProvider : SimpleFeatureSettingsProvider(
    DynamicBadgeSettings.ID,
    "动态角标",
    "为聊天列表联系人和群组的未读角标添加动态渐变效果",
    FeatureSettingsProvider.CATEGORY_PRACTICAL,
    true
)
