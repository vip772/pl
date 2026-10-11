package t.mma.utils

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Short startup-only slow-call sampling, no per-frame output or user content. */
object PerformanceSample {
    private val until = SystemClock.elapsedRealtime() + 180_000L
    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    @JvmStatic
    fun start(): Long = if (SystemClock.elapsedRealtime() <= until) SystemClock.elapsedRealtimeNanos() else 0L
    @JvmStatic
    fun finish(name: String, started: Long) {
        if (started == 0L) return
        val elapsed = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000L
        if (elapsed < 16L || SystemClock.elapsedRealtime() > until) return
        if (counts.getOrPut(name) { AtomicInteger() }.incrementAndGet() <= 3) {
            HLog.i("[Hchat:PerformanceSample] operation=$name elapsedMs=$elapsed thread=${Thread.currentThread().name}")
        }
    }
}