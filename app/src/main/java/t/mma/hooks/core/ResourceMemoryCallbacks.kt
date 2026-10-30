package t.mma.hooks.core

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import t.mma.hooks.items.backgroundbeauty.BackgroundBeautyStore
import t.mma.ui.miuix.AvatarMemoryCache
import t.mma.utils.HLog
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Releases cache ownership, never bitmaps that may still be drawn by host views. */
object ResourceMemoryCallbacks : ComponentCallbacks2 {
    @Volatile private var registered = false
    private val pending = AtomicBoolean(false)
    private val cleaner = ThreadPoolExecutor(
        0, 1, 10L, TimeUnit.SECONDS, LinkedBlockingQueue(),
        java.util.concurrent.ThreadFactory { task ->
            Thread(task, "Hchat-CacheTrim").apply { isDaemon = true }
        }
    )

    @JvmStatic
    @Synchronized
    fun install(context: Context) {
        if (registered) return
        (context.applicationContext ?: context).registerComponentCallbacks(this)
        registered = true
    }

    override fun onTrimMemory(level: Int) {
        // UI_HIDDEN is a visibility change, not evidence of memory pressure.
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) releaseCaches()
    }

    override fun onLowMemory() = releaseCaches()
    override fun onConfigurationChanged(newConfig: Configuration) = Unit

    private fun releaseCaches() {
        if (!pending.compareAndSet(false, true)) return
        try {
            cleaner.execute {
                try {
                    BackgroundBeautyStore.trimMemory()
                    AvatarMemoryCache.trimMemory()
                } catch (error: Throwable) {
                    HLog.e("[Hchat:Memory] 缓存清理失败", error)
                } finally {
                    pending.set(false)
                }
            }
        } catch (error: Throwable) {
            pending.set(false)
            HLog.e("[Hchat:Memory] 缓存清理提交失败", error)
        }
    }
}
