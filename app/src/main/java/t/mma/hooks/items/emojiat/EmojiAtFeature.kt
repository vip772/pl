package t.mma.hooks.items.emojiat

import android.content.Context
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.preferences.HchatStorage

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
    override fun onFeatureInstall(context: FeatureContext) {
        if (context.loadPackageParam().processName != context.loadPackageParam().packageName) return
        val installed = EmojiAtRuntime(context)
        runtime = installed
        installed.install()
    }
    override fun onFeatureDestroy(context: FeatureContext) { runtime?.destroy(); runtime = null }
}

