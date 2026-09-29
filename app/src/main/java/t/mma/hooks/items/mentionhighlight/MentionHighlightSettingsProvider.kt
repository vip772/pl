package t.mma.hooks.items.mentionhighlight

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class MentionHighlightSettingsProvider : SimpleFeatureSettingsProvider(
    MentionHighlightFeature.ID,
    "艾特高亮",
    "高亮自己和别人消息中的艾特对象，点击打开资料页；重新进入聊天后生效",
    FeatureSettingsProvider.CATEGORY_ENHANCE,
    true
)