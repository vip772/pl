package h.Hchat.hooks.items.analysis

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import h.Hchat.dexkit.SettingsDexFinder
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.items.script.ScriptMenuDispatcher
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.ui.SimpleFeatureSettingsProvider
import h.Hchat.ui.miuix.MiuixSettingsPage

class AnalysisFeature : BaseFeature() {
    private var menu: ScriptMenuDispatcher.ScriptMenuHandle? = null

    override fun featureId(): String = ID
    override fun name(): String = "分析"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(
            SimpleFeatureSettingsProvider(
                ID,
                "分析",
                "群聊消息、成员活跃度与时间分布",
                FeatureSettingsProvider.CATEGORY_PRACTICAL
            )
        )
    }

    override fun onFeatureInstall(context: FeatureContext) {
        DexInstallScheduler.schedule("$ID:menu", "分析-消息菜单", stage = DexInstallScheduler.Stage.WARMUP) {
            val finder = SettingsDexFinder(context.dexKitBridge(), context.hostClassLoader(), context.hostContext())
            finder.resolveAll(true)
            ScriptMenuDispatcher.install(context, finder) { message, error -> logError(message, error) }
            registerMenu(context)
            true
        }
    }

    private fun registerMenu(context: FeatureContext) {
        if (!enabled(context) || menu?.isRegistered() == true) return
        menu = ScriptMenuDispatcher.registerMessageMenu(ID, null, "分析", null, true) { message ->
            val talker = message.getTalker()
            if (!message.isGroupChat()) {
                Toast.makeText(context.hostContext(), "分析仅支持群聊", Toast.LENGTH_SHORT).show()
                return@registerMessageMenu
            }
            Handler(Looper.getMainLooper()).postDelayed({
                val activity = WeChatApis.currentActivity()?.currentActivity()
                if (activity != null) MiuixSettingsPage.showAnalysis(activity, talker)
            }, 150L)
        }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        menu?.remove()
        menu = null
    }

    private fun enabled(context: FeatureContext): Boolean =
        HchatStorage.preferences(context.hostContext(), AnalysisSettings.PREFS_NAME)
            .getBoolean(AnalysisSettings.KEY_ENABLE, AnalysisSettings.DEFAULT_ENABLE)

    companion object { const val ID = "analysis" }
}