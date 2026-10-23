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
        if (started != 0L) record(name, (SystemClock.elapsedRealtimeNanos() - started).coerceAtLeast(0))
    }
    private var lastGeometry = "unseen"
    @JvmStatic fun geometry(native: Int, expanded: Int, overlap: Int) {
        if (!isActive()) return
        synchronized(lock) {
            if (isActive()) lastGeometry = "native=$native,expanded=$expanded,overlap=$overlap"
        }
    }
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
            stats.clear(); pairs.clear(); sources.clear(); budget = 12; queries = 0; lastGeometry = "unseen"
            until = session + 300_000L
            worker = ScheduledThreadPoolExecutor(1) { task -> Thread(task, "Hchat-ScrollDiag").apply { isDaemon = true } }.apply {
                scheduleAtFixedRate({ tick() }, 5, 5, TimeUnit.SECONDS)
            }
            RuntimeLogStore.log("INFO", "[Hchat:ScrollDiag] start session=$session group=$label durationMs=300000")
        }
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
            sources.forEach { append(" | source=$it") }
        })
        stats.clear(); pairs.clear(); sources.clear()
    }
    @JvmStatic fun stop() { synchronized(lock) {
        if (until == 0L) return
        until = 0L
        try { flush("stop") } finally {
            worker?.shutdownNow(); worker = null; host = null
            stats.clear(); pairs.clear(); sources.clear()
        }
    } }
}
