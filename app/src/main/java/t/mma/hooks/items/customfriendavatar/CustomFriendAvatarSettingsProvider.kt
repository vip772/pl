package t.mma.hooks.items.customfriendavatar

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class CustomFriendAvatarSettingsProvider : SimpleFeatureSettingsProvider(
    CustomFriendAvatarFeature.ID,
    "自定义头像",
    "为指定好友或群聊设置仅本地显示的头像",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
