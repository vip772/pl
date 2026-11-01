package t.mma.hooks.items.floatingshortcut

import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

class FloatingShortcutFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "悬浮快捷菜单"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(FloatingShortcutSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val owner = FloatingShortcutRuntime.acquire(context.hostContext())
        trackResource { owner.close() }
        // Activity 由早期注册的 Application 回调统一跟踪，避免重复派发挂载任务。
        trackSubscription(
            WeChatApis.chatPage()?.subscribe { event ->
                owner.onChatPageChanged(event.isEnter)
            }
        )
        WeChatApis.currentActivity()?.currentActivity()?.let(owner::attach)
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        // Exact installation owner is released by the feature scope.
    }

    companion object {
        const val ID = "floating_shortcut_menu"
    }
}

private class FloatingShortcutSettingsProvider : SimpleFeatureSettingsProvider(
    FloatingShortcutFeature.ID,
    "悬浮快捷菜单",
    "展开插件 Agent、自定义快捷项或微信页面",
    FeatureSettingsProvider.CATEGORY_PRACTICAL
)
