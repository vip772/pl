package t.mma.hooks.items.emojiat

internal object EmojiAtText {
    fun users(ids: Iterable<String>, self: String): List<String> = if (self.isBlank()) emptyList() else
        ids.map { it.trim() }.filter { it.isNotEmpty() && it != self && it != "weixin" && it != "filehelper" &&
            it.none { c -> c in ",<>&]\"'" || c.isWhitespace() } }.distinct().take(499)
    fun merge(original: String?, users: List<String>): String {
        require(users.isNotEmpty())
        val node = "<atuserlist><![CDATA[${users.joinToString(",")}]]></atuserlist>"
        val source = original.orEmpty().trim()
        if (source.isEmpty()) return "<msgsource>$node</msgsource>"
        require(source.startsWith("<msgsource>") && source.endsWith("</msgsource>")) { "未知 MsgSource 格式" }
        return source.replace(Regex("(?s)<atuserlist(?:\\s[^>]*)?>.*?</atuserlist>"), "")
            .replace("</msgsource>", "$node</msgsource>")
    }
}

/** All calls are synchronized; identity maps are bounded elsewhere by per-send scope/weak keys. */
internal class EmojiAtPending(private val clock: () -> Long) {
    private data class Entry(val source: String, val created: Long)
    private val entries = LinkedHashMap<Pair<String, Long>, Entry>()
    @Synchronized fun put(talker: String, id: Long, source: String) {
        prune(); if (id <= 0) return
        if (entries.size >= 128) entries.remove(entries.keys.first())
        entries[talker to id] = Entry(source, clock())
    }
    @Synchronized fun take(talker: String, id: Long): String? { prune(); return entries.remove(talker to id)?.source }
    @Synchronized fun prune() { val now = clock(); entries.entries.removeAll { now - it.value.created >= 120_000 } }
    @Synchronized fun clear() = entries.clear()
}
