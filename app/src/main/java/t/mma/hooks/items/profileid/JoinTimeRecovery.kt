package t.mma.hooks.items.profileid

import android.os.SystemClock
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.HookRegistry
import t.mma.utils.HLog
import t.mma.utils.KavaReflector
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Read-only, bounded full-detail recovery for the verified 8.0.78 protocol. */
internal object JoinTimeRecovery {
    private data class Result(val times: Map<String, Long>, val at: Long)
    private class Request(val account: String, val group: String, val epoch: Long, val queue: Any) {
        val done = CountDownLatch(1)
    }
    private val lock = Any()
    private val pending = IdentityHashMap<Any, Request>()
    private val cache = object : LinkedHashMap<String, Result>(32, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Result>?) = size > 32
    }
    private var hook: XC_MethodHook.Unhook? = null
    private var epoch = 0L
    private var account = ""
    private var enabled = false
    fun open() = synchronized(lock) { enabled = true }
    fun accountKey(loader: ClassLoader?): String = try {
        if (WeChatApis.version()?.versionName() != "8.0.78") "native-provider"
        else if (loader == null) "" else static(loader, "b41.y1", "u") as? String ?: ""
    } catch (_: Throwable) { "" }
    private fun releaseIdleHook() {
        if (pending.isEmpty()) {
            val old = hook
            hook = null
            HookRegistry.get().unhook(old)
        }
    }
    private fun cancel(scene: Any, request: Request) {
        try { call(request.queue, "d", scene) }
        catch (t: Throwable) { HLog.e("[Hchat:profile_group_info] 进群时间取消请求失败", t) }
        finally { pending.remove(scene); request.done.countDown() }
    }
    private fun field(value: Any, name: String): Any =
        KavaReflector.readField(value, name) ?: error("Missing ${value.javaClass.name}.$name")
    private fun call(value: Any, name: String, vararg args: Any?): Any? {
        val m = KavaReflector.findCompatibleMethod(value.javaClass, name, *args) ?: error("Missing method $name")
        return KavaReflector.invokeOrThrow(m, value, *args)
    }
    private fun static(loader: ClassLoader, type: String, name: String, vararg args: Any?): Any? {
        val c = loader.loadClass(type)
        val m = KavaReflector.findCompatibleMethod(c, name, *args) ?: error("Missing $type.$name")
        return KavaReflector.invokeOrThrow(m, null, *args)
    }
    /** Called only by the existing background group worker, never a view callback. */
    fun read(loader: ClassLoader?, group: String): Map<String, Long> {
        if (loader == null || !group.endsWith("@chatroom") ||
            WeChatApis.version()?.versionName() != "8.0.78" ||
            WeChatApis.version()?.versionCode() != 3180L) return emptyMap()
        try {
            val self = static(loader, "b41.y1", "u") as? String ?: return emptyMap()
            if (self.isBlank()) return emptyMap()
            lateinit var request: Request
            synchronized(lock) {
                if (!enabled) return emptyMap()
                if (account != self) {
                    epoch++
                    pending.entries.toList().forEach { cancel(it.key, it.value) }
                    cache.clear()
                    account = self
                }
                val previous = cache[group]
                if (previous != null && SystemClock.elapsedRealtime() - previous.at < 300_000L) return previous.times
                // Background-only requests with a hard bound.
                if (pending.size >= 2 || pending.values.any { it.group == group }) return previous?.times ?: emptyMap()
                val c = loader.loadClass("qn.w")
                if (hook == null) {
                    val m = KavaReflector.findDeclaredMethod(c, "onGYNetEnd", Int::class.javaPrimitiveType!!,
                        Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, String::class.java,
                        loader.loadClass("com.tencent.mm.network.y0"), ByteArray::class.java) ?: error("Callback unavailable")
                    hook = HookRegistry.get().hook(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(p: MethodHookParam) { receive(p) }
                    })
                }
                val ctor = KavaReflector.findConstructor(c, String::class.java, Int::class.javaPrimitiveType!!)
                    ?: error("Scene constructor unavailable")
                val scene = ctor.newInstance(group, 0)
                val body = field(field(field(scene, "d"), "a"), "a")
                check(KavaReflector.writeField(body, "e", 0))
                check((field(body, "e") as Number).toInt() == 0)
                val queue = field(static(loader, "gp0.j1", "q") ?: error("Kernel unavailable"), "b")
                request = Request(self, group, epoch, queue)
                pending[scene] = request
                cache[group] = Result(previous?.times ?: emptyMap(), SystemClock.elapsedRealtime())
                try {
                    check(call(queue, "g", scene) == true) { "Queue rejected request" }
                } catch (t: Throwable) {
                    cancel(scene, request)
                    releaseIdleHook()
                    throw t
                }
            }
            try { request.done.await(8, TimeUnit.SECONDS) }
            finally {
                synchronized(lock) {
                    pending.entries.firstOrNull { it.value === request }?.let { cancel(it.key, it.value) }
                    releaseIdleHook()
                }
            }
            return synchronized(lock) {
                if (enabled && request.epoch == epoch && request.account == account) cache[group]?.times ?: emptyMap()
                else emptyMap()
            }
        } catch (t: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (t: Throwable) {
            HLog.e("[Hchat:profile_group_info] 进群时间只读补查失败 version=8.0.78", t)
        }
        synchronized(lock) { releaseIdleHook() }
        return emptyMap()
    }
    private fun receive(p: XC_MethodHook.MethodHookParam) {
        val request = synchronized(lock) { pending[p.thisObject] } ?: return
        // Skip native processing for these exact scene identities only.
        p.result = null
        try {
            if (p.args[1] == 0 && p.args[2] == 0) {
                val response = field(field(field(p.thisObject, "d"), "b"), "a")
                val rows = field(field(response, "f"), "e") as Collection<*>
                check(rows.size <= 5000) { "Member response exceeds bound" }
                val times = HashMap<String, Long>()
                val now = System.currentTimeMillis() / 1000 + 86400
                for (row in rows) {
                    if (row == null) continue
                    val id = field(row, "d") as String
                    val seconds = (field(row, "v") as Number).toLong() and 0xffffffffL
                    if (id.isNotBlank() && seconds in 946684800L..now) times[id] = seconds * 1000
                }
                synchronized(lock) {
                    if (enabled && request.epoch == epoch && account == request.account) {
                        val merged = HashMap(cache[request.group]?.times ?: emptyMap())
                        merged.putAll(times) // Zero or omitted fields never erase a known time.
                        cache[request.group] = Result(merged, SystemClock.elapsedRealtime())
                    }
                }
            }
        } catch (t: Throwable) {
            HLog.e("[Hchat:profile_group_info] 进群时间回包提取失败", t)
        } finally {
            try { call(field(p.thisObject, "e"), "onSceneEnd", p.args[1], p.args[2], p.args[3], p.thisObject) }
            catch (t: Throwable) { HLog.e("[Hchat:profile_group_info] 进群时间请求结束通知失败", t) }
            synchronized(lock) {
                pending.remove(p.thisObject)
                releaseIdleHook()
            }
            request.done.countDown()
        }
    }
    fun close() = synchronized(lock) {
        enabled = false
        epoch++
        cache.clear()
        account = ""
        pending.entries.toList().forEach { cancel(it.key, it.value) }
        releaseIdleHook()
    }
}
