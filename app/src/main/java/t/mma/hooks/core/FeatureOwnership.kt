package t.mma.hooks.core

import android.os.Handler
import android.os.Looper
import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Member
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private fun reportOwnershipError(onError: (Throwable) -> Unit, error: Throwable) {
    try { onError(error) } catch (_: Throwable) { /* Cleanup must continue. */ }
}
/**
 * 功能独占资源的所有权原语。
 *
 * 固定约定：
 * 1. 登记前先判断本代是否已关闭；关闭是幂等单向操作。
 * 2. 单项释放异常只上报，不阻断其余资源释放。
 * 3. 关闭之后到达的句柄、投递、任务立即释放或拒绝，不允许复活旧代。
 * 4. 只持有本功能自己的资源；公共 API、共享 Hook、进程级服务归第 2 项统一归属。
 */

/** 本功能独占的 Hook 句柄集合。迟到句柄在关闭后立即释放。 */
class OwnedHooks(private val onError: (Throwable) -> Unit) : AutoCloseable {
    private val lock = Any()
    private val handles = ArrayList<XC_MethodHook.Unhook>()
    private var closed = false

    fun isClosed(): Boolean = synchronized(lock) { closed }

    fun size(): Int = synchronized(lock) { handles.size }

    /** 安装并持有句柄；安装失败只返回 null，由调用方决定是否重试。 */
    fun hook(method: Member, callback: XC_MethodHook): XC_MethodHook.Unhook? {
        if (isClosed()) return null
        val handle = try {
            HookRegistry.get().hook(method, callback)
        } catch (error: Throwable) {
            reportOwnershipError(onError, error)
            null
        }
        return adopt(handle)
    }

    /** 接管外部已安装的句柄，保证销毁时不会遗漏。 */
    fun adopt(handle: XC_MethodHook.Unhook?): XC_MethodHook.Unhook? {
        if (handle == null) return null
        val releaseNow = synchronized(lock) {
            if (closed) {
                true
            } else {
                if (handles.none { it === handle }) handles.add(handle)
                false
            }
        }
        if (releaseNow) release(handle)
        return if (releaseNow) null else handle
    }

    private fun release(handle: XC_MethodHook.Unhook) {
        try {
            HookRegistry.get().unhook(handle)
        } catch (error: Throwable) {
            reportOwnershipError(onError, error)
        }
    }

    override fun close() {
        val pending = synchronized(lock) {
            if (closed) return
            closed = true
            handles.asReversed().toList().also { handles.clear() }
        }
        pending.forEach(::release)
    }
}

/** 本功能独占的主线程投递：关闭时撤销全部未执行任务，关闭后拒绝新投递。 */
class OwnedHandler(
    private val onError: (Throwable) -> Unit,
    looper: Looper = Looper.getMainLooper()
) : AutoCloseable {
    private val handler = Handler(looper)
    private val lock = Any()
    private val entries = LinkedHashSet<Entry>()
    private var closed = false
    // Each posting owns a separate entry. Cancellation drops its task capture.
    private inner class Entry(var task: Runnable?) : Runnable {
        override fun run() {
            val claimed = synchronized(lock) {
                entries.remove(this)
                val next = if (closed) null else task
                task = null
                next
            } ?: return
            // Already-claimed work may finish; host side effects need consumer guards.
            try { claimed.run() } catch (error: Throwable) {
                reportOwnershipError(onError, error)
            }
        }
    }
    fun isClosed(): Boolean = synchronized(lock) { closed }
    fun pendingCount(): Int = synchronized(lock) { entries.size }
    fun post(task: Runnable): Boolean = enqueue(task, 0L)
    fun postDelayed(task: Runnable, delayMs: Long): Boolean = enqueue(task, delayMs)
    private fun enqueue(task: Runnable, delayMs: Long): Boolean {
        var failure: Throwable? = null
        val accepted = synchronized(lock) {
            if (closed) return false
            val entry = Entry(task)
            entries.add(entry)
            // Serialize insertion with close, not merely ownership registration.
            val queued = try {
                if (delayMs <= 0L) handler.post(entry) else handler.postDelayed(entry, delayMs)
            } catch (error: Throwable) {
                failure = error
                false
            }
            if (!queued) {
                entries.remove(entry)
                entry.task = null
            }
            queued
        }
        failure?.let { reportOwnershipError(onError, it) }
        return accepted
    }
    /** Cancel every posting of this exact task, like Handler.removeCallbacks. */
    fun remove(task: Runnable) {
        val pending = synchronized(lock) {
            entries.filter { it.task === task }.also { matches ->
                matches.forEach { entries.remove(it); it.task = null }
            }
        }
        pending.forEach(::removeCallback)
    }
    private fun removeCallback(entry: Entry) {
        try { handler.removeCallbacks(entry) } catch (error: Throwable) {
            reportOwnershipError(onError, error)
        }
    }
    override fun close() {
        val pending = synchronized(lock) {
            if (closed) return
            closed = true
            entries.toList().also { snapshot ->
                entries.clear()
                snapshot.forEach { it.task = null }
            }
        }
        pending.forEach(::removeCallback)
    }
}
/** 本功能独占的工作线程：单线程 + 有界队列，关闭时撤销排队任务并中断执行中任务。 */
class OwnedExecutor(
    name: String,
    private val onError: (Throwable) -> Unit,
    queueCapacity: Int = DEFAULT_QUEUE_CAPACITY
) : AutoCloseable {
    private val lock = Any()
    private val executor = ThreadPoolExecutor(
        1,
        1,
        30L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(queueCapacity.coerceAtLeast(1)),
        java.util.concurrent.ThreadFactory { runnable ->
            Thread(runnable, name).apply { isDaemon = true }
        }
    ).apply { allowCoreThreadTimeOut(true) }
    private var closed = false

    fun isClosed(): Boolean = synchronized(lock) { closed }

    fun queuedCount(): Int = synchronized(lock) { executor.queue.size }

    /** 拒绝的提交必须让调用方知道，不允许静默丢弃。 */
    fun submit(task: Runnable): Boolean {
        synchronized(lock) { if (closed) return false }
        return try {
            executor.execute {
                if (isClosed()) return@execute
                try {
                    task.run()
                } catch (error: Throwable) {
                    reportOwnershipError(onError, error)
                }
            }
            true
        } catch (_: RejectedExecutionException) {
            false
        } catch (error: Throwable) {
            reportOwnershipError(onError, error)
            false
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
        }
        try {
            executor.shutdownNow()
        } catch (error: Throwable) {
            reportOwnershipError(onError, error)
            executor.queue.clear()
            try {
                executor.shutdown()
            } catch (_: Throwable) {
                // 已关闭或已终止：无需继续处理。
            }
        }
    }

    companion object {
        const val DEFAULT_QUEUE_CAPACITY = 64
    }
}

/**
 * 启用区间代际。旧代回调携带自己的 generation，代际切换后一律失效，
 * 避免旧线程或旧投递操作新实例。
 */
class OwnerGeneration {
    private var current = 0
    private var closed = true
    @Synchronized fun open(): Int {
        current += 1
        closed = false
        return current
    }
    @Synchronized fun close(): Int {
        closed = true
        current += 1
        return current
    }
    @Synchronized fun isActive(generation: Int): Boolean = !closed && current == generation
    @Synchronized fun isClosed(): Boolean = closed
}
