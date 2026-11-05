package t.mma.hooks.items.conversationanalysis

/** A bounded latest-message query; full-period statistics use their own repository. */
internal object ConversationAnalysisInput {
    const val MAX_MESSAGES = 1500
    fun messageLimit(configured: Int): Int =
        if (configured <= 0) MAX_MESSAGES else configured.coerceAtMost(MAX_MESSAGES)
    fun latestMessagesSql(limit: Int): String =
        "SELECT content,isSend,type FROM message WHERE talker = ? AND type = 1 AND createTime >= ? AND createTime <= ? ORDER BY createTime DESC LIMIT ${messageLimit(limit)}"
}
