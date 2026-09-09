package h.Hchat.hooks.items.profileid

import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider

class ProfileAddTimeSettingsProvider : SimpleFeatureSettingsProvider(
    ProfileAddTimeFeature.ID,
    "显示添加时间",
    "在好友资料页头部显示好友添加时间",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)