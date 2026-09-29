package t.mma.hooks.items.fakescancamera

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class FakeScanCameraSettingsProvider : SimpleFeatureSettingsProvider(
    FakeScanCameraFeature.ID,
    "模拟相机扫码",
    "让相册识别二维码按相机扫码来源处理",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
