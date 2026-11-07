package t.mma.hooks.items.hiddenat

internal object HiddenAtText {
    private val atList = Regex("<atuserlist(?:\\s[^>]*)?>([\\s\\S]*?)</atuserlist>")
    fun users(buffer: ByteArray, self: String): List<String> = atList.find(buffer.toString(Charsets.UTF_8))?.groupValues?.get(1)
        ?.replace("<![CDATA[", "")?.replace("]]>", "")?.split(',')?.map { it.trim() }
        ?.filter { it.isNotEmpty() && it != self && it !in listOf("weixin", "filehelper") && it.none { c -> c in "<>&" } }
        ?.distinct().orEmpty()
    private val emailDomain = Regex("[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+")
    data class Match(val text: String, val removed: Int, val exact: Int)

    /** Compare complete display names literally, never as regular expressions or stripped Unicode. */
    fun match(content: String, count: Int, names: Collection<String> = emptyList()): Match? {
        if (count <= 0) return null
        val sorted = names.filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }
        val out = StringBuilder()
        var i = 0; var removed = 0; var exact = 0
        while (i < content.length) {
            if (content[i] != '@') { out.append(content[i++]); continue }
            // A native mention ends at U+2005. Read the complete token first: punctuation,
            // spaces, emoji and even another '@' can belong to the display name.
            val end = content.indexOf('\u2005', i + 1)
            if (end > i + 1) {
                val token = content.substring(i + 1, end)
                if (token in sorted || !emailAt(content, i, token)) {
                    i = end + 1
                    removed++
                    if (token in sorted) exact++
                    continue
                }
            }
            // Some host paths omit the native delimiter; require a complete known name.
            val name = sorted.firstOrNull { n ->
                content.startsWith(n, i + 1) &&
                    (i + 1 + n.length == content.length || boundary(content[i + 1 + n.length])) &&
                    !emailAt(content, i, n)
            }
            if (name != null) {
                i += name.length + 1
                if (content.getOrNull(i) == ' ') i++
                removed++; exact++
                continue
            }
            out.append(content[i++])
        }
        // Recipients are distinct IDs: the same member may be mentioned more than once.
        if (removed < count) return null
        return Match(out.toString().ifBlank { "\u200B" }, removed, exact)
    }
    fun xml(title: String, source: String): String =
        "<msg><appmsg><title>${title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</title><type>57</type></appmsg>$source</msg>"
    fun clean(content: String, count: Int): String? = match(content, count)?.text
    private fun emailChar(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "._%+-"
    private fun emailAt(content: String, at: Int, token: String): Boolean =
        at > 0 && emailChar(content[at - 1]) && emailDomain.find(token)?.let { domain ->
            domain.range.first == 0 && (domain.range.last == token.lastIndex ||
                token[domain.range.last + 1].isWhitespace())
        } == true
    private fun boundary(c: Char) = c.isWhitespace() || c in "\u2005\u00A0，。！？：；、,.!?;:()（）[]【】"
}