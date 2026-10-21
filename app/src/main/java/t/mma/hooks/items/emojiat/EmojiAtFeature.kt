package t.mma.hooks.items.emojiat

import android.content.Context
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

object EmojiAtSettings {
    const val ID = "emoji_at"
    const val PREFS = "Hchat_emoji_at_config"
    const val ENABLE = "enable"
    fun preferences(c: Context) = HchatStorage.preferences(c, PREFS)
}

class EmojiAtFeature : BaseFeature() {
    private var runtime: EmojiAtRuntime? = null
    override fun featureId() = EmojiAtSettings.ID
    override fun name() = "表情艾特"
    override fun isEnabled(context: FeatureContext) = true
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(object : SimpleFeatureSettingsProvider(
            EmojiAtSettings.ID, "表情艾特", "发送群聊表情时选择普通发送或隐藏成员艾特",
            FeatureSettingsProvider.CATEGORY_ENHANCE, true
        ) {}, enabledCheck = { EmojiAtSettings.preferences(context.hostContext()).getBoolean(EmojiAtSettings.ENABLE, false) })
    }
    override fun onFeatureInstall(context: FeatureContext) {
        if (context.loadPackageParam().processName != context.loadPackageParam().packageName) return
        runtime = EmojiAtRuntime(context).also { it.install() }
    }
    override fun onFeatureDestroy(context: FeatureContext) { runtime?.destroy(); runtime = null }
}

