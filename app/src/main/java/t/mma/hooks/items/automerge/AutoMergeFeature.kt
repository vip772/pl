package t.mma.hooks.items.automerge

import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.utils.HLog

class AutoMergeFeature : BaseFeature() {
    private var owner: PreferenceRuntimeOwner? = null

    override fun featureId(): String = ID
    override fun name(): String = "自动勾选合并"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(AutoMergeSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = AutoMergeSettings.preferences(context.hostContext())
        owner = PreferenceRuntimeOwner(
            prefs = prefs,
            keys = setOf(AutoMergeSettings.KEY_ENABLE),
            enabled = { prefs.getBoolean(AutoMergeSettings.KEY_ENABLE, AutoMergeSettings.DEFAULT_ENABLE) },
            create = { scope -> AutoMergeRuntime(context, prefs, scope).install() },
            onError = { HLog.e("[Hchat:AutoMerge] 生命周期异常", it) }
        ).also { it.start() }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        owner?.close()
        owner = null
    }

    companion object {
        const val ID = "auto_merge"
    }
}
