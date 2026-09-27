package h.Hchat.hooks.items.conversationanalysis

import android.content.Context
import h.Hchat.hooks.api.core.WeChatApis
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

internal class ConversationAnalysisAi(private val context: Context) {
    private val configStore = ConversationAnalysisAiConfigStore(context)
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "Hchat-ConversationAnalysisAI").apply { isDaemon = true } }
    private val requestToken = AtomicLong(0L)
    @Volatile private var activeConnection: HttpURLConnection? = null

    fun configured(): Boolean = config().let { it.address.isNotBlank() && it.key.isNotBlank() && it.model.isNotBlank() }

    fun profiles(): List<ConversationAiProfile> = (1..ConversationAnalysisAiConfigStore.PROFILE_COUNT).map { configStore.profile(it) }
    fun activeProfileId(): Int = configStore.activeProfileId()
    fun selectProfile(id: Int) = configStore.setActiveProfile(id)

    fun generate(talker: String, days: Int, onResult: (String) -> Unit) {
        generateStreaming(talker, days, onResult, onResult)
    }

    fun generateStreaming(talker: String, days: Int, onUpdate: (String) -> Unit, onFinished: (String) -> Unit) {
        val token = requestToken.incrementAndGet()
        activeConnection?.disconnect()
        executor.execute {
            val result = runCatching { requestStreaming(talker, days, token, onUpdate) }
                .getOrElse { "AI 总结失败：${it.message ?: it.javaClass.simpleName}" }
            android.os.Handler(android.os.Looper.getMainLooper()).post { if (token == requestToken.get()) onFinished(result) }
        }
    }

    fun cancel() {
        requestToken.incrementAndGet()
        activeConnection?.disconnect()
        activeConnection = null
    }

    fun save(address: String, path: String, key: String, model: String) {
        val current = configStore.activeProfile()
        configStore.save(current.copy(address = address, path = path, key = key, model = model))
    }

    fun saveProfile(profile: ConversationAiProfile) = configStore.save(profile)
    fun testConnection(address: String, path: String, key: String, onResult: (Boolean, String) -> Unit) {
        executor.execute {
            val result: Pair<Boolean, String> = runCatching<Pair<Boolean, String>> {
                val conn = URL(join(address, path)).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"; conn.doOutput = true; conn.connectTimeout = 10000; conn.readTimeout = 10000
                    conn.setRequestProperty("Authorization", "Bearer $key"); conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write("{}".toByteArray(StandardCharsets.UTF_8)) }
                    val code = conn.responseCode
                    Pair(code in 200..499, "HTTP $code")
                } finally { conn.disconnect() }
            }.getOrElse { Pair(false, it.message ?: it.javaClass.simpleName) }
            android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(result.first, result.second) }
        }
    }
    fun fetchModels(address: String, key: String, onResult: (List<String>, String?) -> Unit) {
        executor.execute {
            val result = runCatching {
                val conn = URL(join(address, "models")).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "GET"; conn.connectTimeout = 10000; conn.readTimeout = 10000
                    conn.setRequestProperty("Authorization", "Bearer $key")
                    val response = BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
                    val data = JSONObject(response).optJSONArray("data") ?: JSONArray()
                    (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
                } finally { conn.disconnect() }
            }.fold(onSuccess = { it to null }, onFailure = { emptyList<String>() to (it.message ?: it.javaClass.simpleName) })
            android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(result.first, result.second) }
        }
    }
    private fun requestStreaming(talker: String, days: Int, token: Long, onUpdate: (String) -> Unit): String {
        val cfg = config()
        require(configured()) { "请先配置 AI 地址、密钥和模型" }
        val db = WeChatApis.database() ?: error("数据库API尚未初始化")
        require(db.prepare()) { "消息数据库尚未就绪" }
        val start = aiPeriodStart(days)
        val end = aiPeriodEnd(days)
        val limit = configStore.sampleLimit().let { if (it == 0) 3000 else it }
        val rows = db.query("SELECT content,isSend,type FROM message WHERE talker = ? AND type NOT IN (10000) AND createTime >= ? AND createTime <= ? ORDER BY createTime ASC LIMIT $limit", arrayOf(talker, start.toString(), end.toString()))
        val records = rows.mapNotNull { row ->
            val raw = row["content"].toString()
            raw.substringAfter(":\n", raw).replace('\n', ' ').trim().takeIf { it.isNotBlank() }?.take(500)
        }
        require(records.isNotEmpty()) { "所选时段暂无文本消息" }
        val prompt = "你是群聊分析报告生成器。请严格按照以下固定格式输出中文报告，只输出报告正文，不要 Markdown 代码块、不要额外解释。只能依据聊天记录，不得编造；没有证据时写‘暂无明确信息’。每个章节之间空一行，消息引用必须单独成行。格式如下：\\n\\n整体氛围\\n用一段话概括群聊整体氛围。\\n\\n重点人物与群体画像\\n用一段话概括重点人物和群体画像。\\n\\n有趣的点\\n列出 2-4 条有证据的有趣观察。\\n\\n主题/话题\\n概括主要讨论主题。\\n\\n一、主题名称\\n本节内容概述。\\n◆ 关键消息人名：消息原文\\n◆ 关键消息人名：消息原文\\n结论：本节结论。\\n\\n二、主题名称\\n本节内容概述。\\n◆ 关键消息人名：消息原文\\n结论：本节结论。\\n\\n最后补充‘有趣的点’或‘总结’章节。标题使用中文，章节数量根据记录决定。\\n\\n群聊记录：\\n" + records.joinToString("\\n")
        val body = JSONObject().put("model", cfg.model).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt))).put("stream", true)
        val conn = URL(join(cfg.address, cfg.path)).openConnection() as HttpURLConnection
        activeConnection = conn
        val output = StringBuilder()
        var result = ""
        try {
            conn.requestMethod = "POST"; conn.doOutput = true; conn.connectTimeout = 15000; conn.readTimeout = 60000
            conn.setRequestProperty("Authorization", "Bearer ${cfg.key}"); conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}: ${conn.errorStream?.bufferedReader()?.readText()}")
            BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { reader ->
                var line: String? = null
                while (token == requestToken.get() && reader.readLine().also { line = it } != null) {
                    val data = line.orEmpty().trim().removePrefix("data:").trim()
                    // Parse the response once below so non-stream responses are not duplicated.
                    if (data.isBlank() || data == "[DONE]") continue
                    val chunk = runCatching {
                        val choice = JSONObject(data).optJSONArray("choices")?.optJSONObject(0)
                        choice?.optJSONObject("delta")?.optString("content")?.takeIf { it.isNotBlank() }
                            ?: choice?.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }
                            ?: JSONObject(data).optString("content").takeIf { it.isNotBlank() }
                            ?: ""
                    }.getOrDefault("")
                    if (chunk.isNotEmpty()) {
                        output.append(chunk)
                        val snapshot = output.toString()
                        android.os.Handler(android.os.Looper.getMainLooper()).post { if (token == requestToken.get()) onUpdate(snapshot) }
                    }
                }
            }
            result = if (output.isNotEmpty()) output.toString() else request(talker, days)
        } finally {
            if (activeConnection === conn) activeConnection = null
            conn.disconnect()
        }
        return result
    }

    private fun request(talker: String, days: Int): String {
        val cfg = config()
        require(configured()) { "请先配置 AI 地址、密钥和模型" }
        val db = WeChatApis.database() ?: error("数据库API尚未初始化")
        require(db.prepare()) { "消息数据库尚未就绪" }
        val start = aiPeriodStart(days)
        val end = aiPeriodEnd(days)
        val rows = db.query("SELECT content,isSend,type FROM message WHERE talker = ? AND type NOT IN (10000) AND createTime >= ? AND createTime <= ? ORDER BY createTime ASC LIMIT 3000", arrayOf(talker, start.toString(), end.toString()))
        val records = rows.mapNotNull { row ->
            val content = row["content"].toString().substringAfter(":\n", row["content"].toString()).replace('\n', ' ').trim()
            content.takeIf { it.isNotBlank() }?.take(500)
        }
        require(records.isNotEmpty()) { "所选时段暂无文本消息" }
        val prompt = "你是群聊分析报告生成器。请严格按照固定格式输出中文报告，只输出报告正文，不要 Markdown 代码块、不要额外解释。只能依据聊天记录，不得编造；没有证据时写‘暂无明确信息’。每个章节之间空一行，消息引用必须单独成行。格式如下：\n\n整体氛围\n用一段话概括群聊整体氛围。\n\n重点人物与群体画像\n用一段话概括重点人物和群体画像。\n\n有趣的点\n列出 2-4 条有证据的有趣观察。\n\n主题/话题\n概括主要讨论主题。\n\n一、主题名称\n本节内容概述。\n◆ 关键消息人名：消息原文\n◆ 关键消息人名：消息原文\n结论：本节结论。\n\n二、主题名称\n本节内容概述。\n◆ 关键消息人名：消息原文\n结论：本节结论。\n\n最后补充‘有趣的点’或‘总结’章节。标题使用中文，章节数量根据记录决定。\n\n群聊记录：\n" + records.joinToString("\n")
        val body = JSONObject().put("model", cfg.model).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt))).put("stream", false)
        val conn = URL(join(cfg.address, cfg.path)).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"; conn.doOutput = true; conn.connectTimeout = 15000; conn.readTimeout = 60000
            conn.setRequestProperty("Authorization", "Bearer ${cfg.key}"); conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val response = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}: $response")
            return JSONObject(response).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty().ifBlank { "AI 返回内容为空" }
        } finally { conn.disconnect() }
    }
    private fun aiPeriodStart(days: Int): Long {
        if (days >= 60) return 0L
        val day = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return when (days) {
            -1 -> day.apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            -7 -> day.apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
            -30 -> day.apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
            else -> day.apply { add(Calendar.DAY_OF_YEAR, -(days.coerceAtLeast(1) - 1)) }.timeInMillis
        }
    }
    private fun aiPeriodEnd(days: Int): Long {
        if (days >= 60) return System.currentTimeMillis()
        val end = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }
        return when (days) {
            -1 -> end.apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            -7 -> end.apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            -30 -> end.apply { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            else -> end.timeInMillis
        }
    }
    private fun config() = configStore.activeProfile()
    private fun join(address: String, path: String) = address.trimEnd('/') + "/" + path.trimStart('/')
}