package t.mma.hooks.items.selectedmessages

import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.model.WeChatContact
import t.mma.ui.miuix.VoiceForwardMiuixDialog
import t.mma.ui.miuix.pickerDisplayName
import java.util.Locale
import java.lang.ref.WeakReference
import t.mma.hooks.api.contact.WeChatContactApi
import t.mma.hooks.api.conversation.WeChatConversationApi

internal object SelectedMessageContactRepository {
    // Both features share values, but each installation owns its query/publication permission.
    private val lifecycleLock = Any()
    private var sessions = 0
    private var allContacts: CachedContacts? = null
    private var friendContacts: CachedContacts? = null
    class Session : AutoCloseable {
        internal var closed = false // guarded by lifecycleLock
        override fun close() {
            synchronized(lifecycleLock) {
                if (closed) return
                closed = true
                sessions--
                if (sessions == 0) { allContacts = null; friendContacts = null }
            }
        }
        fun cached(friendsOnly: Boolean): List<VoiceForwardMiuixDialog.ContactItem>? =
            synchronized(lifecycleLock) { if (closed) null else cachedLocked(friendsOnly) }
        fun load(friendsOnly: Boolean): List<VoiceForwardMiuixDialog.ContactItem> {
            val api = synchronized(lifecycleLock) {
                if (closed) return emptyList()
                cachedLocked(friendsOnly)?.let { return it }
                WeChatApis.contact().contacts()
            } ?: return emptyList()
            if (!api.isAvailable) return emptyList()
            val conversations = WeChatApis.conversations()
            val loaded = loadFresh(api, conversations, friendsOnly, this)
            return synchronized(lifecycleLock) {
                if (closed || WeChatApis.contact().contacts() !== api) return emptyList()
                cache(loaded, friendsOnly, api)
                loaded
            }
        }
        internal fun active(): Boolean = synchronized(lifecycleLock) { !closed }
    }
    fun openSession(): Session = synchronized(lifecycleLock) { sessions++; Session() }
    private fun cachedLocked(friendsOnly: Boolean): List<VoiceForwardMiuixDialog.ContactItem>? {
        val source = WeChatApis.contact().contacts()
        if (allContacts?.valid(source) != true) allContacts = null
        if (friendContacts?.valid(source) != true) friendContacts = null
        val all = allContacts
        if (!friendsOnly) return all?.items
        return friendContacts?.items
    }
    private fun loadFresh(
        api: WeChatContactApi,
        conversations: WeChatConversationApi?,
        friendsOnly: Boolean,
        session: Session
    ): List<VoiceForwardMiuixDialog.ContactItem> {
        if (!session.active()) return emptyList()
        val labelsByUser = linkedMapOf<String, MutableList<String>>()
        runCatching { api.getContactLabelList() }.getOrDefault(emptyList()).forEach { label ->
            val labelName = label.labelName.ifBlank { label.labelId }
            if (labelName.isBlank()) return@forEach
            label.userNameList.forEach { wxId ->
                if (wxId.isNotBlank()) labelsByUser.getOrPut(wxId) { arrayListOf() }.add(labelName)
            }
        }
        if (!session.active()) return emptyList()
        if (friendsOnly) {
            return api.getPickerContacts()
                .mapNotNull { contact -> contact.toPickerItem(false, labelsByUser[contact.wxId].orEmpty()) }
                .sortedWith(conversationComparator(conversations, session))
        }
        val result = ArrayList<VoiceForwardMiuixDialog.ContactItem>()
        result += api.getPickerContacts().mapNotNull { contact ->
            contact.toPickerItem(false, labelsByUser[contact.wxId].orEmpty())
        }
        if (!session.active()) return emptyList()
        result += api.getPickerOfficialAccounts().mapNotNull { contact ->
            contact.toPickerItem(group = false, labels = emptyList(), official = true)
        }
        if (!session.active()) return emptyList()
        result += api.getPickerGroups().mapNotNull { contact ->
            contact.toPickerItem(group = true, labels = emptyList())
        }
        return result.distinctBy { it.id }.sortedWith(conversationComparator(conversations, session))
    }

    private fun cache(
        contacts: List<VoiceForwardMiuixDialog.ContactItem>,
        friendsOnly: Boolean,
        source: WeChatContactApi
    ) {
        // Large accounts remain queryable without retaining oversized shared snapshots.
        if (contacts.size > MAX_CACHED_CONTACTS) return
        val cached = CachedContacts(contacts, System.nanoTime(), WeakReference(source))
        if (friendsOnly) {
            friendContacts = cached
        } else {
            allContacts = cached
            friendContacts = CachedContacts(contacts.filter(::isFriend), cached.savedAt, cached.source)
        }
    }

    private fun isFriend(item: VoiceForwardMiuixDialog.ContactItem): Boolean {
        return !item.group && !item.official
    }

    private fun conversationComparator(conversations: WeChatConversationApi?, session: Session): Comparator<VoiceForwardMiuixDialog.ContactItem> {
        val conversationOrder = (if (session.active()) conversations else null)
            ?.getRecentConversationUsernames(10000)
            .orEmpty()
            .mapIndexed { index, username -> username to index }
            .toMap()
        return compareBy<VoiceForwardMiuixDialog.ContactItem> {
            conversationOrder[it.id] ?: Int.MAX_VALUE
        }.thenBy { it.group }.thenBy { it.official }.thenBy { it.label.lowercase(Locale.US) }
    }

    private fun WeChatContact?.toPickerItem(
        group: Boolean,
        labels: List<String>,
        official: Boolean = false
    ): VoiceForwardMiuixDialog.ContactItem? {
        if (this == null || wxId.isBlank()) return null
        return VoiceForwardMiuixDialog.ContactItem(
            id = wxId,
            label = pickerDisplayName(group),
            group = group,
            official = official,
            avatarUrl = avatarUrl,
            avatarBackupUrl = avatarBackupUrl,
            labels = labels.distinct(),
            searchAliases = listOf(remarkName, nickname, customWxId)
                .filter { it.isNotBlank() }
                .distinct()
        )
    }

    private data class CachedContacts(
        val items: List<VoiceForwardMiuixDialog.ContactItem>,
        val savedAt: Long,
        val source: WeakReference<WeChatContactApi>
    ) {
        fun valid(current: WeChatContactApi?): Boolean = current != null && source.get() === current &&
            items.isNotEmpty() && System.nanoTime() - savedAt in 0..CONTACT_CACHE_NANOS
    }

    private const val CONTACT_CACHE_NANOS = 5 * 60_000_000_000L
    private const val MAX_CACHED_CONTACTS = 10_000
}
