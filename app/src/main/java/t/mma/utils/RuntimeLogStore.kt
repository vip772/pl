package t.mma.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import t.mma.BuildConfig
import t.mma.hooks.api.core.WeChatApis
import t.mma.preferences.HchatStorage
import java.io.RandomAccessFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Private, size-limited diagnostics log used by the in-app export action. */
object RuntimeLogStore {
    private const val PREFS_NAME = "Hchat_runtime_log"
    private const val KEY_ENABLED = "enabled"
    @Volatile private var enabled = false
    private var settings: SharedPreferences? = null
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == KEY_ENABLED) enabled = prefs.getBoolean(KEY_ENABLED, false)
    }
    private const val LOG_DIR = "logs"
    private const val LOG_FILE = "runtime.log"
    private const val CRASH_DIR = "crash"
    private const val LAST_CRASH_FILE = "last_crash.log"
    private const val MAX_LOG_BYTES = 512 * 1024L
    private const val RETAIN_BYTES = 384 * 1024
    private val lock = Any()
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "Hchat-RuntimeLog").apply { isDaemon = true }
    }
    private val pending = java.util.concurrent.atomic.AtomicInteger()
    private val dropped = java.util.concurrent.atomic.AtomicInteger()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)
    private fun flushPending() {
        if (Thread.currentThread().name == "Hchat-RuntimeLog") return
        runCatching { writer.submit {}.get(3, java.util.concurrent.TimeUnit.SECONDS) }
    }
    @Volatile private var applicationContext: Context? = null

    @JvmStatic
    fun initialize(context: Context?) {
        if (context == null) return
        val appContext = context.applicationContext ?: context
        applicationContext = appContext
        synchronized(lock) {
            if (settings == null) {
                val prefs = HchatStorage.preferences(appContext, PREFS_NAME)
                settings = prefs
                enabled = prefs.getBoolean(KEY_ENABLED, false)
                prefs.registerOnSharedPreferenceChangeListener(settingsListener)
            }
        }
    }

    @JvmStatic
    fun isEnabled(context: Context): Boolean {
        initialize(context)
        return enabled
    }
    @JvmStatic
    fun setEnabled(context: Context, value: Boolean) {
        initialize(context)
        synchronized(lock) {
            enabled = value
            settings?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
        }
    }
    @JvmStatic
    fun log(level: String, message: String) {
        append(level, message, null)
    }
    @JvmStatic
    fun clearRuntimeLog(context: Context? = applicationContext) {
        val appContext = context?.applicationContext ?: context ?: return
        runCatching {
            writer.submit { synchronized(lock) { runtimeLogFile(appContext).delete() } }
                .get(3, java.util.concurrent.TimeUnit.SECONDS)
        }
    }
    @JvmStatic
    fun readRuntimeLog(context: Context? = applicationContext): String {
        val appContext = context?.applicationContext ?: context ?: return ""
        flushPending()
        return synchronized(lock) { readLimited(runtimeLogFile(appContext)) }
    }
    private data class LogEntry(val time: Long, val level: String, val message: String, val throwable: Throwable?)
    private val queueLock = Any()
    private val queue = java.util.ArrayDeque<LogEntry>()
    private var drainScheduled = false
    @JvmStatic
    fun append(level: String, message: String, throwable: Throwable?) {
        if (!enabled || applicationContext == null) return
        synchronized(queueLock) {
            if (pending.get() >= 1024) {
                dropped.incrementAndGet()
                return
            }
            pending.incrementAndGet()
            queue.addLast(LogEntry(System.currentTimeMillis(), level, message, throwable))
            if (!drainScheduled) {
                drainScheduled = true
                writer.execute { drainQueue() }
            }
        }
    }
    private fun drainQueue() {
        while (true) {
            val batch = synchronized(queueLock) {
                if (queue.isEmpty()) {
                    drainScheduled = false
                    return
                }
                ArrayList<LogEntry>(minOf(queue.size, 64)).also { entries ->
                    repeat(minOf(queue.size, 64)) { entries.add(queue.removeFirst()) }
                }
            }
            try {
                if (!enabled) continue
                val context = applicationContext ?: continue
                val lost = dropped.getAndSet(0)
                val text = buildString {
                    if (lost > 0) append("[Hchat:RuntimeLog] queue overflow dropped=").append(lost).append('\n')
                    batch.forEach { entry ->
                        append(formatter.format(Date(entry.time))).append(' ').append(entry.level)
                            .append(' ').append(entry.message).append('\n')
                        entry.throwable?.let { append(android.util.Log.getStackTraceString(it)).append('\n') }
                    }
                }
                runCatching {
                    synchronized(lock) {
                        if (!enabled) return@synchronized
                        val file = runtimeLogFile(context)
                        file.parentFile?.mkdirs()
                        if (file.length() > MAX_LOG_BYTES) file.writeBytes(readTail(file, RETAIN_BYTES))
                        file.appendText(text, Charsets.UTF_8)
                    }
                }
            } finally { pending.addAndGet(-batch.size) }
        }
    }
    @JvmStatic
    fun buildDiagnosticReport(context: Context): String {
        initialize(context)
        val appContext = context.applicationContext ?: context
        val version = runCatching { WeChatApis.version()?.current() }.getOrNull()
        val runtimeLog = readRuntimeLog(appContext)
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

    private fun readTail(file: File, limit: Int): ByteArray =
        RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            val count = minOf(length, limit.toLong()).toInt()
            ByteArray(count).also { bytes ->
                input.seek(length - count)
                input.readFully(bytes)
            }
        }
    private fun readLimited(file: File): String = runCatching {
        if (!file.isFile) return@runCatching ""
                readTail(file, MAX_LOG_BYTES.toInt()).toString(Charsets.UTF_8)
    }.getOrDefault("")
}
