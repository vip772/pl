package t.mma.hooks.items.profileid
import t.mma.utils.HLog

import android.app.Activity
import android.content.Context
import t.mma.hooks.api.contact.WeChatChatroomChangeApi
import t.mma.hooks.api.contact.WeContactHeaderApi
import t.mma.hooks.api.core.WeChatApis
import t.mma.preferences.HchatStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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
                    java.util.concurrent.ThreadPoolExecutor(1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                        java.util.concurrent.ArrayBlockingQueue<Runnable>(128),
                        java.util.concurrent.ThreadFactory { runnable ->
                            Thread(runnable, "Hchat-GroupJoinTime").apply { isDaemon = true }
                        }).also { executor = it }
            }
        }
        @Volatile private var bindingStore: JoinTimeBindingStore? = null
        private val creationObserver = InitialGroupJoinObserver()
        private var changeSubscription: WeChatChatroomChangeApi.Subscription? = null
        private val membershipVersions = ConcurrentHashMap<String, Int>()

        @Synchronized fun startBindings(context: Context) {
            bindingStore = JoinTimeBindingStore(context)
            ensureChangeSubscription()
            creationObserver.start(hostClassLoader) { account, group, selected ->
                val store = bindingStore ?: return@start
                val run = generation.get()
                try { executor().execute {
                    if (!active || generation.get() != run || bindingStore !== store ||
                        account != JoinTimeRecovery.accountKey(hostClassLoader)) return@execute
                    try {
                        // Existing persisted groups are never retrospectively treated as new groups.
                        if (store.contains(account, group)) return@execute
                        val state = JoinTimeBindingStore.Group()
                        state.recordCreation(account, selected)
                        val current = runCatching { WeChatApis.chatrooms()?.getChatroom(group)?.memberIds?.toSet().orEmpty() }
                            .getOrDefault(emptySet())
                        state.reconcile(current, System.currentTimeMillis())
                        val native = runCatching { WeChatApis.chatrooms()?.getNativeMemberJoinTimes(group).orEmpty() }
                            .getOrDefault(emptyMap())
                        state.merge(native) { "native" }
                        state.bindInitialMembers(native[account] ?: 0L)
                        if (generation.get() != run || bindingStore !== store ||
                            account != JoinTimeRecovery.accountKey(hostClassLoader)) return@execute
                        store.write(account, group, state)
                        synchronized(cache) { cache.remove(group) }
                        WeContactHeaderApi.refreshCurrentGroupProfile(group)
                    } catch (error: Throwable) {
                        HLog.e("[Hchat:profile_group_info] 初始成员绑定保存失败", error)
                    }
                } } catch (error: java.util.concurrent.RejectedExecutionException) {
                    HLog.e("[Hchat:profile_group_info] 初始成员绑定任务未接收", error)
                }
            }
        }

        @Synchronized private fun ensureChangeSubscription() {
            if (bindingStore == null || changeSubscription?.isActive == true) return
            val changes = WeChatApis.chatroomChanges() ?: return
            changes.install()
            changeSubscription = changes.subscribe { change ->
                if (!change.mayMemberListChanged()) return@subscribe
                val group = change.chatroomId()
                val room = change.chatroom ?: return@subscribe
                val members = room.memberIds.filter { it.isNotBlank() }.toSet()
                if (group.isBlank() || members.isEmpty()) return@subscribe
                val store = bindingStore ?: return@subscribe
                val account = JoinTimeRecovery.accountKey(hostClassLoader)
                if (account.isBlank()) return@subscribe
                if (!store.contains(account, group)) return@subscribe
                val run = generation.get()
                val observedAt = System.currentTimeMillis()
                // Invalidate immediately, before queued queries can publish an older membership.
                membershipVersions.merge(group, 1, Int::plus)
                synchronized(cache) { cache.remove(group) }
                try { executor().execute {
                    if (!active || generation.get() != run || bindingStore !== store ||
                        account != JoinTimeRecovery.accountKey(hostClassLoader)) return@execute
                    try {
                        val state = store.read(account, group)
                        state.reconcile(members, observedAt)
                        store.write(account, group, state)
                        synchronized(cache) { cache.remove(group) }
                        WeContactHeaderApi.refreshCurrentGroupProfile(group)
                    } catch (error: Throwable) {
                        HLog.e("[Hchat:profile_group_info] 成员时间绑定更新失败", error)
                    }
                } } catch (error: java.util.concurrent.RejectedExecutionException) {
                    HLog.e("[Hchat:profile_group_info] 成员时间绑定任务未接收", error)
                }
            }
        }

        private fun closeBindings() {
            creationObserver.close()
            changeSubscription?.unsubscribe()
            changeSubscription = null
            bindingStore = null
            membershipVersions.clear()
        }

        @Volatile private var active = false
        @Volatile private var hostClassLoader: ClassLoader? = null
        private val generation = AtomicInteger(0)
        private var cacheAccount = ""
        private data class Snapshot(val times: Map<String, Long>, val loadedAt: Long, val complete: Boolean)
        private val cache = object : LinkedHashMap<String, Snapshot>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Snapshot>?): Boolean = size > 32
        }
        private val loadingGroups = ConcurrentHashMap<String, Int>()
        private const val REFRESH_MS = 15_000L
        @Synchronized fun installProvider(loader: ClassLoader? = null) {
            if (active) return
            active = true
            hostClassLoader = loader
            generation.incrementAndGet()
            WeContactHeaderApi.addProvider(joinTimeProvider)
            WeContactHeaderApi.addProvider(inviterProvider)
        }

        @Synchronized fun removeProvider() {
            active = false
            closeBindings()
            hostClassLoader = null
            generation.incrementAndGet()
            executor?.shutdownNow()
            executor = null
            WeContactHeaderApi.removeProvider(joinTimeProvider)
            WeContactHeaderApi.removeProvider(inviterProvider)
            synchronized(cache) { cache.clear() }
            loadingGroups.clear()
        }
        @Synchronized fun stopJoinTime() {
            closeBindings()
            generation.incrementAndGet()
            executor?.shutdownNow()
            executor = null
            synchronized(cache) { cache.clear(); cacheAccount = "" }
            loadingGroups.clear()
        }
        // 仅在资料页请求进群时间时加载当前群，注册 Provider 不触发全群消息扫描。
        @Synchronized private fun scheduleGroup(groupId: String, requestedMember: String, run: Int = generation.get()) {
            if (!active || groupId.isBlank() || generation.get() != run) return
            if (loadingGroups.size >= 32) return
            if (loadingGroups.putIfAbsent(groupId, run) != null) return
            val membershipVersion = membershipVersions[groupId] ?: 0
            val work = Runnable {
                try {
                    if (generation.get() != run || !active) return@Runnable
                    val accountAtStart = JoinTimeRecovery.accountKey(hostClassLoader)
                    val api = WeChatApis.chatrooms() ?: return@Runnable
                    val store = bindingStore
                    val state = store?.read(accountAtStart, groupId)
                    val currentMembers = runCatching { api.getChatroom(groupId)?.memberIds?.filter { it.isNotBlank() }?.toSet().orEmpty() }.getOrDefault(emptySet())
                    if (state != null && currentMembers.isNotEmpty()) {
                        state.reconcile(currentMembers, System.currentTimeMillis())
                    }
                    // Database/history failure must not prevent the independent native request.
                    val times = HashMap<String, Long>()
                    var complete = false
                    if (state != null) times.putAll(state.bindings.mapValues { it.value.time })
                    // An already-bound profile needs only membership reconciliation, never history.
                    val boundRequestedMember = state?.bindings?.containsKey(requestedMember) == true &&
                        !(requestedMember in state.initialMembers && state.bindings[requestedMember]?.source == "history")
                    try {
                        val result = if (boundRequestedMember) api.getNativeMemberJoinTimes(groupId) else if (state == null) api.getMemberJoinTimes(groupId) else
                            api.getMemberJoinTimes(groupId, state.bindings.mapValues { it.value.time }, state.floors)
                        complete = boundRequestedMember || api.isJoinTimeResultComplete(result)
                        if (state == null) times.putAll(result) else {
                            val eligible = result.filter { (id, _) ->
                                id !in state.initialMembers || api.memberJoinTimeSource(result, id) != "history"
                            }
                            state.merge(eligible) { api.memberJoinTimeSource(result, it) }
                            val creatorBinding = state.bindings[state.creator]
                            if (creatorBinding?.source == "native" || creatorBinding?.source == "server")
                                state.bindInitialMembers(creatorBinding.time)
                            times.putAll(state.bindings.mapValues { it.value.time })
                        }
                    } catch (error: Throwable) {
                        HLog.e("[Hchat:profile_group_info] 本地进群时间读取失败，继续原生补查", error)
                    }
                    fun publish(): Boolean {
                        fun valid() = active && generation.get() == run && accountAtStart.isNotBlank() &&
                            accountAtStart == JoinTimeRecovery.accountKey(hostClassLoader) &&
                            (membershipVersions[groupId] ?: 0) == membershipVersion && bindingStore === store
                        if (!valid()) return false
                        if (state != null && store != null) store.write(accountAtStart, groupId, state)
                        synchronized(cache) {
                            if (!active || generation.get() != run || accountAtStart.isBlank() ||
                                accountAtStart != JoinTimeRecovery.accountKey(hostClassLoader) ||
                                (membershipVersions[groupId] ?: 0) != membershipVersion || bindingStore !== store) return false
                            cache[groupId] = Snapshot(HashMap(times), SystemClock.elapsedRealtime(), complete)
                        }
                        WeContactHeaderApi.refreshCurrentGroupProfile(groupId)
                        return true
                    }
                    if (!publish() || boundRequestedMember) return@Runnable
                    // Local query success does not imply every member has a timestamp.
                    // Recovery is independent of local history/database availability.
                    val recovered = JoinTimeRecovery.read(hostClassLoader, groupId)
                    if (recovered.isNotEmpty()) {
                        if (state == null) times.putAll(recovered) else {
                            state.merge(recovered) { "server" }
                            state.bindInitialMembers(recovered[state.creator] ?: 0L)
                            times.clear()
                            times.putAll(state.bindings.mapValues { it.value.time })
                        }
                        publish()
                    }
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

        private fun cachedJoinTime(groupId: String, memberId: String): Long {
            ensureChangeSubscription()
            val self = JoinTimeRecovery.accountKey(hostClassLoader)
            synchronized(cache) {
                if (self != cacheAccount) {
                    generation.incrementAndGet()
                    cache.clear()
                    membershipVersions.clear()
                    loadingGroups.clear()
                    cacheAccount = self
                }
            }
            if (self.isBlank()) return 0L
            val snapshot = synchronized(cache) { cache[groupId] }
            // Retry incomplete reads as well as members with missing timestamps.
            if (snapshot == null || ((!snapshot.complete || snapshot.times[memberId] == null) &&
                    SystemClock.elapsedRealtime() - snapshot.loadedAt >= REFRESH_MS)) scheduleGroup(groupId, memberId)
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