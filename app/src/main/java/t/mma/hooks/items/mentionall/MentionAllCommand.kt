package t.mma.hooks.items.mentionall

/** Pure policy shared by the settings page and the outgoing group-message pipeline. */
internal object MentionAllCommand {
    const val MAX_MEMBERS = 499
    const val MAX_COMMAND_LENGTH = 64
    private val excludedMembers = setOf("notify@all", "weixin", "filehelper")

    fun normalize(value: String): String = value.trim().takeIf {
        it.isNotEmpty() && it.length <= MAX_COMMAND_LENGTH && it.none(Char::isWhitespace) &&
            it.none(Char::isISOControl) && '\u200B' !in it
    } ?: MentionAllSettings.DEFAULT_COMMAND

    /** A full token at the start, not a substring or a prefix of a longer word. */
    fun body(content: String, command: String): String? {
        if (!content.startsWith(command)) return null
        if (content.length > command.length && !content[command.length].isWhitespace()) return null
        return content.substring(command.length).trimStart().ifBlank { "\u200B" }
    }

    fun recipients(members: Iterable<String>, self: String): List<String> {
        require(self.isNotBlank()) { "当前账号未就绪" }
        val result = linkedSetOf<String>()
        for (member in members) {
            if (member == self || member.isBlank() || member in excludedMembers) continue
            require(member.none { it in ",<>&]\"'" || it.isWhitespace() || it.isISOControl() }) { "群成员标识无效" }
            result += member
            if (result.size == MAX_MEMBERS) break
        }
        return result.toList()
    }
}
