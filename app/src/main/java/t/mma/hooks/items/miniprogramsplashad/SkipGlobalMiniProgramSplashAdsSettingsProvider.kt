package t.mma.hooks.items.miniprogramsplashad

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class SkipGlobalMiniProgramSplashAdsSettingsProvider : SimpleFeatureSettingsProvider(
    SkipGlobalMiniProgramSplashAdsFeature.ID,
    "跳过全局小程序开屏广告",
    "阻止所有小程序展示启动开屏广告",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
