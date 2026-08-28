package h.Hchat.hooks.items.analysis

import h.Hchat.hooks.api.core.WeChatApis
import java.util.Calendar

object AnalysisRepository {
    fun load(talker: String, days: Int): Result<AnalysisSnapshot> = runCatching {
        require(talker.endsWith("@chatroom") || talker.endsWith("@im.chatroom")) { "仅支持群聊" }
        val database = WeChatApis.database() ?: error("消息数据库尚未就绪")
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(days - 1).coerceAtLeast(0))
        }.timeInMillis
        val rows = database.query(
            "SELECT type, isSend, createTime, content FROM message WHERE talker=? AND createTime>=? ORDER BY createTime DESC LIMIT 50000",
            arrayOf(talker, start.toString())
        )
        val counts = LinkedHashMap<String, Int>()
        val hours = MutableList(24) { 0 }
        var text = 0
        var image = 0
        var voice = 0
        var video = 0
        rows.forEach { row ->
            val type = (row["type"] as? Number)?.toInt() ?: 0
            when (type) {
                1 -> text++
                3 -> image++
                34 -> voice++
                43, 62 -> video++
            }
            val rawTime = (row["createTime"] as? Number)?.toLong() ?: 0L
            val time = if (rawTime in 1 until 100_000_000_000L) rawTime * 1000L else rawTime
            if (time > 0L) {
                hours[Calendar.getInstance().apply { timeInMillis = time }.get(Calendar.HOUR_OF_DAY)]++
            }
            val outgoing = (row["isSend"] as? Number)?.toInt() == 1
            val content = row["content"]?.toString().orEmpty()
            val sender = if (outgoing) "我" else content.substringBefore(":\n", "未知成员").ifBlank { "未知成员" }
            counts[sender] = (counts[sender] ?: 0) + 1
        }
        val contacts = WeChatApis.contacts()
        val members = counts.entries.sortedByDescending { it.value }.map { (id, count) ->
            val name = if (id == "我" || id == "未知成员") id
            else contacts?.getDisplayName(id).orEmpty().ifBlank { id }
            AnalysisMember(id, name, count)
        }
        AnalysisSnapshot(talker, days, rows.size, text, image, voice, video, members, hours)
    }
}