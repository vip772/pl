package t.mma.hooks.items.settings

import t.mma.crash.CrashReportSettingsProvider
import t.mma.dexkit.SettingsDexFinder
import t.mma.hooks.api.ui.SettingsInjector
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.items.quickread.QuickMarkReadRuntime
import t.mma.hooks.items.quickterminate.QuickTerminateRuntime
import t.mma.ui.FeatureSettingsProvider

/**
 * 微信内设置入口注入功能。
 */
class SettingsFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "设置入口"

    @Throws(Throwable::class)
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(SettingsEntryProvider())
        registerSettingsProvider(PluginAgentEntryProvider())
        registerSettingsProvider(CrashReportSettingsProvider())
    }

    @Throws(Throwable::class)
    override fun onFeatureInstall(context: FeatureContext) {
        DexInstallScheduler.runDexKitTask {
            installSettingsHook(context)
        }
    }

    private fun installSettingsHook(context: FeatureContext) {
        val settingsDexFinder = SettingsDexFinder.managed(
            context.dexKitManager(),
            context.hostClassLoader(),
            context.hostContext()
        )
        val entrySettings = SettingsEntrySettings(context.hostContext())
        settingsDexFinder.resolveAll(
            entrySettings.plusMenuEnabled() ||
                entrySettings.pluginAgentPlusMenuEnabled() ||
                QuickMarkReadRuntime.isPlusMenuEnabled(context.hostContext()) ||
                QuickTerminateRuntime.isEnabled(context.hostContext())
        )

        val injector = SettingsInjector(
            context.hostContext(),
            context.hostClassLoader(),
            settingsDexFinder,
            entrySettings
        )
        trackResource { injector.close() }
        injector.hookSettings()
    }

    companion object {
        const val ID = "settings"
    }
}

class SettingsEntryProvider : FeatureSettingsProvider {
    override fun featureId(): String = SettingsFeature.ID

    override fun title(): String = "入口设置"

    override fun subtitle(): String = "控制加号菜单和长按加号入口"

    override fun category(): String = FeatureSettingsProvider.CATEGORY_PRACTICAL
}

class PluginAgentEntryProvider : FeatureSettingsProvider {
    override fun featureId(): String = ID

    override fun title(): String = "插件 Agent 入口"

    override fun subtitle(): String = "控制右上角加号菜单中的 Agent 入口"

    override fun category(): String = FeatureSettingsProvider.CATEGORY_PRACTICAL

    companion object {
        const val ID = "plugin_agent_entry"
    }
}
