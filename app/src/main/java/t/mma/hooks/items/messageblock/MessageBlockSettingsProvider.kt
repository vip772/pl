package t.mma.hooks.items.messageblock

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class MessageBlockSettingsProvider : SimpleFeatureSettingsProvider(
    MessageBlockFeature.ID,
    "屏蔽消息",
    "拦截指定成员或会话的消息，不显示也不弹通知",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
