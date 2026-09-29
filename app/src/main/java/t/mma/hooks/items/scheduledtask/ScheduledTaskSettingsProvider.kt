package t.mma.hooks.items.scheduledtask

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class ScheduledTaskSettingsProvider : SimpleFeatureSettingsProvider(
    ScheduledTaskFeature.ID,
    "定时任务",
    "按计划时间发送聊天消息或发布朋友圈",
    FeatureSettingsProvider.CATEGORY_ENHANCE
)
