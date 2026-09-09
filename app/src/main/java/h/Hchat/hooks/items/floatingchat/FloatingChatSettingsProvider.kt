package h.Hchat.hooks.items.floatingchat

import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider

class FloatingChatHeaderSettingsProvider : SimpleFeatureSettingsProvider(
    FloatingChatHeaderFeature.ID, "悬浮标题栏", "将聊天标题栏重排为悬浮卡片", FeatureSettingsProvider.CATEGORY_ENHANCE
)
class FloatingChatFooterSettingsProvider : SimpleFeatureSettingsProvider(
    FloatingChatFooterFeature.ID, "悬浮输入框", "将聊天输入区重排为悬浮输入卡片", FeatureSettingsProvider.CATEGORY_ENHANCE
)
