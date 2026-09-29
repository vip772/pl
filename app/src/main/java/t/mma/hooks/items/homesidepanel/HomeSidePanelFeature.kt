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
        val installed = HomeSidePanelRuntime(context).also { it.install() }
        runtime = installed
        t.mma.hooks.api.core.WeChatApis.lifecycle()?.let { lifecycle ->
            trackSubscription(lifecycle.subscribe { event -> installed.onActivityEvent(event) })
        }
        t.mma.hooks.api.core.WeChatApis.chatPage()?.let { chatPage ->
            trackSubscription(chatPage.subscribe { event -> installed.onChatPageChanged(event) })
        }
        t.mma.hooks.api.core.WeChatApis.currentActivity()?.currentActivity()
            ?.let(installed::attach)
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }

    companion object {
        const val ID = "home_side_panel"
    }
}

private class HomeSidePanelSettingsProvider : SimpleFeatureSettingsProvider(
    HomeSidePanelFeature.ID,
    "首页侧边栏",
    "从微信首页左缘右滑，打开快捷面板",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
