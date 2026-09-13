package h.Hchat.hooks.items.profileid

import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider

class ProfileIdSettingsProvider : SimpleFeatureSettingsProvider(
    ProfileIdFeature.ID,
    "资料页显示",
    "在好友和群聊资料页头部显示微信 ID 和添加时间",
    FeatureSettingsProvider.CATEGORY_PRACTICAL,
    true
)
