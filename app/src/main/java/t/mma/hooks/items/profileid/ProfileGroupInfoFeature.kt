package t.mma.hooks.items.profileid
import t.mma.utils.HLog

import android.app.Activity
import t.mma.hooks.api.contact.WeContactHeaderApi
import t.mma.hooks.api.core.WeChatApis
import t.mma.preferences.HchatStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger

class ProfileGroupInfoFeature {
    companion object {
        private val joinTimeProvider = JoinTimeProvider()
        private val inviterProvider = InviterProvider()
@Volatile private var executor: ExecutorService? = null

        private fun executor(): ExecutorService {
            val current = executor
            if (current != null && !current.isShutdown) return current
            return synchronized(this) {
                val existing = executor
                if (existing != null && !existing.isShutdown) existing else
                    Executors.newSingleThreadExecutor { runnable ->
                        Thread(runnable, "Hchat-GroupJoinTime").apply { isDaemon = true }
                    }.also { executor = it }
            }
        }
        private val generation = AtomicInteger(0)
        private val cache = ConcurrentHashMap<String, Map<String, Long>>()
        private val loadedGroups = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
        private val loadingGroups = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

        fun installProvider() {
            generation.incrementAndGet()
            WeContactHeaderApi.addProvider(joinTimeProvider)
            WeContactHeaderApi.addProvider(inviterProvider)
        }

        fun removeProvider() {
            generation.incrementAndGet()
            executor?.shutdownNow()
            executor = null
            WeContactHeaderApi.removeProvider(joinTimeProvider)
            WeContactHeaderApi.removeProvider(inviterProvider)
            cache.clear()
            loadedGroups.clear()
            loadingGroups.clear()
        }
        // 仅在资料页请求进群时间时加载当前群，注册 Provider 不触发全群消息扫描。
        private fun scheduleGroup(groupId: String, run: Int = generation.get()) {
            if (groupId.isBlank() || generation.get() != run
                || loadedGroups.contains(groupId) || !loadingGroups.add(groupId)) return
            executor().execute {
                try {
                    val times = WeChatApis.chatrooms()?.getMemberJoinTimes(groupId).orEmpty()
                    if (generation.get() != run) return@execute
                    cache[groupId] = HashMap(times)
                    loadedGroups.add(groupId)
                    times.keys.forEach { memberId ->
                        WeContactHeaderApi.refreshCurrentProfile(groupId, memberId)
                    }
                } catch (t: Throwable) {
                    HLog.e("[Hchat:profile_group_info] 群成员进群时间索引失败: $groupId", t)
                } finally {
                    loadingGroups.remove(groupId)
                }
            }
        }

        private fun cachedJoinTime(groupId: String, memberId: String): Long {
            val groupCache = cache[groupId]
            if (groupCache == null) {
                scheduleGroup(groupId)
                return 0L
            }
            return groupCache[memberId] ?: 0L
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
                val time = cachedJoinTime(groupId, memberId)
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