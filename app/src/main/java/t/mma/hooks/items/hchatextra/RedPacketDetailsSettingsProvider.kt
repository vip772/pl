package t.mma.hooks.items.hchatextra

import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class RedPacketDetailsSettingsProvider : SimpleFeatureSettingsProvider(
    FEATURE_ID,
    "红包显示详情",
    "红包详情页显示金额、个数和领取时间",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
) {
    companion object {
        const val FEATURE_ID = "hchat_red_packet_details"
    }
}
