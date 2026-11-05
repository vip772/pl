package t.mma.hooks.items.conversationanalysis

/** Repair model layout without decoding arbitrary escapes or changing URLs and Windows paths. */
internal object ConversationAnalysisText {
    private val protectedTokens = Regex("""(?:https?://|www\.)\S+|[A-Za-z]:\\\S+""")
    private val escapedBreaks = Regex("""\\+(?:r\\+n|n|r)""")
    private val repeatedSlashBreaks = Regex("(?:/n){2,}")
    private val excessBreaks = Regex("\n[ \t]*\n(?:[ \t]*\n)+")

    fun normalize(source: String): String {
        val text = source.replace("\r\n", "\n").replace('\r', '\n')
        val result = StringBuilder(text.length)
        var offset = 0
        protectedTokens.findAll(text).forEach { token ->
            result.append(repair(text.substring(offset, token.range.first)))
            result.append(token.value)
            offset = token.range.last + 1
        }
        result.append(repair(text.substring(offset)))
        return result.toString().replace(excessBreaks, "\n\n")
    }

    private fun repair(text: String): String = text
        .replace(escapedBreaks, "\n")
        .replace(repeatedSlashBreaks, "\n\n")
}
