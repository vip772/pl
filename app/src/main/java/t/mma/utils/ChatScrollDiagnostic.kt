package t.mma.utils

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import t.mma.preferences.HchatStorage
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Temporary opt-in bounded counters; no Views or raw contact identifiers retained. */
object ChatScrollDiagnostic {
    private val lock = Any()
    @Volatile private var until = 0L
    private var worker: ScheduledThreadPoolExecutor? = null
    private var host: Context? = null
    private var label = ""
    private var session = 0L
    private var salt = 0
    private var budget = 0
    private var queries = 0
    private data class Stat(var count: Long = 0, var total: Long = 0, var max: Long = 0)
    private val stats = linkedMapOf<String, Stat>()
    private val pairs = HashMap<Int, Int>()
    private val sources = HashSet<String>()
    @JvmStatic fun isActive() = until != 0L && SystemClock.elapsedRealtime() < until
    @JvmStatic fun begin(): Long = if (isActive()) SystemClock.elapsedRealtimeNanos() else 0L
    @JvmStatic fun end(name: String, started: Long) {
        if (started != 0L) {
            val elapsed = (SystemClock.elapsedRealtimeNanos() - started).coerceAtLeast(0)
            record(name, elapsed)
            if (elapsed >= 4_000_000L) detail("slow.$name", "atMs=${SystemClock.elapsedRealtime()} elapsedUs=${elapsed/1000}")
        }
    }
    /** Inline so disabled diagnostics do not allocate a lambda or read the clock. */
    inline fun <T> phase(name: String, block: () -> T): T {
        val started = begin()
        try { return block() } finally { end(name, started) }
    }
    fun backgroundWrite(reason: Int, targetClass: String) {
        if (!isActive()) return
        val category = when (reason) {
            1 -> "external"
            2 -> "first"
            3 -> "slot"
            4 -> "bitmap"
            else -> "feedback"
        }
        hit("background.write.$category")
        // One bounded latest sample per reason per flush; never retain the View.
        detail("background.write.$category", "atMs=${SystemClock.elapsedRealtime()} target=${targetClass.take(160)}")
    }
    private var lastGeometry = "unseen"
    @JvmStatic fun geometry(native: Int, expanded: Int, overlap: Int) {
        if (!isActive()) return
        synchronized(lock) {
            if (isActive()) lastGeometry = "native=$native,expanded=$expanded,overlap=$overlap"
        }
    }
    private val details = linkedMapOf<String, String>()
    fun detail(name: String, value: String) {
        if (!isActive()) return
        synchronized(lock) { if (isActive() && (details.size < 32 || name in details)) details[name] = value.take(2400) }
    }
    fun observe(name: String, nanos: Long) { if (isActive()) record(name, nanos) }
    @JvmStatic fun hit(name: String) { if (isActive()) record(name, 0) }
    private fun record(name: String, time: Long) {
        try {
            if (!isActive()) return
            val key = name + if (Looper.myLooper() == Looper.getMainLooper()) ".main" else ".worker"
            synchronized(lock) {
                if (!isActive() || (stats.size >= 128 && key !in stats)) return
                val s = stats.getOrPut(key) { Stat() }; s.count++; s.total += time; s.max = maxOf(s.max, time)
            }
        } catch (_: Throwable) { }
    }
    @JvmStatic fun query(group: String?, member: String?) {
        if (!isActive()) return
        try {
            val sample = synchronized(lock) {
                if (!isActive()) return
                val key = (31 * (group?.hashCode() ?: 0) + (member?.hashCode() ?: 0)) xor salt
                if (pairs.size < 256 || key in pairs) pairs[key] = (pairs[key] ?: 0) + 1
                val take = budget > 0 && queries++ % 64 == 0
                if (take) budget--
                take
            }
            if (sample) {
                val path = Throwable().stackTrace.asSequence()
                    .filter { it.className != ChatScrollDiagnostic::class.java.name }
                    .take(10).joinToString(" <- ") { "${it.className}#${it.methodName}:${it.lineNumber}" }
                synchronized(lock) { if (isActive() && sources.size < 12) sources.add((if (Looper.myLooper() == Looper.getMainLooper()) "main:" else "worker:") + path) }
            }
        } catch (_: Throwable) { }
    }
    fun start(context: Context, group: String) {
        stop()
        if (!RuntimeLogStore.isEnabled(context)) return
        synchronized(lock) {
            host = context.applicationContext ?: context
            label = group.takeIf { it in listOf("A", "B", "C", "D") } ?: "?"
            session = SystemClock.elapsedRealtime(); salt = java.util.Random().nextInt()
            stats.clear(); pairs.clear(); sources.clear(); details.clear(); budget = 12; queries = 0; lastGeometry = "unseen"
            until = session + 300_000L
            worker = ScheduledThreadPoolExecutor(1) { task -> Thread(task, "Hchat-ScrollDiag").apply { isDaemon = true } }.apply {
                scheduleAtFixedRate({ tick() }, 5, 5, TimeUnit.SECONDS)
            }
            RuntimeLogStore.log("INFO", "[Hchat:ScrollDiag] start session=$session group=$label durationMs=300000")
        }
        ChatScrollTrace.start()
    }
    private fun tick() {
        try { synchronized(lock) {
            if (until == 0L) return
            if (!isActive() || host?.let { !RuntimeLogStore.isEnabled(it) } == true) stop()
            else flush("window")
        } } catch (_: Throwable) { }
    }
    private fun flush(reason: String) {
        val prefs = host?.let { HchatStorage.preferences(it, "Hchat_global_config") }
        val footer = prefs?.getBoolean("floating_chat_footer_enabled", false)
        val header = prefs?.getBoolean("floating_chat_header_enabled", false)
        RuntimeLogStore.log("INFO", buildString {
            append("[Hchat:ScrollDiag] session=$session group=$label reason=$reason footer=$footer header=$header elapsedMs=${SystemClock.elapsedRealtime()-session}")
            append(" geometry=$lastGeometry pairs=${pairs.size} repeatQueries=${pairs.values.sumOf { (it-1).coerceAtLeast(0) }} pairCap=256")
            stats.forEach { (key, s) -> append(" | $key count=${s.count} totalUs=${s.total/1000} maxUs=${s.max/1000}") }
            details.forEach { (key, value) -> append(" | $key=$value") }
            sources.forEach { append(" | source=$it") }
        })
        stats.clear(); pairs.clear(); sources.clear(); details.clear()
    }
    @JvmStatic fun stop() { synchronized(lock) {
        if (until == 0L) return
        until = 0L
        try { flush("stop") } finally {
            worker?.shutdownNow(); worker = null; host = null
            ChatScrollTrace.stop()
            stats.clear(); pairs.clear(); sources.clear(); details.clear()
        }
    } }
}
