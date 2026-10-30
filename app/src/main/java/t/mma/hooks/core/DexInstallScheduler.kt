package t.mma.hooks.core
import t.mma.utils.HLog

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 统一调度依赖 DexKit 的 Hook 安装。
 *
 * 功能层只返回本次是否真正安装成功；缓存缺失、DexKit 暂时空结果或 Hook 失败时，
 * 这里负责去重和有限重试，避免用户靠多次强停微信碰运气。
 */
object DexInstallScheduler {
    enum class Stage(internal val level: Int) {
        EARLY(0),
        BRIDGE(1),
        WARMUP(2)
    }

    private const val TAG = "[Hchat:DexInstall]"
    private const val MAX_RETRY_ATTEMPTS = 6
    private const val MAX_RETRY_DELAY_MS = 60_000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val states = ConcurrentHashMap<String, State>()
    private val readyLevel = AtomicInteger(0)
    private val dexKitExecutionLock = Any()
    private val executor = ThreadPoolExecutor(
        1, 1, 30L, TimeUnit.SECONDS, LinkedBlockingQueue(),
        java.util.concurrent.ThreadFactory { runnable ->
            Thread(runnable, "Hchat-DexInstall").apply { isDaemon = true }
        }
    ).apply { allowCoreThreadTimeOut(true) }

    fun schedule(
        key: String,
        label: String = key,
        stage: Stage = Stage.BRIDGE,
        priority: Int = 0,
        installer: () -> Boolean
    ) {
        if (key.isBlank()) return
        HLog.d("$TAG 调度功能: $label key=$key stage=$stage priority=$priority")
        val state = states.computeIfAbsent(key) { State(label) }
        synchronized(state) {
            if (state.installed.get()) return
            state.label = label
            state.stage = stage
            state.priority = priority
            state.installer = installer
        }
        if (!isReady(stage)) return
        if (!state.installed.get() && !state.running.get() && !state.retryScheduled.get() &&
            state.retryIndex.get() >= MAX_RETRY_ATTEMPTS
        ) {
            state.retryIndex.set(0)
        }
        runNow(key, state)
    }

    @JvmStatic
    fun scheduleTask(key: String, label: String, stage: Stage, priority: Int, installer: Callable<Boolean>) {
        schedule(key, label, stage, priority) {
            installer.call() == true
        }
    }

    @JvmStatic
    fun markDexBridgeReady() {
        advanceReady(Stage.BRIDGE)
    }

    @JvmStatic
    fun markDexReady() {
        markDexWarmupReady()
    }

    @JvmStatic
    fun markDexWarmupReady() {
        advanceReady(Stage.WARMUP)
    }

    @JvmStatic
    fun runDexKitTask(task: Runnable) {
        synchronized(dexKitExecutionLock) {
            task.run()
        }
    }

    /** Consume native-backed results before returning; only detached values may escape. */
    fun <T> queryDexKit(task: () -> T): T = synchronized(dexKitExecutionLock) { task() }

    /** Java entry: query and lazy result reads share the same reentrant gate. */
    @JvmStatic
    fun <T> queryDexKitTask(task: Callable<T>): T = synchronized(dexKitExecutionLock) { task.call() }
    private fun advanceReady(stage: Stage) {
        while (true) {
            val current = readyLevel.get()
            if (current >= stage.level) break
            if (readyLevel.compareAndSet(current, stage.level)) break
        }
        runReadyStates()
    }

    fun markInstalled(key: String) {
        val state = states.computeIfAbsent(key) { State(key) }
        synchronized(state) {
            state.installed.set(true)
            state.installer = null
        }
    }

    private fun runNow(key: String, state: State) {
        if (!isReady(state.stage)) return
        synchronized(state) {
            if (state.installed.get() || state.installer == null) return
            if (!state.running.compareAndSet(false, true)) return
        }
        HLog.d("$TAG 安装入队: ${state.label} key=$key attempt=${state.retryIndex.get() + 1}")
        executor.execute {
            var ok = false
            try {
                ok = synchronized(dexKitExecutionLock) {
                    HLog.i("$TAG 开始执行: ${state.label} key=$key attempt=${state.retryIndex.get() + 1} thread=${Thread.currentThread().name}")
                    val installer = synchronized(state) {
                        if (state.installed.get()) null else state.installer
                    }
                    if (installer == null) state.installed.get() else installer()

                }
            } catch (e: Throwable) {
                HLog.e("$TAG ${state.label} 安装异常: ${e.message}", e)
            } finally {
                if (ok) {
                    synchronized(state) {
                        state.installed.set(true)
                        state.installer = null
                    }
                    HLog.i("$TAG 功能生效: ${state.label} key=$key")
                } else {
                    HLog.w("$TAG 安装未完成: ${state.label} key=$key attempt=${state.retryIndex.get()}")
                }
                state.running.set(false)
            }
            if (!ok) scheduleRetry(key, state)
        }
    }

    private fun runReadyStates() {
        states.entries
            .asSequence()
            .filter { isReady(it.value.stage) }
            .sortedWith(
                compareBy<Map.Entry<String, State>> { it.value.stage.level }
                    .thenBy { it.value.priority }
                    .thenBy { it.key }
            )
            .forEach { (key, state) -> runNow(key, state) }
    }

    private fun scheduleRetry(key: String, state: State) {
        if (state.installed.get()) return
        if (!state.retryScheduled.compareAndSet(false, true)) return
        val index = state.retryIndex.getAndIncrement()
        if (index >= MAX_RETRY_ATTEMPTS) {
            state.retryScheduled.set(false)
            HLog.e("$TAG ${state.label} 多次安装失败，停止本轮重试 key=$key")
            return
        }
        mainHandler.postDelayed({
            state.retryScheduled.set(false)
            if (isReady(state.stage)) runNow(key, state)
        }, retryDelayMs(index))
    }

    private fun retryDelayMs(attempt: Int): Long {
        if (attempt <= 0) return 1_000L
        if (attempt == 1) return 3_000L
        return minOf(MAX_RETRY_DELAY_MS, 3_000L * (1L shl (attempt - 1)))
    }

    private fun isReady(stage: Stage): Boolean {
        return readyLevel.get() >= stage.level
    }

    private class State(@Volatile var label: String) {
        @Volatile var stage: Stage = Stage.BRIDGE
        @Volatile var priority: Int = 0
        @Volatile var installer: (() -> Boolean)? = null
        val installed = AtomicBoolean(false)
        val running = AtomicBoolean(false)
        val retryScheduled = AtomicBoolean(false)
        val retryIndex = AtomicInteger(0)
    }
}
