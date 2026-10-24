package t.mma.hooks.items.mentionextension

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class MentionExtensionSettingsProvider : SimpleFeatureSettingsProvider(
    ID, "艾特扩展", "隐藏艾特、表情艾特与艾特高亮",
    FeatureSettingsProvider.CATEGORY_ENHANCE, true
) {
    companion object { const val ID = "mention_extension" }
}
