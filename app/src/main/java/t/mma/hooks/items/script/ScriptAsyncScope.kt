package t.mma.hooks.items.script

import java.util.concurrent.FutureTask
import java.lang.ref.WeakReference
import java.util.concurrent.Callable
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Plugin owns pending closures; running side effects are never interrupted on unload. */
internal class ScriptAsyncScope(
    private val enqueue: (Runnable) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    companion object {
        private const val MAX_PENDING = 256
        private val fallback = ThreadPoolExecutor(
            2, 2, 30L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(256),
            java.util.concurrent.ThreadFactory { task ->
                Thread(task, "Hchat-Script-Async").apply { isDaemon = true }
            }
        ).apply { allowCoreThreadTimeOut(true) }
        fun enqueueFallback(task: Runnable) = fallback.execute(task)
    }
    private val lock = Any()
    private val pending = HashSet<FutureTask<Unit>>()
    private var disposed = false

    fun execute(block: () -> Unit) {
        synchronized(lock) {
            if (disposed) {
                onError(RejectedExecutionException("插件异步作用域已关闭"))
                return
            }
            if (pending.size >= MAX_PENDING) {
                onError(RejectedExecutionException("插件待执行异步任务达到上限 $MAX_PENDING"))
                return
            }
            val task = Task(this, block, onError)
            pending.add(task)
            try {
                enqueue(task)
            } catch (error: Throwable) {
                task.cancel(false)
                onError(error)
            }
        }
    }

    // Cancelled queued wrappers must not retain their plugin owner.
    private class Task(owner: ScriptAsyncScope, block: () -> Unit, onError: (Throwable) -> Unit) :
        FutureTask<Unit>(Callable {
            try { block() } catch (error: Throwable) { onError(error) }
            Unit
        }) {
        private val ownerRef = WeakReference(owner)
        override fun done() {
            val owner = ownerRef.get() ?: return
            synchronized(owner.lock) { owner.pending.remove(this) }
            ownerRef.clear()
        }
    }

    fun dispose() {
        synchronized(lock) {
            if (disposed) return
            disposed = true
            pending.toList().forEach { it.cancel(false) }
            pending.clear()
        }
    }
}
