package t.mma.hooks.items.profileid

import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.HookRegistry
import t.mma.utils.HLog
import t.mma.utils.KavaReflector
import java.util.WeakHashMap

/** Passive observer for the verified ordinary create-chatroom protocol (119), never add-member. */
internal class InitialGroupJoinObserver {
    private data class Request(val account: String, val selected: Set<String>)
    private val requests = WeakHashMap<Any, Request>()
    private val hooks = mutableListOf<XC_MethodHook.Unhook>()
    private var open = false

    @Synchronized fun start(loader: ClassLoader?, accept: (String, String, Set<String>) -> Unit) {
        if (open || loader == null || WeChatApis.version()?.versionName() != "8.0.78" ||
            WeChatApis.version()?.versionCode() != 3180L) return
        try {
            val type = loader.loadClass("qn.o")
            val dispatch = type.declaredMethods.single { it.name == "doScene" && it.parameterCount == 2 }
            val end = type.declaredMethods.single { it.name == "onGYNetEnd" && it.parameterCount == 6 }
            hooks += HookRegistry.get().hook(dispatch, object : XC_MethodHook() {
                override fun beforeHookedMethod(p: MethodHookParam) {
                    runCatching {
                        val account = JoinTimeRecovery.accountKey(loader)
                        val request = field(field(field(p.thisObject, "e"), "a"), "a")
                        val selected = (field(request, "f") as Collection<*>).mapNotNull {
                            it?.let { item -> text(loader, field(item, "d")) }
                        }.filter { it.isNotBlank() && it != account }.toSet()
                        synchronized(this@InitialGroupJoinObserver) {
                            if (open && account.isNotBlank() && selected.isNotEmpty() && requests.size < 32)
                                requests[p.thisObject] = Request(account, selected)
                        }
                    }.onFailure { HLog.e("[Hchat:profile_group_info] 建群请求观察失败", it) }
                }
            })
            hooks += HookRegistry.get().hook(end, object : XC_MethodHook() {
                override fun afterHookedMethod(p: MethodHookParam) {
                    val request = synchronized(this@InitialGroupJoinObserver) {
                        requests.remove(p.thisObject).takeIf { open }
                    } ?: return
                    if (p.hasThrowable() || p.args[1] != 0 || p.args[2] != 0 ||
                        request.account != JoinTimeRecovery.accountKey(loader)) return
                    runCatching {
                        val responseHolder = field(field(p.thisObject, "e"), "b")
                        if (call(responseHolder, "getRetCode") != 0) return@runCatching
                        val response = field(responseHolder, "a")
                        val group = text(loader, field(response, "i"))
                        if (!group.endsWith("@chatroom") || (field(response, "g") as Number).toInt() <= 0) return@runCatching
                        val joined = (field(response, "h") as Collection<*>).mapNotNull { item ->
                            if (item == null || (field(item, "e") as Number).toInt() != 0) null
                            else text(loader, field(item, "d"))
                        }.toSet().intersect(request.selected)
                        if (joined.isNotEmpty()) accept(request.account, group, joined)
                    }.onFailure { HLog.e("[Hchat:profile_group_info] 建群成功观察失败", it) }
                }
            })
            open = true
        } catch (error: Throwable) {
            close()
            HLog.e("[Hchat:profile_group_info] 建群观察安装失败", error)
        }
    }

    @Synchronized fun close() {
        open = false
        hooks.forEach { HookRegistry.get().unhook(it) }
        hooks.clear()
        requests.clear()
    }

    private fun field(value: Any, name: String): Any =
        KavaReflector.readField(value, name) ?: error("Missing ${value.javaClass.name}.$name")
    private fun call(value: Any, name: String): Any? =
        KavaReflector.invokeOrThrow(KavaReflector.findCompatibleMethod(value.javaClass, name)
            ?: error("Missing $name"), value)
    private fun text(loader: ClassLoader, value: Any): String {
        val method = KavaReflector.findCompatibleMethod(loader.loadClass("x91.j1"), "g", value)
            ?: error("Missing native string decoder")
        return KavaReflector.invokeOrThrow(method, null, value) as? String ?: ""
    }
}
