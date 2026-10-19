package t.mma.hooks.items.homeextension

import android.content.Context
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider

object HomeExtensionSettings {
    const val PREFS = "Hchat_home_extension_config"
    const val ENABLE = "enable"
    const val CALENDAR = "calendar"
    const val RECENT = "recent"
    const val CALENDAR_ALPHA = "calendar_alpha"
    const val RECENT_ALPHA = "recent_alpha"
    const val FAVORITES = "favorites"
    const val FAVORITE_IDS = "favorite_ids"
    const val PHOTO = "photo"
    const val PHOTO_SLOT = "home_extension_photo"
    const val PHOTO_HEIGHT = "photo_height"
    const val PHOTO_WIDTH = "photo_width"
    const val PHOTO_ALPHA = "photo_alpha"
    const val ORDER = "order"
    fun order(value: String?) = (value.orEmpty().split(",").filter { it in listOf("calendar", "contacts", "photo") } + listOf("calendar", "contacts", "photo")).distinct()
    fun favorites(value: String?) = value.orEmpty().split(",").filter { it.isNotBlank() }.distinct().take(8)
    const val GREETING = "greeting"
    const val DEFAULT_GREETING = "愿你今天也有好心情"
    fun preferences(context: Context) = HchatStorage.preferences(context, PREFS)
}

class HomeExtensionFeature : BaseFeature() {
    private var runtime: HomeExtensionRuntime? = null
    override fun featureId() = ID
    override fun name() = "首页扩展"
    override fun isEnabled(context: FeatureContext) = true
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(object : SimpleFeatureSettingsProvider(
            ID, "首页扩展", "首页日历、问候语与最近联系人和群聊",
            FeatureSettingsProvider.CATEGORY_PRACTICAL, true
        ) {})
    }
    override fun onFeatureInstall(context: FeatureContext) {
        runtime = HomeExtensionRuntime(context).also { it.install() }
    }
    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }
    companion object { const val ID = "home_extension" }
}
