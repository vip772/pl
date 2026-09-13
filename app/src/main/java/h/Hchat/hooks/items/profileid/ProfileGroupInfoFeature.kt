package h.Hchat.hooks.items.profileid

import android.app.Activity
import h.Hchat.hooks.api.contact.WeContactHeaderApi
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.preferences.HchatStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileGroupInfoFeature : WeContactHeaderApi.Provider {
    override fun getHeaderText(activity: Activity): String? {
        val intent = activity.intent ?: return null
        val groupId = intent.getStringExtra("Contact_ChatRoomId")
            ?: intent.getStringExtra("RoomInfo_Id")
            ?: return null
        val memberId = intent.getStringExtra("room_member")
            ?: intent.getStringExtra("Contact_User")
            ?: intent.getStringExtra("Chat_User")
            ?: return null
        if ((!groupId.endsWith("@chatroom") && !groupId.endsWith("@im.chatroom")) ||
            memberId.isBlank() || memberId == groupId) return null

        val lines = ArrayList<String>(2)
        val timeEnabled = HchatStorage.preferences(activity, ProfileGroupJoinTimeSettings.PREFS_NAME)
            .getBoolean(ProfileGroupJoinTimeSettings.KEY_ENABLE, ProfileGroupJoinTimeSettings.DEFAULT_ENABLE)
        if (timeEnabled) {
            val time = runCatching { WeChatApis.chatrooms()?.getMemberJoinTime(groupId, memberId) ?: 0L }
                .getOrDefault(0L)
            if (time > 0L) {
                val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(time))
                lines += "进群时间：$formatted"
            }
        }

        val inviterEnabled = HchatStorage.preferences(activity, ProfileGroupInviterSettings.PREFS_NAME)
            .getBoolean(ProfileGroupInviterSettings.KEY_ENABLE, ProfileGroupInviterSettings.DEFAULT_ENABLE)
        if (inviterEnabled) {
            val api = WeChatApis.chatrooms()
            val inviterId = runCatching { api?.getMemberInviter(groupId, memberId).orEmpty() }.getOrDefault("")
            val inviterName = if (inviterId.isBlank()) {
                "自然进群"
            } else {
                api?.getMemberDisplayName(groupId, inviterId).orEmpty().ifBlank { inviterId }
            }
            lines += "邀请人：$inviterName"
        }
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    companion object {
        private val provider = ProfileGroupInfoFeature()

        fun installProvider() {
            WeContactHeaderApi.addProvider(provider)
        }

        fun removeProvider() {
            WeContactHeaderApi.removeProvider(provider)
        }
    }
}