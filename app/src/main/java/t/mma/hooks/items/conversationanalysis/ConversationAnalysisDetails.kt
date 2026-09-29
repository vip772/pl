package t.mma.hooks.items.conversationanalysis

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import t.mma.hooks.api.core.WeChatApis
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

internal data class ConversationRankingRow(val sender: String, val count: Int, val name: String)
internal data class ConversationLowActivityRow(val wxId: String, val name: String, val count: Int)
internal data class ConversationDeepStats(
    val hourly: IntArray,
    val types: Map<Int, Int>,
    val avgLength: Int,
    val longest: Int,
    val questionCount: Int,
    val laughCount: Int
)
internal data class ConversationAnalysisDetails(
    val ranking: List<ConversationRankingRow>,
    val lowActivity: List<ConversationLowActivityRow>,
    val deep: ConversationDeepStats,
    val error: String? = null
)

internal class ConversationAnalysisDetailsRepository {
    fun load(talker: String, days: Int): ConversationAnalysisDetails {
        val db = WeChatApis.database() ?: return error("微信数据库API尚未初始化")
        if (!db.prepare()) return error("微信消息数据库尚未就绪")
        val start = periodStart(days)
        val end = periodEnd(days)
        val self = WeChatApis.account()?.selfWxId().orEmpty()
        val rankingSql = "SELECT CASE WHEN isSend = 1 THEN ? " +
            "WHEN instr(content, ':\\n') > 0 THEN substr(content, 1, instr(content, ':\\n') - 1) " +
            "WHEN instr(content, ':') > 0 THEN substr(content, 1, instr(content, ':') - 1) ELSE NULL END sender, " +
            "COUNT(*) cnt FROM message WHERE talker = ? AND type NOT IN (10000) " +
            "AND createTime >= ? AND createTime <= ? GROUP BY sender ORDER BY cnt DESC LIMIT 50"
        val ranking = db.query(rankingSql, arrayOf(self, talker, start.toString(), end.toString()))
            .mapNotNull { row ->
                val sender = row["sender"].text()
                if (sender.isBlank() || sender.endsWith("@chatroom") || sender.endsWith("@im.chatroom")) null
                else ConversationRankingRow(sender, row["cnt"].number().toInt(), displayName(talker, sender))
            }
        val members = WeChatApis.contacts()?.getGroupMemberIds(talker).orEmpty().distinct()
        val counts = ranking.associate { it.sender to it.count }.toMutableMap()
        val low = members.map { id -> ConversationLowActivityRow(id, displayName(talker, id), counts[id] ?: 0) }
            .sortedWith(compareBy<ConversationLowActivityRow> { it.count }.thenBy { it.name.lowercase(Locale.getDefault()) })
        val rows = db.query(
            "SELECT content, type, createTime FROM message WHERE talker = ? AND type NOT IN (10000) " +
                "AND createTime >= ? AND createTime <= ? ORDER BY createTime DESC LIMIT 50000",
            arrayOf(talker, start.toString(), end.toString())
        )
        val hourly = IntArray(24)
        val types = linkedMapOf<Int, Int>()
        var totalLength = 0
        var textRows = 0
        var longest = 0
        var questions = 0
        var laughs = 0
        rows.forEach { row ->
            val calendar = Calendar.getInstance().apply { timeInMillis = row["createTime"].number().normalizeTime() }
            hourly[calendar.get(Calendar.HOUR_OF_DAY)]++
            val type = row["type"].number().toInt()
            types[type] = (types[type] ?: 0) + 1
            val content = row["content"].text().substringAfter(":\n", row["content"].text())
            if (content.isNotBlank()) {
                textRows++
                totalLength += content.length
                longest = maxOf(longest, content.length)
                if (content.contains('?') || content.contains('？')) questions++
                if (content.contains("哈哈") || content.contains('笑')) laughs++
            }
        }
        return ConversationAnalysisDetails(ranking, low, ConversationDeepStats(hourly, types, if (textRows == 0) 0 else totalLength / textRows, longest, questions, laughs))
    }
    private fun displayName(group: String, id: String): String = runCatching {
        WeChatApis.contacts()?.getGroupMemberDisplayName(group, id).orEmpty().ifBlank { id }
    }.getOrDefault(id)
    private fun periodStart(days: Int): Long {
        if (days >= 60) return 0L
        val now = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return when (days) {
            -1 -> now.apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            -7 -> now.apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
            -30 -> now.apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
            else -> now.apply { add(Calendar.DAY_OF_YEAR, -(days.coerceAtLeast(1) - 1)) }.timeInMillis
        }
    }
    private fun periodEnd(days: Int): Long {
        if (days >= 60) return System.currentTimeMillis()
        val end = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }
        return when (days) {
            -1 -> end.apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            -7 -> end.apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            -30 -> end.apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            else -> end.timeInMillis
        }
    }
    private fun error(message: String) = ConversationAnalysisDetails(emptyList(), emptyList(), ConversationDeepStats(IntArray(24), emptyMap(), 0, 0, 0, 0), message)
    private fun Any?.text() = this?.toString().orEmpty()
    private fun Any?.number() = when (this) { is Number -> this.toLong(); else -> this?.toString()?.toLongOrNull() ?: 0L }
    private fun Long.normalizeTime(): Long = if (this in 1..10_000_000_000L) this * 1000 else this
}

internal object ConversationAnalysisDetailsDialog {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "Hchat-ConversationAnalysisDetails").apply { isDaemon = true } }
    fun show(activity: Activity, title: String, details: ConversationAnalysisDetails) {
        val dialog = Dialog(activity)
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 18), dp(activity, 16), dp(activity, 18), dp(activity, 16)); background = bg(Color.rgb(250, 250, 250), 16) }
        root.addView(TextView(activity).apply { text = title; textSize = 23f; setTextColor(Color.rgb(35, 35, 35)); setPadding(0, 0, 0, dp(activity, 12)) })
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(activity).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(TextView(activity).apply { text = "关闭"; gravity = Gravity.CENTER; textSize = 16f; setTextColor(Color.rgb(38, 150, 92)); setOnClickListener { dialog.dismiss() } }, LinearLayout.LayoutParams(-1, dp(activity, 48)))
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener { dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .92f).toInt(), (activity.resources.displayMetrics.heightPixels * .82f).toInt()) }
        dialog.show()
        if (details.error != null) { body.addView(line(activity, details.error)); return }
        body.addView(line(activity, "活跃排行"))
        details.ranking.take(20).forEachIndexed { index, row -> body.addView(line(activity, "${index + 1}. ${row.name}  ${row.count} 条")) }
        body.addView(line(activity, "低活跃成员（${details.lowActivity.count { it.count == 0 }} 人未发言）"))
        details.lowActivity.take(80).forEach { row -> body.addView(line(activity, "${row.name}  ${row.count} 条")) }
        body.addView(line(activity, "深度分析"))
        body.addView(line(activity, "平均文本长度：${details.deep.avgLength} 字，最长：${details.deep.longest} 字"))
        body.addView(line(activity, "问题消息：${details.deep.questionCount}，欢乐表达：${details.deep.laughCount}"))
        body.addView(line(activity, "活跃时段：${details.deep.hourly.indices.maxByOrNull { details.deep.hourly[it] } ?: 0} 时"))
        body.addView(line(activity, "消息类型：${details.deep.types.entries.joinToString { "${it.key}=${it.value}" }}"))
    }
    private fun line(activity: Activity, text: String) = TextView(activity).apply { this.text = text; textSize = 15f; setTextColor(Color.DKGRAY); setPadding(dp(activity, 4), dp(activity, 7), dp(activity, 4), dp(activity, 7)) }
    private fun bg(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private fun dp(activity: Activity, value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}