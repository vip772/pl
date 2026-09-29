package t.mma.hooks.items.quickmoments

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class QuickMomentsSettingsProvider : SimpleFeatureSettingsProvider(
    QuickMomentsFeature.ID,
    "快捷查看朋友圈",
    "长按本人、好友或企业微信联系人会话打开个人朋友圈",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
