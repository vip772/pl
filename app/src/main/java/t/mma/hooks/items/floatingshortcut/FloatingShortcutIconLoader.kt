package t.mma.hooks.items.floatingshortcut
import t.mma.utils.HLog

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** All view/cache bookkeeping runs on main; only bounded image decoding runs on the worker. */
internal class FloatingShortcutIconLoader : AutoCloseable {
    private val MAX_EDGE = 256
    private val MAX_PENDING = 64
    private data class Entry(val bitmap: Bitmap?)
    private class Request {
        var unpublished: Bitmap? = null
        fun discard() { unpublished?.recycle(); unpublished = null; targets.clear() }
        val targets = mutableListOf<Pair<WeakReference<ImageView>, Any>>()
    }

    private val lock = Any()
    @Volatile private var closed = false
    private var epoch = 0L
    private val main = Handler(Looper.getMainLooper())
    private val pendingMain = linkedSetOf<MainTask>()
    private inner class MainTask(var action: (() -> Unit)?) : Runnable {
        override fun run() {
            synchronized(lock) {
                pendingMain.remove(this)
                val work = if (closed) null else action
                action = null
                work?.invoke()
            }
        }
    }
    private val cache = object : LruCache<String, Entry>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Entry): Int =
            (value.bitmap?.allocationByteCount ?: 0) + 128
    }
    private val pending = mutableMapOf<String, Request>()
    private val bindings = WeakHashMap<ImageView, Any>()
    private val worker = ThreadPoolExecutor(
        0, 1, 30L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(MAX_PENDING),
        { task -> Thread(task, "Hchat-FloatingIcons").apply { isDaemon = true } }
    )
    fun bind(view: ImageView, path: String, fallback: Drawable) {
        check(Looper.myLooper() == Looper.getMainLooper())
        synchronized(lock) {
            if (closed) return
            if (!bindings.containsKey(view) && bindings.size >= 256) return
            val token = Any()
            bindings[view] = token
            view.setImageDrawable(fallback)
            if (path.isBlank()) return
            cache.get(path)?.let { entry ->
                entry.bitmap?.let { view.setImageDrawable(BitmapDrawable(view.resources, it)) }
                return
            }
            request(path)?.targets?.let { targets ->
                targets.removeAll { it.first.get() == null || it.first.get() === view }
                if (targets.size < 256) targets.add(WeakReference(view) to token)
            }
        }
    }
    fun prefetch(paths: List<String>) = onMain {
        paths.asSequence().filter { it.isNotBlank() }.distinct().take(MAX_PENDING).forEach { path ->
            if (cache.get(path) == null) request(path)
        }
    }
    fun invalidate(path: String) = onMain {
        cache.remove(path)
        pending.remove(path)?.discard()
    }
    fun clear() {
        synchronized(lock) { clearLocked() }
    }
    private fun clearLocked() {
        epoch++
        pending.values.forEach { it.discard() }
        pending.clear()
        bindings.clear()
        cache.evictAll()
        worker.queue.clear()
        pendingMain.forEach { it.action = null }
        pendingMain.clear()
        main.removeCallbacksAndMessages(null)
    }
    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            clearLocked()
            worker.shutdown()
        }
    }
    private fun request(path: String): Request? {
        if (closed) return null
        pending[path]?.let { return it }
        if (pending.size >= MAX_PENDING) return null
        val request = Request()
        val generation = epoch
        pending[path] = request
        try {
            worker.execute {
                val admitted = synchronized(lock) { !closed && epoch == generation && pending[path] === request }
                if (!admitted) return@execute
                val bitmap = try { decode(path) } catch (error: Throwable) {
                    try { HLog.e("[Hchat:FloatingShortcut] 图标解码失败", error) } catch (_: Throwable) { }
                    null
                }
                synchronized(lock) {
                    if (closed || epoch != generation || pending[path] !== request) {
                        bitmap?.recycle()
                        return@synchronized
                    }
                    request.unpublished = bitmap
                    val posted = onMain {
                        if (pending[path] === request && epoch == generation) {
                            pending.remove(path)
                            request.unpublished = null
                            cache.put(path, Entry(bitmap))
                            if (bitmap != null) request.targets.forEach { (reference, token) ->
                                val target = reference.get()
                                if (target != null && bindings[target] === token && target.isAttachedToWindow) {
                                    target.setImageDrawable(BitmapDrawable(target.resources, bitmap))
                                }
                            }
                            request.targets.clear()
                        }
                    }
                    if (!posted) { pending.remove(path); request.discard() }
                }
            }
        } catch (_: RejectedExecutionException) {
            pending.remove(path)
            return null
        }
        return request
    }
    private fun decode(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_EDGE * 2) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
            inSampleSize = sample
        }) ?: return null
        val edge = maxOf(bitmap.width, bitmap.height)
        if (edge <= MAX_EDGE) return bitmap
        val scale = MAX_EDGE.toFloat() / edge
        val scaled = Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1), true
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun onMain(block: () -> Unit): Boolean {
        synchronized(lock) {
            if (closed || pendingMain.size >= 64) return false
            val task = MainTask(block)
            pendingMain.add(task)
            if (Looper.myLooper() == Looper.getMainLooper()) task.run()
            else if (!main.post(task)) {
                pendingMain.remove(task)
                task.action = null
                return false
            }
            return true
        }
    }
}
