package t.mma.hooks.api.message

import de.robv.android.xposed.XC_MethodHook
import t.mma.dexkit.DexMethodCache
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.utils.HLog
import t.mma.utils.KavaReflector as R
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.io.Closeable
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList

/** 8.0.78: yb(e9) delegates to Bb(e9, boolean); both emoji and text pass Bb. */
object OutgoingAtMessageHook {
    interface Observer {
        fun before(param: XC_MethodHook.MethodHookParam) {}
        fun after(param: XC_MethodHook.MethodHookParam) {}
    }
    private data class Client(val id: String, val callback: Observer)
    private val clients = CopyOnWriteArrayList<Client>()
    private var hook: XC_MethodHook.Unhook? = null
    fun locate(context: FeatureContext): Method {
        val version = context.hostContext().packageManager.getPackageInfo(context.hostContext().packageName, 0).versionName
        require(version == "8.0.78") { "发送艾特当前仅验证8.0.78，实际=$version" }
        val loader = context.hostClassLoader()
        val prefs = DexMethodCache.prefs(context.hostContext(), "Hchat_outgoing_at_dex_v2")
        val key = DexMethodCache.runtimeKey(context.hostContext(), loader)
        DexMethodCache.load(prefs, key, loader, "insert")?.let { return it }
        val query = FindMethod().apply { matcher(MethodMatcher().apply { usingStrings(listOf("MsgInfo processAddMsg insert db error")) }) }
        val entry = context.dexKitBridge().findMethod(query).map { it.getMethodInstance(loader) }.singleOrNull {
            it.returnType == java.lang.Long.TYPE && it.parameterTypes.size == 1 && !R.isStatic(it)
        } ?: error("消息入库直接字符串入口不唯一")
        val message = entry.parameterTypes[0]
        val method = R.findMethodRecursive(entry.declaringClass, "Bb", message, Boolean::class.javaPrimitiveType!!)
            ?: error("8.0.78入库核心签名不符")
        require(method.returnType == java.lang.Long.TYPE && !R.isAbstract(method))
        for (name in listOf("N0", "getType", "z0", "j")) require(R.findMethodRecursive(message, name) != null)
        require(R.findMethodRecursive(message, "b1", String::class.java) != null)
        require(R.findMethodRecursive(message, "r3", String::class.java) != null)
        require(R.findFieldRecursive(message, "G")?.type == String::class.java)
        DexMethodCache.save(prefs, key, "insert", method)
        return method
    }
    @Synchronized fun register(method: Method, id: String, callback: Observer): Closeable {
        require(clients.none { it.id == id })
        if (hook == null) hook = HookRegistry.get().hook(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                clients.forEach { try { it.callback.before(param) } catch (e: Throwable) { HLog.e("[Hchat:发送艾特] ${it.id} before", e) } }
            }
            override fun afterHookedMethod(param: MethodHookParam) {
                clients.forEach { try { it.callback.after(param) } catch (e: Throwable) { HLog.e("[Hchat:发送艾特] ${it.id} after", e) } }
            }
        })
        clients += Client(id, callback)
        return Closeable { synchronized(this) {
            clients.removeAll { it.id == id }
            if (clients.isEmpty()) { hook?.let { HookRegistry.get().unhook(it) }; hook = null }
        } }
    }
}
