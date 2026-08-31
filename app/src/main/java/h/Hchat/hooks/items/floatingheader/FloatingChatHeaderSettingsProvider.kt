package h.Hchat.hooks.items.floatingheader

import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider

class FloatingChatHeaderSettingsProvider : SimpleFeatureSettingsProvider(
    FloatingChatHeaderFeature.ID,
    "悬浮标题栏",
    "将聊天页面顶部标题栏转换为悬浮卡片样式",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
