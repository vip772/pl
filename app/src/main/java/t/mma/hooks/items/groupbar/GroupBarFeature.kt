package t.mma.hooks.items.groupbar

import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.DexInstallScheduler
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class GroupBarSettingsProvider : SimpleFeatureSettingsProvider(
    GroupBarStore.ID, "分组横栏", "在首页标题栏下切换独立会话分组",
    FeatureSettingsProvider.CATEGORY_ENHANCE, true
)
class GroupBarFeature : BaseFeature() {
    private var runtime: GroupBarRuntime? = null
    override fun featureId() = GroupBarStore.ID
    override fun name() = "分组横栏"
    override fun onFeatureInit(context: FeatureContext) { registerSettingsProvider(GroupBarSettingsProvider()) }
    override fun onFeatureInstall(context: FeatureContext) {
        val lock = Any()
        var closed = false
        val ticket = DexInstallScheduler.installation(featureId(), name(),
            stage = DexInstallScheduler.Stage.BRIDGE, priority = -100)
        trackResource { synchronized(lock) {
            closed = true
            ticket.close()
            val old = runtime
            runtime = null
            old?.destroy()
        } }
        fun attempt(): Boolean = synchronized(lock) {
            if (closed) return@synchronized true
            runtime?.destroy()
            GroupBarRuntime(context).also { runtime = it }.install()
        }
        if (!attempt()) ticket.schedule { attempt() }
    }
}