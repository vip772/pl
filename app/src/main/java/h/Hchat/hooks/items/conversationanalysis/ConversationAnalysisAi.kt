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
        val start = System.currentTimeMillis() - (days - 1L) * 86_400_000L
        val limit = configStore.sampleLimit().let { if (it == 0) 3000 else it }
        val rows = db.query("SELECT content,isSend FROM message WHERE talker = ? AND type = 1 AND createTime >= ? ORDER BY createTime ASC LIMIT $limit", arrayOf(talker, start.toString()))
        val records = rows.mapNotNull { row ->
            val raw = row["content"].toString()
            raw.substringAfter(":\n", raw).replace('\n', ' ').trim().takeIf { it.isNotBlank() }?.take(500)
        }
        require(records.isNotEmpty()) { "所选时段暂无文本消息" }
        val prompt = "请根据以下群聊记录生成中文群聊总结，包含主题、关键人物、整体氛围和有趣细节。只能依据记录，不得编造。\\n" + records.joinToString("\\n")
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
                    if (data.isBlank() || data == "[DONE]") continue
                    val chunk = runCatching { JSONObject(data).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty() }.getOrDefault("")
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
        val start = System.currentTimeMillis() - (days - 1L) * 86_400_000L
        val rows = db.query("SELECT content,isSend FROM message WHERE talker = ? AND type = 1 AND createTime >= ? ORDER BY createTime ASC LIMIT 3000", arrayOf(talker, start.toString()))
        val records = rows.mapNotNull { row ->
            val content = row["content"].toString().substringAfter(":\n", row["content"].toString()).replace('\n', ' ').trim()
            content.takeIf { it.isNotBlank() }?.take(500)
        }
        require(records.isNotEmpty()) { "所选时段暂无文本消息" }
        val prompt = "请根据以下群聊记录生成中文群聊总结，包含主题、关键人物、整体氛围和有趣细节。只能依据记录，不得编造。\n" + records.joinToString("\n")
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
    private fun config() = configStore.activeProfile()
    private fun join(address: String, path: String) = address.trimEnd('/') + "/" + path.trimStart('/')
}