package t.mma.hooks.items.profileid

import android.app.Activity
import t.mma.hooks.api.contact.WeContactHeaderApi
import t.mma.hooks.api.core.WeChatApis
// Settings are in the same package.
import t.mma.preferences.HchatStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileAddTimeFeature : WeContactHeaderApi.Provider {
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
        return sp.getString(ProfileAddTimeSettings.KEY_COLOR, ProfileAddTimeSettings.DEFAULT_COLOR)
            ?: ProfileAddTimeSettings.DEFAULT_COLOR
    }

}
