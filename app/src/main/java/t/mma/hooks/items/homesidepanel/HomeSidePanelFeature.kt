package t.mma.hooks.items.homesidepanel

import android.content.SharedPreferences
import t.mma.hooks.core.FeatureResourceScope
import t.mma.utils.KavaReflector
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class HomeSidePanelFeature : BaseFeature() {
    private var runtime: HomeSidePanelRuntime? = null
    private var runningResources: FeatureResourceScope? = null

    override fun featureId(): String = ID

    override fun name(): String = "首页侧边栏"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(
            HomeSidePanelSettingsProvider()
        )
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val preferences = HomeSidePanelSettings.preferences(context.hostContext())
        var active = true
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == HomeSidePanelSettings.KEY_ENABLE || key == null) {
                synchronized(this) { if (active) updateRuntime(context, preferences) }
            }
        }
        trackResource {
            synchronized(this) {
                active = false
                preferences.unregisterOnSharedPreferenceChangeListener(listener)
                stopRuntime()
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        updateRuntime(context, preferences)
    }
    @Synchronized
    private fun updateRuntime(context: FeatureContext, preferences: SharedPreferences) {
        if (!preferences.getBoolean(HomeSidePanelSettings.KEY_ENABLE, HomeSidePanelSettings.DEFAULT_ENABLE)) {
            stopRuntime()
            return
        }
        if (runningResources != null) return
        val resources = FeatureResourceScope { logError("侧边栏运行时释放失败", it) }
        runningResources = resources
        try {
            // Shared read-marker service has other consumers and is not released with this feature.
            t.mma.hooks.items.quickread.QuickMarkReadRuntime.install(context)
            val installed = HomeSidePanelRuntime(context)
            runtime = installed
            resources.register { installed.destroy(); if (runtime === installed) runtime = null }
            installed.install()
            t.mma.hooks.api.core.WeChatApis.lifecycle()?.let { lifecycle ->
                registerSubscription(resources, lifecycle.subscribe { event -> installed.onActivityEvent(event) })
            }
            t.mma.hooks.api.core.WeChatApis.chatPage()?.let { chatPage ->
                registerSubscription(resources, chatPage.subscribe { event -> installed.onChatPageChanged(event) })
            }
            t.mma.hooks.api.core.WeChatApis.currentActivity()?.currentActivity()?.let(installed::attach)
        } catch (error: Throwable) {
            stopRuntime()
            logError("侧边栏运行时启动失败", error)
        }
    }
    private fun registerSubscription(resources: FeatureResourceScope, subscription: Any?) {
        if (subscription != null) resources.register {
            KavaReflector.invoke(KavaReflector.findMethod(subscription.javaClass, "unsubscribe"), subscription)
        }
    }
    private fun stopRuntime() {
        val resources = runningResources
        runningResources = null
        resources?.close()
    }
    companion object {
        const val ID = "home_side_panel"
    }
}

private class HomeSidePanelSettingsProvider : SimpleFeatureSettingsProvider(
    HomeSidePanelFeature.ID,
    "首页侧边栏",
    "左边缘右划或点击微信首页头像昵称，打开快捷面板",
    FeatureSettingsProvider.CATEGORY_PRACTICAL,
    true
)
