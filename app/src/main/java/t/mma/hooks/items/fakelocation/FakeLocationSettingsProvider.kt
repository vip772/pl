package t.mma.hooks.items.fakelocation

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class FakeLocationSettingsProvider : SimpleFeatureSettingsProvider(
    FakeLocationFeature.ID,
    "虚拟定位",
    "将微信获取到的位置固定为预设经纬度",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
