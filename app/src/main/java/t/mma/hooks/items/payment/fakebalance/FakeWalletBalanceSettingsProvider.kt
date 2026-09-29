package t.mma.hooks.items.payment.fakebalance

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class FakeWalletBalanceSettingsProvider : SimpleFeatureSettingsProvider(
    FakeWalletBalanceFeature.ID,
    "伪造零钱",
    "自定义零钱、零钱通和经营账户显示",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
