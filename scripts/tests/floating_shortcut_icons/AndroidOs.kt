package android.os
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
class Looper private constructor() {
    companion object {
        private val owner = Thread.currentThread()
        private val main = Looper()
        fun getMainLooper() = main
        fun myLooper(): Looper? = if (Thread.currentThread() === owner) main else null
    }
}
class Handler(looper: Looper) {
    private val owned = java.util.concurrent.ConcurrentHashMap.newKeySet<Runnable>()
    fun post(runnable: Runnable): Boolean {
        if (reject) return false
        lateinit var wrapper: Runnable
        wrapper = Runnable { owned.remove(wrapper); runnable.run() }
        owned.add(wrapper)
        return tasks.offer(wrapper)
    }
    fun removeCallbacksAndMessages(token: Any?) { tasks.removeAll(owned); owned.clear() }
    companion object {
        @Volatile var reject = false
        private val tasks = LinkedBlockingQueue<Runnable>()
        fun runNext() { checkNotNull(tasks.poll(3, TimeUnit.SECONDS)) { "Worker callback timed out" }.run() }
        fun drain() { while (true) (tasks.poll() ?: return).run() }
    }
}
