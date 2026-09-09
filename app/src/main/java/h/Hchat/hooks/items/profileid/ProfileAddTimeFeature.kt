package h.Hchat.hooks.items.profileid

import android.app.Activity
import android.content.res.Configuration
import h.Hchat.hooks.api.contact.WeContactHeaderApi
import h.Hchat.hooks.api.core.WeChatApis
// Color values are stored in ProfileAddTimeSettings.
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.preferences.HchatStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileAddTimeFeature : BaseFeature(), WeContactHeaderApi.Provider {
    override fun featureId() = ID
    override fun name() = "显示添加时间"
    override fun onFeatureInit(context: FeatureContext) = Unit
    override fun onFeatureInstall(context: FeatureContext) = Unit

    companion object {
        private val provider = ProfileAddTimeFeature()
        fun installProvider(context: android.content.Context) {
            WeContactHeaderApi.addProvider(provider)
        }
        fun removeProvider() { WeContactHeaderApi.removeProvider(provider) }
        const val ID = "profile_add_time"
    }
    override fun getHeaderText(activity: Activity): String? {
        val id = resolveId(activity) ?: return null
        if (!HchatStorage.preferences(activity, ProfileAddTimeSettings.PREFS_NAME).getBoolean(ProfileAddTimeSettings.KEY_ENABLE, false)) return null
        val time = runCatching { WeChatApis.contacts()?.getContactCreateTime(id) ?: 0L }.getOrNull() ?: return null
        if (time <= 0L) return null
        return "添加时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(time))}"
    }
    private fun resolveId(activity: Activity): String? = activity.intent.getStringExtra("Contact_User")
        ?: activity.intent.getStringExtra("Contact_Username")

    override fun getHeaderColorSpec(activity: Activity): String? {
        val sp = HchatStorage.preferences(activity, ProfileAddTimeSettings.PREFS_NAME)
        val dark = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val startKey = if (dark) ProfileAddTimeSettings.KEY_LEFT_DARK_COLOR else ProfileAddTimeSettings.KEY_LEFT_LIGHT_COLOR
        val endKey = if (dark) ProfileAddTimeSettings.KEY_RIGHT_DARK_COLOR else ProfileAddTimeSettings.KEY_RIGHT_LIGHT_COLOR
        val startFallback = if (dark) ProfileAddTimeSettings.DEFAULT_LEFT_DARK_COLOR else ProfileAddTimeSettings.DEFAULT_LEFT_LIGHT_COLOR
        val endFallback = if (dark) ProfileAddTimeSettings.DEFAULT_RIGHT_DARK_COLOR else ProfileAddTimeSettings.DEFAULT_RIGHT_LIGHT_COLOR
        val start = sp.getString(startKey, startFallback) ?: startFallback
        val end = sp.getString(endKey, endFallback) ?: endFallback
        return "$start,$end"
    }

}
