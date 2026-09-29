package t.mma.hooks.items.voiceforward

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class VoiceForwardSettingsProvider : SimpleFeatureSettingsProvider(
    VoiceForwardFeature.ID,
    "语音转发保存",
    "长按聊天语音或收藏语音后转发给好友/群聊，或保存到本地",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
