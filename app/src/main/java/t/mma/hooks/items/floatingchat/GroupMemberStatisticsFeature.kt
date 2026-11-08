package t.mma.hooks.items.floatingchat

import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class GroupMemberStatisticsFeature : BaseFeature() {
    override fun featureId() = ID
    override fun name() = "群员性别好友统计"
    override fun isEnabled(context: FeatureContext) = true
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(GroupMemberStatisticsSettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        val mount = ChatHeaderMount.acquire(context)
        trackResource { ChatHeaderMount.release() }
        mount.refreshVisibleOnMain()
    }
    companion object { const val ID = "floating_chat_header_member_statistics" }
}
class GroupMemberStatisticsSettingsProvider : SimpleFeatureSettingsProvider(
    GroupMemberStatisticsFeature.ID, "群员性别好友统计", "群名称下方显示性别人数与好友人数，可独立开启",
    FeatureSettingsProvider.CATEGORY_PRACTICAL, true
)
