package t.mma.hooks.items.hiddenat

/** Replacement identity survives the synchronous sender scope and network retries. */
internal class HiddenAtPending(private val clock: () -> Long) {
    data class Entry(val users: List<String>, val created: Long)
    private val entries = LinkedHashMap<Pair<String, Long>, Entry>()
    @Synchronized fun put(talker: String, id: Long, users: List<String>) {
        prune()
        require(id > 0 && users.isNotEmpty())
        if (entries.size >= 128) entries.remove(entries.keys.first())
        entries[talker to id] = Entry(users.toList(), clock())
    }
    @Synchronized fun get(talker: String, id: Long): List<String>? {
        prune()
        return entries[talker to id]?.users
    }
    private fun prune() {
        val now = clock()
        entries.entries.removeAll { now - it.value.created >= 120_000 }
    }
    @Synchronized fun clear() = entries.clear()
}

internal object HiddenAtSource {
    /** Preserve native metadata; replace only the recipient node. Reject unknown envelopes. */
    fun merge(original: String?, users: List<String>): String {
        require(users.isNotEmpty() && users.all { it.isNotBlank() && it.none { c -> c in ",<>&]\\\"'" || c.isWhitespace() } })
        val node = "<atuserlist><![CDATA[${users.joinToString(",")}]]></atuserlist>"
        val source = original.orEmpty().trim()
        if (source.isEmpty()) return "<msgsource>$node</msgsource>"
        require(source.startsWith("<msgsource>") && source.endsWith("</msgsource>")) { "未知上行MsgSource格式" }
        val result = source.replace(Regex("(?s)<atuserlist(?:\\s[^>]*)?>.*?</atuserlist>"), "")
            .replace("</msgsource>", "$node</msgsource>")
        check(HiddenAtText.users(result.toByteArray(Charsets.UTF_8), "").toSet() == users.toSet())
        return result
    }
}
