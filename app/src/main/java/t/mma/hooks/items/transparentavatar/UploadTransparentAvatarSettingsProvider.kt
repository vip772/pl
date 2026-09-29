package t.mma.hooks.items.transparentavatar

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class UploadTransparentAvatarSettingsProvider : SimpleFeatureSettingsProvider(
    UploadTransparentAvatarFeature.ID,
    "上传透明头像",
    "上传个人头像时保留透明背景",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
