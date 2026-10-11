package t.mma.hooks.items.musicorder

/** Claim before enqueueing: wrapper/SQLite callbacks may report the same insert twice. */
internal class MusicCommandDeduplicator(
    private val now: () -> Long,
    private val ttlMillis: Long = 60_000L,
    private val capacity: Int = 4096
) {
    private val seen = LinkedHashMap<Key, Long>()
    private data class Key(val talker: String, val kind: Int, val id: Long, val content: String)

    @Synchronized
    fun claim(talker: String, msgId: Long, createTime: Long, content: String): Boolean {
        val time = now()
        seen.entries.removeAll { time - it.value >= ttlMillis }
        // Different local messages with the same song remain distinct. No-ID events
        // use creation time + content, not merely the song name or a debounce interval.
        val key = if (msgId > 0) Key(talker, 0, msgId, "")
            else Key(talker, 1, createTime, content)
        if (seen.containsKey(key)) return false
        while (seen.size >= capacity) {
            val iterator = seen.entries.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
        seen[key] = time
        return true
    }

    @Synchronized fun clear() { seen.clear() }
}