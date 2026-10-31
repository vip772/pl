package t.mma.hooks.core

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Keeps each owner's serial ordering; idle workers exit without closing reusable services. */
internal object IdleSerialExecutor {
    fun create(name: String): ThreadPoolExecutor = ThreadPoolExecutor(
        1, 1, 30L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(),
        ThreadFactory { task -> Thread(task, name).apply { isDaemon = true } }
    ).apply { allowCoreThreadTimeOut(true) }
}