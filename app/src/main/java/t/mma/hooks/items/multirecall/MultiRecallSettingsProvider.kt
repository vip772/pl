package t.mma.hooks.items.multirecall

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class MultiRecallSettingsProvider : SimpleFeatureSettingsProvider(
    MultiRecallFeature.ID,
    "多选撤回",
    "在多选分享菜单中批量撤回自己发送的消息",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
