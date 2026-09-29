package t.mma.hooks.items.payment.transfer

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class AutoTransferSettingsProvider : SimpleFeatureSettingsProvider(
    AutoTransferFeature.ID,
    "自动收款",
    "自动领取微信转账",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
