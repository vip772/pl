package t.mma.hooks.items.custombottombar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.core.HookRegistry
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException

/** Each settings page owns its callbacks and hooks; entered decoding is never interrupted. */
object CustomBottomBarIconPicker {
    private val owners = linkedSetOf<Session>()
    private val codes = linkedSetOf<Int>()
    private var nextCode = 0x7710
    private fun allocateCode(): Int = synchronized(codes) {
        while (codes.contains(nextCode)) nextCode = if (nextCode >= 0x77ff) 0x7710 else nextCode + 1
        nextCode.also { codes.add(it); nextCode = if (nextCode >= 0x77ff) 0x7710 else nextCode + 1 }
    }
    fun openSession(): Session = synchronized(owners) {
        check(owners.size < 8) { "Bottom icon picker session cap reached" }
        Session().also { owners.add(it) }
    }
    fun destroy() {
        val snapshot = synchronized(owners) { owners.toList().also { owners.clear() } }
        snapshot.forEach { it.close() }
    }
    class Session internal constructor() : AutoCloseable {
        private val lock = Any()
        private var closed = false
        private val pending = linkedMapOf<Int, Pending>()
        private val hooks = linkedMapOf<Method, XC_MethodHook.Unhook>()
        private val main = Handler(Looper.getMainLooper())
        private val worker = ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS, LinkedBlockingQueue<Runnable>(4),
            { work -> Thread(work, "Hchat-CustomBottomBarIcon").apply { isDaemon = true } })
        private class Pending(activity: Activity, val key: String, var callback: ((CustomBottomBarIconPickResult) -> Unit)?) {
            val activity = WeakReference(activity)
            val app: Context = activity.applicationContext
            var processing = false
            var path: String? = null
            var delivery: Runnable? = null
        }
        override fun close() {
            synchronized(lock) {
                if (!closed) {
                    closed = true
                    pending.keys.toList().forEach { drop(it) }
                    main.removeCallbacksAndMessages(null)
                    worker.queue.clear()
                    worker.shutdown()
                    val handles = hooks.values.toList(); hooks.clear()
                    handles.forEach { handle -> runCatching { HookRegistry.get().unhook(handle) } }
                }
            }
            synchronized(owners) { owners.remove(this) }
        }
        private fun drop(code: Int) {
            val request = pending.remove(code) ?: return
            synchronized(codes) { codes.remove(code) }
            request.callback = null
            request.activity.clear()
            request.delivery?.let { main.removeCallbacks(it) }
            request.delivery = null
            request.path?.let { path -> runCatching { CustomBottomBarIconStore.delete(request.app, path) } }
            request.path = null
        }
        fun launch(activity: Activity, tabKey: String, callback: (CustomBottomBarIconPickResult) -> Unit) {
            check(Looper.myLooper() == Looper.getMainLooper())
            val code = synchronized(lock) {
                if (closed) return
                if (activity.isFinishing || activity.isDestroyed || pending.size >= 16 || !CustomBottomBarIconStore.isSupportedTabKey(tabKey.trim())) {
                    return@synchronized null
                }
                try { hookHierarchy(activity.javaClass) } catch (_: Throwable) { return@synchronized null }
                allocateCode().also { pending[it] = Pending(activity, tabKey.trim(), callback) }
            }
            if (code == null) { callback(CustomBottomBarIconPickResult.FAILED); return }
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try { activity.startActivityForResult(intent, code) } catch (_: Throwable) {
                try {
                    val fallback = Intent(Intent.ACTION_GET_CONTENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    activity.startActivityForResult(Intent.createChooser(fallback, "选择底栏图标"), code)
                } catch (_: Throwable) {
                    val notify = synchronized(lock) { pending[code]?.callback.also { drop(code) } }
                    notify?.invoke(CustomBottomBarIconPickResult.FAILED)
                }
            }
        }
        private fun hookHierarchy(clazz: Class<*>) {
            var current: Class<*>? = clazz
            while (current != null && Activity::class.java.isAssignableFrom(current)) {
                current.declaredMethods.filter { method ->
                    (method.name == "onDestroy" && method.parameterTypes.isEmpty()) ||
                        (method.name == "onActivityResult" && method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java)))
                }.forEach { method ->
                    if (!hooks.containsKey(method)) {
                        check(hooks.size < 64) { "Bottom icon picker hook cap reached" }
                        hooks[method] = HookRegistry.get().hook(method, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                val activity = param.thisObject as? Activity ?: return
                                if (method.name == "onDestroy") {
                                    synchronized(lock) {
                                        pending.entries.filter { it.value.activity.get() == null || it.value.activity.get() === activity }.map { it.key }.forEach { drop(it) }
                                    }
                                } else onResult(activity, param.args)
                            }
                        })
                    }
                }
                current = current.superclass
            }
        }
        private fun onResult(activity: Activity, args: Array<Any?>) {
            val code = args.getOrNull(0) as? Int ?: return
            val request = synchronized(lock) {
                if (closed) return
                val request = pending[code] ?: return
                if (request.activity.get() !== activity || request.processing) return
                request.processing = true
                request
            }
            val uri = (args.getOrNull(2) as? Intent)?.data
            if ((args.getOrNull(1) as? Int) != Activity.RESULT_OK || uri == null) {
                val callback = synchronized(lock) { pending[code]?.callback.also { drop(code) } }
                callback?.invoke(CustomBottomBarIconPickResult.CANCELLED); return
            }
            try { worker.execute {
                val admitted = synchronized(lock) { !closed && pending[code] === request }
                if (!admitted) return@execute
                val path = runCatching { CustomBottomBarIconStore.saveFromUri(request.app, request.key, uri) }.getOrNull()
                synchronized(lock) {
                    if (closed || pending[code] !== request) {
                        runCatching { CustomBottomBarIconStore.delete(request.app, path) }; return@synchronized
                    }
                    request.path = path
                    val delivery = Runnable {
                        val callback = synchronized(lock) {
                            if (closed || pending[code] !== request) return@Runnable
                            val target = request.activity.get()
                            if (target == null || target.isFinishing || target.isDestroyed) { drop(code); return@Runnable }
                            pending.remove(code)
                            synchronized(codes) { codes.remove(code) }
                            request.callback.also { request.callback = null; request.activity.clear(); request.path = null; request.delivery = null }
                        }
                        try { callback?.invoke(path?.let(CustomBottomBarIconPickResult::Saved) ?: CustomBottomBarIconPickResult.FAILED) }
                        catch (_: Throwable) { runCatching { CustomBottomBarIconStore.delete(request.app, path) } }
                    }
                    request.delivery = delivery
                    if (!main.post(delivery)) drop(code)
                }
            } } catch (_: RejectedExecutionException) {
                val callback = synchronized(lock) { pending[code]?.callback.also { drop(code) } }
                callback?.invoke(CustomBottomBarIconPickResult.FAILED)
            }
        }
    }
}
sealed class CustomBottomBarIconPickResult {
    data class Saved(val path: String) : CustomBottomBarIconPickResult()
    object CANCELLED : CustomBottomBarIconPickResult()
    object FAILED : CustomBottomBarIconPickResult()
}
