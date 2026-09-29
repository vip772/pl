package t.mma.hooks.items.moments

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class MomentsPostNotificationSettingsProvider : SimpleFeatureSettingsProvider(
    MomentsPostNotificationFeature.ID,
    "朋友圈发布通知",
    "指定好友发布朋友圈时提醒",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
