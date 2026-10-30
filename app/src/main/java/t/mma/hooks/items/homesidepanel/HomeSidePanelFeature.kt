package t.mma.hooks.items.homesidepanel

import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class HomeSidePanelFeature : BaseFeature() {
    private var runtime: HomeSidePanelRuntime? = null

    override fun featureId(): String = ID

    override fun name(): String = "首页侧边栏"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(
            HomeSidePanelSettingsProvider()
        )
    }

    override fun onFeatureInstall(context: FeatureContext) {
        t.mma.hooks.items.quickread.QuickMarkReadRuntime.install(context)
        val installed = HomeSidePanelRuntime(context)
        runtime = installed
        trackResource { installed.destroy(); if (runtime === installed) runtime = null }
        installed.install()
        t.mma.hooks.api.core.WeChatApis.lifecycle()?.let { lifecycle ->
            trackSubscription(lifecycle.subscribe { event -> installed.onActivityEvent(event) })
        }
        t.mma.hooks.api.core.WeChatApis.chatPage()?.let { chatPage ->
            trackSubscription(chatPage.subscribe { event -> installed.onChatPageChanged(event) })
        }
        t.mma.hooks.api.core.WeChatApis.currentActivity()?.currentActivity()
            ?.let(installed::attach)
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
