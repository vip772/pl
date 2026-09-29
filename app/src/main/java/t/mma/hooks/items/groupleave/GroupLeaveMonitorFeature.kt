package t.mma.hooks.items.groupleave

import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext

class GroupLeaveMonitorFeature : BaseFeature() {
    private var monitor: GroupLeaveMonitorRuntime? = null

    override fun featureId(): String = ID

    override fun name(): String = "进退群监控"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(GroupLeaveMonitorSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        GroupLeaveMonitorHighlighter.install(context, ::logError)
        val runtime = GroupLeaveMonitorRuntime(context, ::logError)
        monitor = runtime
        runtime.install { subscription ->
            trackSubscription(subscription)
        }
        subscribe(Events.DexReady::class.java) {
            runtime.preloadSnapshots()
        }
    }

    companion object {
        const val ID = "group_leave_monitor"
    }
}
