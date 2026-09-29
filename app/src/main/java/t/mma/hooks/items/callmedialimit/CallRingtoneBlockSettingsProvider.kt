package t.mma.hooks.items.callmedialimit

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class CallRingtoneBlockSettingsProvider : SimpleFeatureSettingsProvider(
    CallRingtoneBlockFeature.ID,
    "屏蔽通话铃声",
    "分别屏蔽微信通话的呼入铃声和呼出等待铃声",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
