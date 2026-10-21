package t.mma.hooks.items.hiddenat

/** Only remove WeChat's mention tokens terminated by its special separator. */
internal object HiddenAtText {
    private val users = Regex("<atuserlist>([\\s\\S]*?)</atuserlist>")
    fun users(buffer: ByteArray, self: String): List<String> = users.find(buffer.toString(Charsets.UTF_8))?.groupValues?.get(1)
        ?.replace("<![CDATA[", "")?.replace("]]>", "")?.split(',')?.map { it.trim() }
        ?.filter { it.isNotEmpty() && it != self && it !in listOf("weixin", "filehelper") && !it.contains('<') && !it.contains('>') }
        ?.distinct()?.take(200).orEmpty()
    fun clean(content: String, count: Int): String? {
        val out = StringBuilder(); var i = 0; var removed = 0
        while (i < content.length) {
            if (content[i] == '@' && removed < count && (i == 0 || content[i-1].isWhitespace() || content[i-1] in "，。！？：；、\u2005")) {
                var end = i + 1
                while (end < content.length && end - i <= 128 && content[end] != '\n' && content[end] != '\r' && content[end] !in "\u2005\u2006\u2009\u200A\u200B\u2060") end++
                if (end < content.length && content[end] in "\u2005\u2006\u2009\u200A\u200B\u2060") { i = end + 1; removed++; continue }
            }
            out.append(content[i++])
        }
        if (removed != count) return null
        return out.toString().trim().ifEmpty { "\u200B" }
    }
    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    fun xml(title: String, ids: List<String>): String = "<msg><appmsg><title>${escape(title)}</title><type>57</type></appmsg><msgsource><atuserlist><![CDATA[${ids.joinToString(",")}]]></atuserlist></msgsource></msg>"
    fun buffer(ids: List<String>): ByteArray {
        val source = "<msgsource><atuserlist><![CDATA[${ids.joinToString(",")}]]></atuserlist></msgsource>".toByteArray(Charsets.UTF_8)
        return ByteArray(source.size + 74).apply {
            this[0] = 0x7B; val n = source.size
            for (i in 0..3) this[5+i] = (n ushr (24-i*8)).toByte()
            source.copyInto(this,9); this[lastIndex] = 0x7D
        }
    }
}
