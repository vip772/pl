package t.mma.hooks.items.membertitle

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class MemberTitleSettingsProvider : SimpleFeatureSettingsProvider(
    MemberTitleFeature.ID,
    "群员头衔",
    "在群聊昵称左侧显示群主、管理员、群员或自定义头衔",
    FeatureSettingsProvider.CATEGORY_ENTERTAINMENT
)
