package h.Hchat.hooks.items.profileid

import android.app.Activity
import h.Hchat.hooks.api.contact.WeContactHeaderApi
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.preferences.HchatStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileGroupInfoFeature {
    companion object {
        private val joinTimeProvider = JoinTimeProvider()
        private val inviterProvider = InviterProvider()

        fun installProvider() {
            WeContactHeaderApi.addProvider(joinTimeProvider)
            WeContactHeaderApi.addProvider(inviterProvider)
        }

        fun removeProvider() {
            WeContactHeaderApi.removeProvider(joinTimeProvider)
            WeContactHeaderApi.removeProvider(inviterProvider)
        }

        private fun profile(activity: Activity): Pair<String, String>? {
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
            return groupId to memberId
        }

        private class JoinTimeProvider : WeContactHeaderApi.Provider {
            override fun getHeaderText(activity: Activity): String? {
                val (groupId, memberId) = profile(activity) ?: return null
                val enabled = HchatStorage.preferences(activity, ProfileGroupJoinTimeSettings.PREFS_NAME)
                    .getBoolean(ProfileGroupJoinTimeSettings.KEY_ENABLE, ProfileGroupJoinTimeSettings.DEFAULT_ENABLE)
                if (!enabled) return null
                val time = runCatching { WeChatApis.chatrooms()?.getMemberJoinTime(groupId, memberId) ?: 0L }
                    .getOrDefault(0L)
                if (time <= 0L) return null
                val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(time))
                return "进群时间：$formatted"
            }

            override fun getHeaderColorSpec(activity: Activity): String = HchatStorage
                .preferences(activity, ProfileGroupJoinTimeSettings.PREFS_NAME)
                .getString(ProfileGroupJoinTimeSettings.KEY_COLOR, ProfileGroupJoinTimeSettings.DEFAULT_COLOR)
                ?: ProfileGroupJoinTimeSettings.DEFAULT_COLOR
        }

        private class InviterProvider : WeContactHeaderApi.Provider {
            override fun getHeaderText(activity: Activity): String? {
                val (groupId, memberId) = profile(activity) ?: return null
                val enabled = HchatStorage.preferences(activity, ProfileGroupInviterSettings.PREFS_NAME)
                    .getBoolean(ProfileGroupInviterSettings.KEY_ENABLE, ProfileGroupInviterSettings.DEFAULT_ENABLE)
                if (!enabled) return null
                val api = WeChatApis.chatrooms()
                val inviterId = runCatching { api?.getMemberInviter(groupId, memberId).orEmpty() }
                    .getOrDefault("")
                val inviterName = if (inviterId.isBlank()) "自然进群"
                else api?.getMemberDisplayName(groupId, inviterId).orEmpty().ifBlank { inviterId }
                return "邀请人：$inviterName"
            }

            override fun getHeaderColorSpec(activity: Activity): String = HchatStorage
                .preferences(activity, ProfileGroupInviterSettings.PREFS_NAME)
                .getString(ProfileGroupInviterSettings.KEY_COLOR, ProfileGroupInviterSettings.DEFAULT_COLOR)
                ?: ProfileGroupInviterSettings.DEFAULT_COLOR
        }
    }
}