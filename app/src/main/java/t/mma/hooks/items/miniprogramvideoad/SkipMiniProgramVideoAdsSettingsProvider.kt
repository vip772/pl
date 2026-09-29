package t.mma.hooks.items.miniprogramvideoad

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class SkipMiniProgramVideoAdsSettingsProvider : SimpleFeatureSettingsProvider(
    SkipMiniProgramVideoAdsFeature.ID,
    "跳过小程序视频广告",
    "自动跳过小程序视频广告",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
