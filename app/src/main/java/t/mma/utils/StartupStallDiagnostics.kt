package t.mma.utils

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Startup-only diagnostics; enabled only with runtime logging. */
object StartupStallDiagnostics {
    private const val TAG = "[Hchat:StartupStall]"
    private val started = AtomicBoolean(false)
    private val operations = ConcurrentHashMap<Long, String>()
    @Volatile private var active = false
    @JvmStatic fun begin(label: String): Long {
        if (!active) return 0L
        operations[Thread.currentThread().id] = label
        return SystemClock.uptimeMillis()
    }
    @JvmStatic fun end(token: Long, label: String) {
        if (token == 0L) return
        operations.remove(Thread.currentThread().id)
        val elapsed = SystemClock.uptimeMillis() - token
        if (elapsed >= 50L) HLog.i("$TAG operation=$label elapsedMs=$elapsed thread=${Thread.currentThread().name}")
    }
    @JvmStatic fun start() {
        if (!started.compareAndSet(false, true)) return
        active = true
        val main = Handler(Looper.getMainLooper())
        val worker = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "Hchat-StallProbe").apply { isDaemon = true }
        }
        val acknowledged = AtomicBoolean(false)
        var pending = false
        var sentAt = 0L
        var samples = 0
        var totalSamples = 0
        var lastTick = SystemClock.uptimeMillis()
        val deadline = lastTick + 90_000L
        HLog.i("$TAG probeStart windowMs=90000 thresholdMs=1000 intervalMs=250")
        worker.scheduleWithFixedDelay({
            try {
                val now = SystemClock.uptimeMillis()
                val gap = now - lastTick
                lastTick = now
                if (now >= deadline) {
                    active = false
                    operations.clear()
                    main.removeCallbacksAndMessages(worker)
                    worker.shutdown()
                    HLog.i("$TAG probeStop")
                } else {
                    if (gap >= 1000L) HLog.i("$TAG probeSchedulingGapMs=$gap")
                    if (pending && acknowledged.get()) {
                        if (samples > 0) HLog.i("$TAG mainRecovered observedWaitMs=${now - sentAt} samples=$samples")
                        pending = false
                    }
                    if (!pending) {
                        sentAt = now
                        samples = 0
                        acknowledged.set(false)
                        pending = true
                        main.postAtTime({ acknowledged.set(true) }, worker, now)
                    } else if (now - sentAt >= 1000L + samples * 750L && samples < 8 && totalSamples < 40) {
                        samples++
                        totalSamples++
                        val stacks = Thread.getAllStackTraces()
                        val trace = buildString {
                            append("$TAG mainUnresponsive waitMs=${now - sentAt} sample=$samples operations=$operations")
                            stacks.entries.filter { (thread, _) ->
                                thread === Looper.getMainLooper().thread || thread.name == "Hchat-DexInstall" ||
                                    thread.name == "Hchat-Init" || thread.name.startsWith("Hchat-Conversation")
                            }.sortedBy { if (it.key === Looper.getMainLooper().thread) 0 else 1 }.forEach { (thread, stack) ->
                                append("\nthread=").append(thread.name).append(" state=").append(thread.state)
                                stack.take(48).forEach { append("\n  at ").append(it) }
                            }
                        }
                        HLog.i(trace)
                    }
                }
            } catch (error: Throwable) {
                active = false
                worker.shutdown()
                HLog.e("$TAG 诊断采样失败", error)
            }
        }, 0L, 250L, TimeUnit.MILLISECONDS)
    }
}
