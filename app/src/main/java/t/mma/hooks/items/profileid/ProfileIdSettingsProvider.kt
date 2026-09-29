package t.mma.hooks.items.profileid

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class ProfileIdSettingsProvider : SimpleFeatureSettingsProvider(
    ProfileIdFeature.ID,
    "资料页显示",
    "在好友和群聊资料页头部显示微信 ID 和添加时间",
    FeatureSettingsProvider.CATEGORY_PRACTICAL,
    true
)
