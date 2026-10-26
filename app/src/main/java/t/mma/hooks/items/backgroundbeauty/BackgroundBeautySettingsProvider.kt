package t.mma.hooks.items.backgroundbeauty

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class BackgroundBeautySettingsProvider : SimpleFeatureSettingsProvider(
    BackgroundBeautyFeature.ID,
    "沉浸式背景",
    "自定义聊天和微信四个主页面背景，并调整背景透明度",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
