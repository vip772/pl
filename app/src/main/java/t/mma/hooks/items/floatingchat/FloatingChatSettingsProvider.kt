package t.mma.hooks.items.floatingchat

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class FloatingChatHeaderSettingsProvider : SimpleFeatureSettingsProvider(
    FloatingChatHeaderFeature.ID, "悬浮标题栏", "将聊天标题栏重排为悬浮卡片", FeatureSettingsProvider.CATEGORY_PRACTICAL, true
)
class FloatingChatFooterSettingsProvider : SimpleFeatureSettingsProvider(
    FloatingChatFooterFeature.ID, "悬浮输入框", "将聊天输入区重排为悬浮输入卡片", FeatureSettingsProvider.CATEGORY_PRACTICAL, true
)
