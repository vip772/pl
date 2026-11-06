package t.mma.hooks.items.profileid

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class ProfileIdSettingsProvider : SimpleFeatureSettingsProvider(
    ProfileIdFeature.ID,
    "资料页显示",
    "在资料页显示微信 ID、添加时间、群成员进群时间、邀请人和实名尾字",
    FeatureSettingsProvider.CATEGORY_PRACTICAL,
    true
)
