package t.mma.hooks.items.grouprename

import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.preferences.HchatStorage
import t.mma.hooks.items.groupleave.GroupLeaveMonitorHighlighter

class GroupRenameMonitorFeature : BaseFeature() {

    override fun featureId(): String = ID

    override fun name(): String = "改名监控"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(GroupRenameMonitorSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        GroupLeaveMonitorHighlighter.installProfileLinkClick(context, ::logError)
        val prefs = HchatStorage.preferences(context.hostContext(), GroupRenameMonitorSettings.PREFS_NAME)
        val current = java.util.concurrent.atomic.AtomicReference<GroupRenameMonitorRuntime?>()
        val owner = PreferenceRuntimeOwner(prefs, setOf(
            GroupRenameMonitorSettings.KEY_NOTICE_ENABLE, GroupRenameMonitorSettings.KEY_SEND_ENABLE),
            enabled = {
                prefs.getBoolean(GroupRenameMonitorSettings.KEY_NOTICE_ENABLE, GroupRenameMonitorSettings.DEFAULT_NOTICE_ENABLE) ||
                    prefs.getBoolean(GroupRenameMonitorSettings.KEY_SEND_ENABLE, GroupRenameMonitorSettings.DEFAULT_SEND_ENABLE)
            }, create = { resources ->
                val monitor = GroupRenameMonitorRuntime(context, ::logError)
                current.set(monitor)
                resources.register { try { monitor.destroy() } finally { current.compareAndSet(monitor, null) } }
                monitor.install()
            }, onError = { logError("改名监控启停失败", it) }, restartOnChange = true)
        trackResource { owner.close() }
        owner.start()
        subscribe(Events.DexReady::class.java) { owner.refresh(); current.get()?.install() }
    }
    companion object {
        const val ID = "group_rename_monitor"
    }
}
