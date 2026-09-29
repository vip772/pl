package t.mma.hooks.items.musicorder

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class QQMusicOrderSettingsProvider : SimpleFeatureSettingsProvider(
    QQMusicOrderFeature.ID,
    "QQ点歌",
    "搜索 QQ 音乐并发送音乐卡片或歌曲语音，可同时发送",
    FeatureSettingsProvider.CATEGORY_ENTERTAINMENT
)
