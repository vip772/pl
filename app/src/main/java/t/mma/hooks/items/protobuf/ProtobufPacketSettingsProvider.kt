package t.mma.hooks.items.protobuf

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class ProtobufPacketSettingsProvider : SimpleFeatureSettingsProvider(
    ProtobufPacketFeature.ID,
    "Protobuf",
    "抓包和自定义发包",
    FeatureSettingsProvider.CATEGORY_ENTERTAINMENT
)
