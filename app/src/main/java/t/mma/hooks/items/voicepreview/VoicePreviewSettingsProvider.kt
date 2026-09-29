package t.mma.hooks.items.voicepreview

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class VoicePreviewSettingsProvider : SimpleFeatureSettingsProvider(
    VoicePreviewFeature.ID,
    "语音消息预览",
    "长按聊天语音查看时长并播放",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
