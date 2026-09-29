package t.mma.hooks.items.antirecall

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class AntiRecallSettingsProvider : SimpleFeatureSettingsProvider(
    AntiRecallFeature.ID,
    "防撤回",
    "保留被撤回的消息",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
