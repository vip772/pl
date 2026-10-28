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
        DexInstallScheduler.schedule(featureId(), name(), stage = DexInstallScheduler.Stage.BRIDGE, priority = -100) {
            runtime?.destroy()
            val candidate = GroupBarRuntime(context)
            runtime = candidate
            candidate.install()
        }
    }
    override fun onFeatureDestroy(context: FeatureContext) { runtime?.destroy(); runtime = null }
}