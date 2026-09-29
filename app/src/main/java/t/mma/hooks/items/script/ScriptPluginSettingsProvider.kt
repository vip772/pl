package t.mma.hooks.items.script

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class ScriptPluginSettingsProvider : SimpleFeatureSettingsProvider(
    ScriptPluginFeature.ID,
    "插件总开关",
    "启动时自动加载已启用插件",
    FeatureSettingsProvider.CATEGORY_ENHANCE
)
