package h.Hchat.utils

import android.content.Context
import android.os.Build
import h.Hchat.BuildConfig
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.preferences.HchatStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Private, size-limited diagnostics log used by the in-app export action. */
object RuntimeLogStore {
    private const val LOG_DIR = "logs"
    private const val LOG_FILE = "runtime.log"
    private const val CRASH_DIR = "crash"
    private const val LAST_CRASH_FILE = "last_crash.log"
    private const val MAX_LOG_BYTES = 512 * 1024L
    private const val RETAIN_BYTES = 384 * 1024
    private val lock = Any()
    @Volatile private var applicationContext: Context? = null

    @JvmStatic
    fun initialize(context: Context?) {
        if (context != null) applicationContext = context.applicationContext ?: context
    }

    @JvmStatic
    fun append(level: String, message: String, throwable: Throwable?) {
        val context = applicationContext ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date())
        val entry = buildString {
            append(timestamp).append(' ').append(level).append(' ').append(message).append('\n')
            if (throwable != null) append(android.util.Log.getStackTraceString(throwable)).append('\n')
        }
        runCatching {
            synchronized(lock) {
                val file = runtimeLogFile(context)
                file.parentFile?.mkdirs()
                if (file.length() > MAX_LOG_BYTES) {
                    val bytes = file.readBytes()
                    val offset = (bytes.size - RETAIN_BYTES).coerceAtLeast(0)
                    file.writeBytes(bytes.copyOfRange(offset, bytes.size))
                }
                file.appendText(entry, Charsets.UTF_8)
            }
        }
    }

    @JvmStatic
    fun buildDiagnosticReport(context: Context): String {
        initialize(context)
        val appContext = context.applicationContext ?: context
        val version = runCatching { WeChatApis.version()?.current() }.getOrNull()
        val runtimeLog = readLimited(runtimeLogFile(appContext))
        val crashLog = readLimited(File(File(HchatStorage.storageDir(appContext), CRASH_DIR), LAST_CRASH_FILE))
        return buildString {
            appendLine("Hchat 诊断日志")
            appendLine("导出时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())}")
            appendLine("模块版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("微信版本: ${version?.versionName?.ifBlank { "未知" } ?: "未知"} (${version?.versionCode ?: 0})")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine("===== Hchat 运行日志 =====")
            appendLine(runtimeLog.ifBlank { "暂无运行日志" })
            appendLine("===== 最近异常日志 =====")
            appendLine(crashLog.ifBlank { "暂无异常日志" })
        }
    }

    private fun runtimeLogFile(context: Context): File =
        File(File(HchatStorage.storageDir(context), LOG_DIR), LOG_FILE)

    private fun readLimited(file: File): String = runCatching {
        if (!file.isFile) return@runCatching ""
        val bytes = file.readBytes()
        val offset = (bytes.size - MAX_LOG_BYTES.toInt()).coerceAtLeast(0)
        bytes.copyOfRange(offset, bytes.size).toString(Charsets.UTF_8)
    }.getOrDefault("")
}
