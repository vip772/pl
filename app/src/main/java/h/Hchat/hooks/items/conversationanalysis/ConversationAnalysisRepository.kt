package h.Hchat.hooks.items.conversationanalysis

import h.Hchat.hooks.api.core.WeChatApis
import java.util.Calendar

internal data class ConversationAnalysisSummary(
    val talker: String,
    val historyTotal: Long,
    val periodTotal: Int,
    val periodSpeakers: Int,
    val textCount: Int,
    val questionCount: Int,
    val laughCount: Int,
    val groupMemberCount: Int,
    val activeMemberCount: Int,
    val error: String? = null
)

internal class ConversationAnalysisRepository {
    fun loadSummary(talker: String, days: Int): ConversationAnalysisSummary {
        val cleanTalker = talker.trim()
        require(cleanTalker.endsWith("@chatroom") || cleanTalker.endsWith("@im.chatroom")) {
            "当前会话不是群聊"
        }
        val database = WeChatApis.database() ?: return unavailable(cleanTalker, "微信数据库API尚未初始化")
        if (!database.prepare()) return unavailable(cleanTalker, "微信消息数据库尚未就绪")

        val start = localPeriodStart(days)
        val end = localPeriodEnd(days)
        val self = WeChatApis.account()?.selfWxId().orEmpty()
        val sql = "SELECT COUNT(*) AS total, " +
            "COUNT(DISTINCT CASE WHEN isSend = 1 THEN ? " +
            "WHEN instr(content, ':\\n') > 0 THEN substr(content, 1, instr(content, ':\\n') - 1) " +
            "WHEN instr(content, ':') > 0 THEN substr(content, 1, instr(content, ':') - 1) ELSE NULL END) AS speakers, " +
            "SUM(CASE WHEN content IS NOT NULL AND length(content) > 0 THEN 1 ELSE 0 END) AS textCount, " +
            "SUM(CASE WHEN instr(content, '?') > 0 OR instr(content, '？') > 0 THEN 1 ELSE 0 END) AS questionCount, " +
            "SUM(CASE WHEN instr(content, '哈哈') > 0 OR instr(content, '笑') > 0 THEN 1 ELSE 0 END) AS laughCount " +
            "FROM message WHERE talker = ? AND type NOT IN (10000) AND createTime >= ? AND createTime <= ?"
        val row = database.query(
            sql,
            arrayOf(self, cleanTalker, start.toString(), end.toString())
        ).firstOrNull().orEmpty()
        val history = database.query(
            "SELECT COUNT(*) AS total FROM message WHERE talker = ?",
            arrayOf(cleanTalker)
        ).firstOrNull().orEmpty()
        val members = WeChatApis.contacts()?.getGroupMemberIds(cleanTalker).orEmpty()
        val active = queryActiveMemberCount(database, cleanTalker, start, end, self)
        return ConversationAnalysisSummary(
            talker = cleanTalker,
            historyTotal = number(history["total"]),
            periodTotal = number(row["total"]).toInt(),
            periodSpeakers = number(row["speakers"]).toInt(),
            textCount = number(row["textCount"]).toInt(),
            questionCount = number(row["questionCount"]).toInt(),
            laughCount = number(row["laughCount"]).toInt(),
            groupMemberCount = members.distinct().size,
            activeMemberCount = active
        )
    }

    private fun queryActiveMemberCount(
        database: h.Hchat.hooks.api.runtime.WeChatDatabaseApi,
        talker: String,
        start: Long,
        end: Long,
        self: String
    ): Int {
        val sql = "SELECT DISTINCT CASE WHEN isSend = 1 THEN ? " +
            "WHEN instr(content, ':\\n') > 0 THEN substr(content, 1, instr(content, ':\\n') - 1) " +
            "WHEN instr(content, ':') > 0 THEN substr(content, 1, instr(content, ':') - 1) ELSE NULL END AS sender " +
            "FROM message WHERE talker = ? AND type NOT IN (10000) AND createTime >= ? AND createTime <= ?"
        return database.query(sql, arrayOf(self, talker, start.toString(), end.toString()))
            .asSequence()
            .map { it["sender"].toString().trim() }
            .filter { it.isNotEmpty() && !it.endsWith("@chatroom") && !it.endsWith("@im.chatroom") }
            .distinct()
            .count()
    }

    private fun localPeriodStart(days: Int): Long {
        val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return when (days) {
            -1 -> start.apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            -7 -> start.apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
            -30 -> start.apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
            else -> if (days >= 60) 0L else start.apply { add(Calendar.DAY_OF_YEAR, -(days.coerceAtLeast(1) - 1)) }.timeInMillis
        }
    }
    private fun localPeriodEnd(days: Int): Long {
        val end = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }
        return when (days) {
            -1, -7 -> end.apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            -30 -> end.apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            else -> if (days >= 60) System.currentTimeMillis() else end.timeInMillis
        }
    }

    private fun number(value: Any?): Long = when (value) {
        is Number -> value.toLong()
        else -> value?.toString()?.toLongOrNull() ?: 0L
    }

    private fun unavailable(talker: String, message: String) = ConversationAnalysisSummary(
        talker = talker,
        historyTotal = 0,
        periodTotal = 0,
        periodSpeakers = 0,
        textCount = 0,
        questionCount = 0,
        laughCount = 0,
        groupMemberCount = 0,
        activeMemberCount = 0,
        error = message
    )
}
