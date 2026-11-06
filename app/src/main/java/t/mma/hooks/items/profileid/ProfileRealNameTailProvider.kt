package t.mma.hooks.items.profileid

import android.app.Activity
import android.os.SystemClock
import t.mma.hooks.api.contact.WeContactHeaderApi
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.items.realtail.BeforeTransferNameQuery
import t.mma.hooks.items.realtail.RealNameTailStore
import t.mma.preferences.HchatStorage

/** A preference-owned profile consumer; it never enables the chat nickname renderer. */
internal class ProfileRealNameTailProvider(
    context: FeatureContext,
    private val logger: (String, Throwable?) -> Unit
) : WeContactHeaderApi.Provider, AutoCloseable {
    private val prefs = HchatStorage.preferences(context.hostContext(), ProfileRealNameTailSettings.PREFS_NAME)
    private val store = RealNameTailStore(context.hostContext())
    private val query = run {
        // Exclude the existing query descriptor store from user backups.
        HchatStorage.runtimePreferences(context.hostContext(), "Hchat_feature_real_name_tail_runtime")
        BeforeTransferNameQuery(context, logger)
    }
    private val lock = Any()
    private var closed = false
    private val results = object : LinkedHashMap<String, Result>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Result>?): Boolean = size > MAX_RESULTS
    }
    private val pending = HashMap<String, Long>()
    private var nextToken = 0L
    private val started = HashMap<String, Long>()

    override fun getHeaderText(activity: Activity): String? {
        if (!prefs.getBoolean(ProfileRealNameTailSettings.KEY_ENABLE, false)) return null
        val intent = activity.intent ?: return null
        val member = sequenceOf("room_member", "Contact_User", "Chat_User", "Contact_Username")
            .mapNotNull { intent.getStringExtra(it)?.takeIf(String::isNotBlank) }.firstOrNull() ?: return null
        if (!RealNameTailStore.isValidWxid(member)) return null
        val group = intent.getStringExtra("Contact_ChatRoomId") ?: intent.getStringExtra("RoomInfo_Id")
        val cached = store.cachedTail(member)
        val now = SystemClock.elapsedRealtime()
        val result = synchronized(lock) {
            if (closed) return null
            results[member]?.takeIf { it.name.isNotBlank() || now - it.time < RETRY_MS }
        }
        if (cached.isBlank() && result == null && RealNameTailStore.isSupportedQueryId(member)) {
            request(member, group)
        }
        return ProfileRealNameTailSettings.headerText(
            cached.ifBlank { result?.name.orEmpty() },
            prefs.getString(ProfileRealNameTailSettings.KEY_PREFIX, ProfileRealNameTailSettings.DEFAULT_PREFIX)
        )
    }

    override fun getHeaderColorSpec(activity: Activity): String =
        prefs.getString(ProfileRealNameTailSettings.KEY_COLOR, ProfileRealNameTailSettings.DEFAULT_COLOR)
            ?: ProfileRealNameTailSettings.DEFAULT_COLOR

    private fun request(member: String, group: String?) {
        val tasks = WeChatApis.tasks() ?: return
        val token = synchronized(lock) {
            if (closed) return
            val now = SystemClock.elapsedRealtime()
            started.entries.removeAll {
                if (now - it.value >= REQUEST_TIMEOUT_MS) { pending.remove(it.key); true } else false
            }
            if (pending.containsKey(member) || pending.size >= MAX_PENDING) return
            (++nextToken).also { pending[member] = it; started[member] = now }
        }
        try {
            tasks.runAsync {
                try {
                    if (!active(member, token)) return@runAsync
                    if (!query.ensureReady()) {
                        complete(member, group, token, "")
                        return@runAsync
                    }
                    tasks.runOnMain {
                        if (!active(member, token)) return@runOnMain
                        try {
                            val sent = query.query(member, group.orEmpty()) { name ->
                                complete(member, group, token, name)
                            }
                            if (!sent) complete(member, group, token, "")
                        } catch (error: Throwable) {
                            complete(member, group, token, "")
                            logger("资料页实名查询失败", error)
                        }
                    }
                } catch (error: Throwable) {
                    complete(member, group, token, "")
                    logger("资料页实名查询准备失败", error)
                }
            }
        } catch (error: Throwable) {
            complete(member, group, token, "")
            logger("资料页实名查询任务未接收", error)
        }
    }

    private fun active(member: String, token: Long): Boolean = synchronized(lock) {
        !closed && pending[member] == token && prefs.getBoolean(ProfileRealNameTailSettings.KEY_ENABLE, false)
    }

    private fun complete(member: String, group: String?, token: Long, raw: String) {
        synchronized(lock) {
            if (closed || pending[member] != token) return
            pending.remove(member)
            started.remove(member)
            results[member] = Result(RealNameTailStore.normalizeMaskedName(raw), SystemClock.elapsedRealtime())
        }
        WeContactHeaderApi.refreshCurrentProfile(group, member)
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            pending.clear()
            started.clear()
            results.clear()
        }
        try { WeContactHeaderApi.removeProvider(this) } finally { query.destroy() }
    }

    private data class Result(val name: String, val time: Long)

    private companion object {
        const val MAX_RESULTS = 64
        const val MAX_PENDING = 8
        const val REQUEST_TIMEOUT_MS = 20_000L
        const val RETRY_MS = 60_000L
    }
}