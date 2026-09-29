package t.mma.hooks.items.profileid

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class ProfileGroupInfoSettingsProvider : SimpleFeatureSettingsProvider(
    ID,
    "进入时间/邀请人",
    "在群成员资料页显示进群时间和邀请人，两个项目可独立开启",
    FeatureSettingsProvider.CATEGORY_PRACTICAL,
    true
) {
    companion object {
        const val ID = "profile_group_info"
    }
}