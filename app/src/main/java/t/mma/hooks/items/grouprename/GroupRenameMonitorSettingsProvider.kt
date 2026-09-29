package t.mma.hooks.items.grouprename

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class GroupRenameMonitorSettingsProvider : SimpleFeatureSettingsProvider(
    GroupRenameMonitorFeature.ID,
    "改名监控",
    "监控群成员修改群内昵称，支持系统消息、文本和卡片提醒",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
