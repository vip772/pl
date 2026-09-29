package t.mma.hooks.items.grouprename

import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.items.groupleave.GroupLeaveMonitorHighlighter

class GroupRenameMonitorFeature : BaseFeature() {
    private var runtime: GroupRenameMonitorRuntime? = null

    override fun featureId(): String = ID

    override fun name(): String = "改名监控"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(GroupRenameMonitorSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        GroupLeaveMonitorHighlighter.installProfileLinkClick(context, ::logError)
        val monitor = GroupRenameMonitorRuntime(context, ::logError)
        runtime = monitor
        monitor.install { subscription ->
            trackSubscription(subscription)
        }
        subscribe(Events.DexReady::class.java) {
            monitor.preloadSnapshots()
        }
    }

    companion object {
        const val ID = "group_rename_monitor"
    }
}
