package t.mma.hooks.items.groupleave

import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.preferences.HchatStorage

class GroupLeaveMonitorFeature : BaseFeature() {

    override fun featureId(): String = ID

    override fun name(): String = "进退群监控"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(GroupLeaveMonitorSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        GroupLeaveMonitorHighlighter.install(context, ::logError)
        val prefs = HchatStorage.preferences(context.hostContext(), GroupLeaveMonitorSettings.PREFS_NAME)
        val current = java.util.concurrent.atomic.AtomicReference<GroupLeaveMonitorRuntime?>()
        val owner = PreferenceRuntimeOwner(prefs, setOf(
            GroupLeaveMonitorSettings.KEY_ENABLE, GroupLeaveMonitorSettings.KEY_INVITE_DETAIL_ENABLE,
            GroupLeaveMonitorSettings.KEY_REPLY_ENABLE, GroupLeaveMonitorSettings.KEY_JOIN_REPLY_ENABLE,
            GroupLeaveMonitorSettings.KEY_LEFT_REPLY_ENABLE),
            enabled = {
                prefs.getBoolean(GroupLeaveMonitorSettings.KEY_ENABLE, GroupLeaveMonitorSettings.DEFAULT_ENABLE) ||
                    prefs.getBoolean(GroupLeaveMonitorSettings.KEY_INVITE_DETAIL_ENABLE, GroupLeaveMonitorSettings.DEFAULT_INVITE_DETAIL_ENABLE) ||
                    prefs.getBoolean(GroupLeaveMonitorSettings.KEY_REPLY_ENABLE, GroupLeaveMonitorSettings.DEFAULT_REPLY_ENABLE)
            }, create = { resources ->
                val monitor = GroupLeaveMonitorRuntime(context, ::logError)
                current.set(monitor)
                resources.register { try { monitor.destroy() } finally { current.compareAndSet(monitor, null) } }
                monitor.install()
            }, onError = { logError("进退群监控启停失败", it) }, restartOnChange = true)
        trackResource { owner.close() }
        owner.start()
        subscribe(Events.DexReady::class.java) { owner.refresh(); current.get()?.install() }
    }
    companion object {
        const val ID = "group_leave_monitor"
    }
}
