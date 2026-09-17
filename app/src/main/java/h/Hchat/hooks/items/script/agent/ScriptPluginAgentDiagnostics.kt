package h.Hchat.hooks.items.script.agent

import android.content.Context
import h.Hchat.preferences.HchatStorage
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** One file per Agent request; records control flow without storing secrets or source text. */
internal class ScriptPluginAgentDiagnostics private constructor(
    private val file: File,
    private val requestId: String
) {
    private val lock = Any()

    fun log(event: String, details: String = "") {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date())
        val line = buildString {
            append(timestamp).append(' ').append(event)
            if (details.isNotBlank()) append(' ').append(details.replace('\n', ' ').take(2_000))
            append('\n')
        }
        runCatching {
            synchronized(lock) {
                file.parentFile?.mkdirs()
                file.appendText(line, Charsets.UTF_8)
            }
        }
    }

    fun parameterSummary(arguments: String): String {
        val digest = sha256(arguments)
        val keys = runCatching { JSONObjectKeys.keys(arguments).joinToString(",") }.getOrDefault("")
        return "bytes=${arguments.toByteArray(Charsets.UTF_8).size} sha256=$digest keys=${keys.take(300)}"
    }

    fun finish(status: String, details: String = "") {
        log("request_finish", "status=$status $details".trim())
        h.Hchat.utils.HLog.i("[Hchat:ScriptAgent] 诊断日志已保存: ${file.absolutePath}")
    }

    fun path(): String = file.absolutePath

    companion object {
        fun start(context: Context, sessionId: String, turnId: String): ScriptPluginAgentDiagnostics {
            val requestId = UUID.randomUUID().toString().replace("-", "")
            val root = File(HchatStorage.storageDir(context), "logs/agent-diagnostics")
            val file = File(root, "agent-$requestId.log")
            return ScriptPluginAgentDiagnostics(file, requestId).also {
                it.log("request_start", "requestId=$requestId sessionId=${sessionId.take(80)} turnId=${turnId.take(80)}")
            }
        }

        private fun sha256(value: String): String {
            return MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
                .take(16)
        }
    }

    private object JSONObjectKeys {
        fun keys(value: String): Set<String> {
            val json = org.json.JSONObject(value)
            return json.keys().asSequence().toCollection(LinkedHashSet())
        }
    }
}
