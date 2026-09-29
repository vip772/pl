package t.mma.hooks.items.payment.core

import t.mma.ui.FeatureSettingsProvider

class RedPacketSettingsProvider : FeatureSettingsProvider {
    override fun featureId(): String = AutoRedPacketFeature.ID

    override fun title(): String = "自动抢红包"

    override fun subtitle(): String = "自动抢红包"

    override fun category(): String = FeatureSettingsProvider.CATEGORY_PRACTICAL
}
