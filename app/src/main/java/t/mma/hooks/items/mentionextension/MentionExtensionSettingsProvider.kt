package t.mma.hooks.items.mentionextension

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class MentionExtensionSettingsProvider : SimpleFeatureSettingsProvider(
    ID, "艾特扩展", "艾特全体、隐藏艾特、表情艾特、艾特高亮、艾特提醒与头像提醒",
    FeatureSettingsProvider.CATEGORY_ENHANCE, true
) {
    companion object { const val ID = "mention_extension" }
}
