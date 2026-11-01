package t.mma.hooks.core

import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Feature-private delayed notices; bounded waiting, no interruption of entered host calls. */
internal class PendingNoticeTasks(name: String, private val capacity: Int = 256) : AutoCloseable {
    private class Entry(var action: (() -> Unit)?) { var future: ScheduledFuture<*>? = null }
    private val lock = Any()
    private var closed = false
    private val pending = HashSet<Entry>()
    private val worker = ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, name).apply { isDaemon = true }
    }.apply {
        removeOnCancelPolicy = true
        setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
        setContinueExistingPeriodicTasksAfterShutdownPolicy(false)
        setKeepAliveTime(30, TimeUnit.SECONDS)
        allowCoreThreadTimeOut(true)
    }

    fun schedule(delayMs: Long, action: () -> Unit): Boolean = synchronized(lock) {
        if (closed || pending.size >= capacity) return false
        val entry = Entry(action)
        pending.add(entry)
        try {
            entry.future = worker.schedule({
                val run = synchronized(lock) {
                    pending.remove(entry)
                    val next = if (closed) null else entry.action
                    entry.action = null
                    entry.future = null
                    next
                }
                run?.invoke()
            }, delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            true
        } catch (error: Throwable) {
            pending.remove(entry)
            entry.action = null
            throw error
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            pending.forEach { entry ->
                entry.action = null
                entry.future?.cancel(false)
                entry.future = null
            }
            pending.clear()
            worker.shutdown()
        }
    }
}
