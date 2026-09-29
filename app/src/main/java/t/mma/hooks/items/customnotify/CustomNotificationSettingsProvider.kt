package t.mma.hooks.items.customnotify

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class CustomNotificationSettingsProvider : SimpleFeatureSettingsProvider(
    CustomNotificationFeature.ID,
    "自定义通知",
    "按默认规则或会话规则接管微信通知，支持铃声、震动、静默、群成员过滤、已读和快捷回复",
    FeatureSettingsProvider.CATEGORY_ENHANCE
)
