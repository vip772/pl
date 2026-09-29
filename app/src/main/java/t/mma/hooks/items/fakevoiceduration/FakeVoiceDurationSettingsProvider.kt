package t.mma.hooks.items.fakevoiceduration

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class FakeVoiceDurationSettingsProvider : SimpleFeatureSettingsProvider(
    FakeVoiceDurationFeature.ID,
    "伪造语音时长",
    "自定义微信显示的语音时长",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
