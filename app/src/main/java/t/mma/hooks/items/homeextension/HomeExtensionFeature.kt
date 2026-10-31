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
    const val CALENDAR_EFFECT = "calendar_effect"
    const val CALENDAR_EFFECT_STYLE = "calendar_effect_style"
    const val RECENT = "recent"
    const val CALENDAR_ALPHA = "calendar_alpha"
    const val ORBIT_COLOR = "orbit_color"
    const val ORBIT_SIZE = "orbit_size"
    const val ORBIT_GAP = "orbit_gap"
    const val DEFAULT_ORBIT_SIZE = 5 // diameter, dp
    const val DEFAULT_ORBIT_GAP = 4 // avatar edge to orbit centre, dp
    const val AVATAR_ORBIT = "avatar_orbit"
    const val RECENT_ALPHA = "recent_alpha"
    const val FAVORITES = "favorites"
    const val FAVORITE_IDS = "favorite_ids"
    const val WEATHER = "weather"
    const val WEATHER_ALPHA = "weather_alpha"
    const val MUSIC = "music"
    const val MUSIC_ALPHA = "music_alpha"
    const val MUSIC_STYLE_TWO_HEIGHT = "music_style_two_height"
    const val LYRIC_COLOR = "music_lyric_color"
    const val LYRIC_ALPHA = "music_lyric_alpha"
    const val LYRIC_X = "music_lyric_x"
    const val LYRIC_Y = "music_lyric_y"
    const val MUSIC_STYLE = "music_style"
    const val MUSIC_AVATAR_SLOT = "home_music_avatar"
    const val MUSIC_THREE_AVATAR_SLOT = "home_music_three_avatar"
    const val CALENDAR_CONTENT_HEIGHT_DP = 150
    const val MUSIC_STYLE_TWO_HEIGHT_DP = CALENDAR_CONTENT_HEIGHT_DP - 45
    const val PHOTO = "photo"
    const val PHOTO_SLOT = "home_extension_photo"
    const val PHOTO_HEIGHT = "photo_height"
    const val PHOTO_WIDTH = "photo_width"
    const val PHOTO_ALPHA = "photo_alpha"
    const val CUSTOM_LAYOUT = "custom_layout"
    const val LAYOUT_ROWS = "layout_rows"
    const val ORDER = "order"
    fun order(value: String?) = (value.orEmpty().split(",").filter { it in listOf("calendar", "contacts", "photo", "weather", "music") } + listOf("calendar", "contacts", "photo", "weather", "music")).distinct()
    fun favorites(value: String?) = value.orEmpty().split(",").filter { it.isNotBlank() }.distinct().take(8)
    const val GREETING = "greeting"
    const val DEFAULT_GREETING = "愿你今天也有好心情"
    fun preferences(context: Context) = HchatStorage.preferences(context, PREFS)
}

class HomeExtensionFeature : BaseFeature() {
    private var runtime: HomeExtensionRuntime? = null
    private var runtimeOwner: t.mma.hooks.core.PreferenceRuntimeOwner? = null
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
        val prefs = HomeExtensionSettings.preferences(context.hostContext())
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val owner = t.mma.hooks.core.PreferenceRuntimeOwner(prefs, setOf(HomeExtensionSettings.ENABLE),
            enabled = { prefs.getBoolean(HomeExtensionSettings.ENABLE, false) },
            create = { resources ->
                main.post {
                    if (!resources.isClosed()) {
                        val installed = HomeExtensionRuntime(context)
                        runtime = installed
                        resources.register {
                            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) installed.destroy()
                            else main.post { installed.destroy() }
                            if (runtime === installed) runtime = null
                        }
                        try { if (!resources.isClosed()) installed.install() }
                        catch (error: Throwable) { resources.close(); logError("首页扩展安装失败", error) }
                    }
                }
            }, onError = { logError("首页扩展启停失败", it) })
        runtimeOwner = owner
        owner.start()
    }
    override fun onFeatureDestroy(context: FeatureContext) {
        runtimeOwner?.close()
        runtimeOwner = null
        runtime?.destroy()
        runtime = null
    }
    companion object { const val ID = "home_extension" }
}
