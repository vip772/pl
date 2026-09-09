package h.Hchat.hooks.items.profileid

import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider

class ProfileIdSettingsProvider : SimpleFeatureSettingsProvider(
    ProfileIdFeature.ID,
    "显示微信 ID",
    "在好友和群聊资料页头部显示可复制的微信 ID",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
