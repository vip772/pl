package h.Hchat.hooks.items.script

import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider

class AnalysisSettingsProvider : SimpleFeatureSettingsProvider(
    FEATURE_ID,
    "分析",
    "群聊消息、成员活跃度与 AI 总结",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
) {
    companion object {
        const val FEATURE_ID = "builtin_analysis"
    }
}
