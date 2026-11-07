package t.mma.hooks.items.profileid
import t.mma.utils.HLog

import android.app.Activity
import java.lang.ref.WeakReference
import t.mma.hooks.api.contact.WeContactHeaderApi
import t.mma.hooks.api.core.WeChatApis
import t.mma.preferences.HchatStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger
import android.os.SystemClock

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
        @Volatile private var active = false
        @Volatile private var diagnosticLoader: ClassLoader? = null
        private val generation = AtomicInteger(0)
        private var cacheAccount = ""
        private data class Snapshot(val times: Map<String, Long>, val loadedAt: Long, val complete: Boolean)
        private val cache = object : LinkedHashMap<String, Snapshot>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Snapshot>?): Boolean = size > 32
        }
        private val verifyingGroups = ConcurrentHashMap<String, Int>()
        private val loadingGroups = ConcurrentHashMap<String, Int>()
        private const val REFRESH_MS = 15_000L
        private var diagnosticActivity = WeakReference<Activity>(null)
        private var diagnosticState = ""
        private fun logDisplay(activity: Activity, state: String) {
            if (diagnosticActivity.get() === activity && diagnosticState == state) return
            diagnosticActivity = WeakReference(activity)
            diagnosticState = state
            HLog.d("[Hchat:profile_group_info] JOIN_DIAG display $state")
        }

        @Synchronized fun installProvider(loader: ClassLoader? = null) {
            if (active) return
            active = true
            diagnosticLoader = loader
            generation.incrementAndGet()
            WeContactHeaderApi.addProvider(joinTimeProvider)
            WeContactHeaderApi.addProvider(inviterProvider)
        }

        @Synchronized fun removeProvider() {
            active = false
            diagnosticLoader = null
            generation.incrementAndGet()
            executor?.shutdownNow()
            executor = null
            WeContactHeaderApi.removeProvider(joinTimeProvider)
            WeContactHeaderApi.removeProvider(inviterProvider)
            synchronized(cache) { cache.clear() }
            loadingGroups.clear()
            verifyingGroups.clear()
            diagnosticActivity.clear()
            diagnosticState = ""
        }
        @Synchronized fun stopJoinTime() {
            generation.incrementAndGet()
            executor?.shutdownNow()
            executor = null
            synchronized(cache) { cache.clear(); cacheAccount = "" }
            loadingGroups.clear()
            verifyingGroups.clear()
        }
        // 仅在资料页请求进群时间时加载当前群，注册 Provider 不触发全群消息扫描。
        @Synchronized private fun scheduleGroup(groupId: String, run: Int = generation.get()) {
            if (!active || groupId.isBlank() || generation.get() != run) return
            if (loadingGroups.size >= 32) {
                HLog.d("[Hchat:profile_group_info] JOIN_DIAG schedule group=$groupId state=capacity")
                return
            }
            if (loadingGroups.putIfAbsent(groupId, run) != null) return
            HLog.d("[Hchat:profile_group_info] JOIN_DIAG schedule group=$groupId generation=$run state=queued")
            val work = Runnable {
                try {
                    if (generation.get() != run || !active) return@Runnable
                    val accountAtStart = JoinTimeRecovery.accountKey(diagnosticLoader)
                    val api = WeChatApis.chatrooms() ?: run {
                        HLog.d("[Hchat:profile_group_info] JOIN_DIAG query group=$groupId state=api-unavailable")
                        return@Runnable
                    }
                    api.logNativeJoinGate(diagnosticLoader)
                    val result = api.getMemberJoinTimes(groupId)
                    val complete = api.isJoinTimeResultComplete(result)
                    val times = HashMap(result)
                    synchronized(cache) {
                        if (generation.get() != run || accountAtStart.isBlank() ||
                            accountAtStart != JoinTimeRecovery.accountKey(diagnosticLoader)) return@Runnable
                        cache[groupId] = Snapshot(HashMap(times), SystemClock.elapsedRealtime(), complete)
                    }
                    WeContactHeaderApi.refreshCurrentGroupProfile(groupId)
                    val recovered = JoinTimeRecovery.read(diagnosticLoader, groupId)
                    if (recovered.isEmpty()) return@Runnable
                    times.putAll(recovered)
                    synchronized(cache) {
                        if (generation.get() != run || accountAtStart.isBlank() ||
                            accountAtStart != JoinTimeRecovery.accountKey(diagnosticLoader)) return@Runnable
                        cache[groupId] = Snapshot(HashMap(times), SystemClock.elapsedRealtime(), complete)
                    }
                    HLog.d("[Hchat:profile_group_info] JOIN_DIAG cacheStored group=$groupId size=${times.size} generation=$run complete=${complete}")
                    WeContactHeaderApi.refreshCurrentGroupProfile(groupId)
                } catch (t: Throwable) {
                    HLog.e("[Hchat:profile_group_info] 群成员进群时间索引失败: $groupId", t)
                } finally {
                    loadingGroups.remove(groupId, run)
                }
            }
            try { executor().execute(work) } catch (t: Throwable) {
                loadingGroups.remove(groupId, run)
                HLog.e("[Hchat:profile_group_info] 群成员进群时间任务未接收: $groupId", t)
            }
        }

        // Runs only after an observed native write, with bounded/coalesced verification work.
        @Synchronized fun nativeWriteCompleted(groupId: String, eventId: Long, expectedHash: String) {
            val run = generation.get()
            if (!active) return
            synchronized(cache) { cache.remove(groupId) }
            if (verifyingGroups.size >= 32 || verifyingGroups.putIfAbsent(groupId, run) != null) {
                HLog.d("[Hchat:profile_group_info] JOIN_DIAG persistedRead event=$eventId group=$groupId state=coalesced-or-capacity")
                return
            }
            try {
                executor().execute {
                    try {
                        if (!active || generation.get() != run) return@execute
                        val api = WeChatApis.chatrooms()
                        if (api == null) HLog.d("[Hchat:profile_group_info] JOIN_DIAG persistedRead event=$eventId group=$groupId state=api-unavailable")
                        else api.logPersistedJoinTimes(groupId, eventId, expectedHash)
                        synchronized(cache) { if (generation.get() == run) cache.remove(groupId) }
                        WeContactHeaderApi.refreshCurrentGroupProfile(groupId)
                    } catch (error: Throwable) {
                        HLog.e("[Hchat:profile_group_info] JOIN_DIAG persistedRead event=$eventId group=$groupId failed", error)
                    } finally { verifyingGroups.remove(groupId, run) }
                }
            } catch (error: Throwable) {
                verifyingGroups.remove(groupId, run)
                HLog.e("[Hchat:profile_group_info] JOIN_DIAG persistedRead rejected", error)
            }
        }

        private fun cachedJoinTime(groupId: String, memberId: String): Long {
            val self = JoinTimeRecovery.accountKey(diagnosticLoader)
            synchronized(cache) {
                if (self != cacheAccount) {
                    generation.incrementAndGet()
                    cache.clear()
                    loadingGroups.clear()
                    cacheAccount = self
                }
            }
            if (self.isBlank()) return 0L
            val snapshot = synchronized(cache) { cache[groupId] }
            // Retry incomplete reads as well as missing members; successful native writes invalidate snapshots.
            if (snapshot == null || ((!snapshot.complete || snapshot.times[memberId] == null) &&
                    SystemClock.elapsedRealtime() - snapshot.loadedAt >= REFRESH_MS)) scheduleGroup(groupId)
            return snapshot?.times?.get(memberId) ?: 0L
        }

        private fun profile(activity: Activity): Pair<String, String>? {
            val intent = activity.intent ?: return null
            val groupId = intent.getStringExtra("Contact_ChatRoomId")
                ?: intent.getStringExtra("RoomInfo_Id")
                ?: return null
            val memberId = intent.getStringExtra("room_member")
                ?: intent.getStringExtra("Contact_User")
                ?: intent.getStringExtra("Chat_User")
                ?: intent.getStringExtra("Contact_Username")
                ?: return null
            if ((!groupId.endsWith("@chatroom") && !groupId.endsWith("@im.chatroom")) ||
                memberId.isBlank() || memberId == groupId) return null
            return groupId to memberId
        }

        private class JoinTimeProvider : WeContactHeaderApi.Provider {
            override fun getHeaderText(activity: Activity): String? {
                val (groupId, memberId) = profile(activity) ?: run {
                    logDisplay(activity, "state=profile-extras-unavailable")
                    return null
                }
                val enabled = HchatStorage.preferences(activity, ProfileGroupJoinTimeSettings.PREFS_NAME)
                    .getBoolean(ProfileGroupJoinTimeSettings.KEY_ENABLE, ProfileGroupJoinTimeSettings.DEFAULT_ENABLE)
                if (!enabled) { logDisplay(activity, "state=disabled"); return null }
                val time = cachedJoinTime(groupId, memberId)
                val snapshot = synchronized(cache) { cache[groupId] }
                logDisplay(activity, "groupHash=${groupId.hashCode().toUInt().toString(16)} memberHash=${memberId.hashCode().toUInt().toString(16)} complete=${snapshot?.complete} pending=${loadingGroups.containsKey(groupId)} state=${if (time > 0L) "text-returned" else if (snapshot == null) "loading-or-not-ready" else "member-time-missing"} snapshotAt=${snapshot?.loadedAt ?: -1L}")
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