package t.mma.hooks.items.automerge

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class AutoMergeSettingsProvider : SimpleFeatureSettingsProvider(
    AutoMergeFeature.ID,
    "自动勾选合并",
    "聊天相册照片和视频合计选中3项及以上时自动勾选发送后合并展示",
    FeatureSettingsProvider.CATEGORY_PRACTICAL,
    true
)
