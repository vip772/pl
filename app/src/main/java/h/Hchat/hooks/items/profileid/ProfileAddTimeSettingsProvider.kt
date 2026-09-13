package h.Hchat.hooks.items.profileid

import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider

class ProfileAddTimeSettingsProvider : SimpleFeatureSettingsProvider(
    ID,
    "添加时间",
    "在好友和群聊资料页显示添加时间",
    FeatureSettingsProvider.CATEGORY_PRACTICAL,
    true
) {
    companion object {
        const val ID = "profile_add_time"
    }
}
