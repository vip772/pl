package t.mma.hooks.items.hiddenat

internal object HiddenAtText {
    private val atList = Regex("<atuserlist(?:\\s[^>]*)?>([\\s\\S]*?)</atuserlist>")
    fun users(buffer: ByteArray, self: String): List<String> = atList.find(buffer.toString(Charsets.UTF_8))?.groupValues?.get(1)
        ?.replace("<![CDATA[", "")?.replace("]]>", "")?.split(',')?.map { it.trim() }
        ?.filter { it.isNotEmpty() && it != self && it !in listOf("weixin", "filehelper") && it.none { c -> c in "<>&" } }
        ?.distinct().orEmpty()
    data class Match(val text: String, val removed: Int, val exact: Int)

    /** Compare complete display names literally, never as regular expressions or stripped Unicode. */
    fun match(content: String, count: Int, names: Collection<String> = emptyList()): Match? {
        if (count <= 0) return null
        val sorted = names.filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }
        val out = StringBuilder()
        var i = 0; var removed = 0; var exact = 0
        while (i < content.length) {
            if (content[i] != '@') { out.append(content[i++]); continue }
            val name = sorted.firstOrNull { n ->
                content.startsWith(n, i + 1) && (i + 1 + n.length == content.length || boundary(content[i + 1 + n.length])) &&
                    (i == 0 || !emailChar(content[i - 1]) || content.getOrNull(i + 1 + n.length) == '\u2005')
            }
            if (name != null) {
                i += name.length + 1
                if (content.getOrNull(i) == '\u2005' || content.getOrNull(i) == ' ') i++
                removed++; exact++; continue
            }
            // Only the observed WeChat delimiter terminates an unknown name, not zero-width/thin spaces.
            var end = i + 1
            while (end < content.length && content[end] != '\u2005' && content[end] != '\n' && content[end] != '\r' && content[end] != '@') end++
            val token = content.substring(i + 1, end)
            if (content.getOrNull(end) == '\u2005' && token.isNotEmpty() && token.none { it == '.' || it == ' ' || it == '\t' } &&
                (i == 0 || !emailChar(content[i - 1]))) {
                i = end + 1; removed++; continue
            }
            out.append(content[i++])
        }
        if (removed == 0 || (exact == 0 && removed < count)) return null
        return Match(out.toString().ifBlank { "\u200B" }, removed, exact)
    }
    fun clean(content: String, count: Int): String? = match(content, count)?.text
    private fun emailChar(c: Char) = c.isLetterOrDigit() || c in "._%+-"
    private fun boundary(c: Char) = c.isWhitespace() || c in "\u2005\u00A0，。！？：；、,.!?;:()（）[]【】"
}