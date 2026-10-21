package t.mma.hooks.items.hiddenat

import android.content.Context
import android.content.SharedPreferences
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider
import t.mma.utils.HLog
import t.mma.utils.KavaReflector

object HiddenAtSettings {
    const val ID = "hidden_at"
    const val PREFS = "Hchat_hidden_at_config"
    const val ENABLE = "enable"
    fun preferences(c: Context) = HchatStorage.preferences(c, PREFS)
}

class HiddenAtFeature : BaseFeature() {
    private var runtime: HiddenAtRuntime? = null
    override fun featureId() = HiddenAtSettings.ID
    override fun name() = "隐藏艾特"
    override fun isEnabled(context: FeatureContext) = true
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(object : SimpleFeatureSettingsProvider(
            HiddenAtSettings.ID, "隐藏艾特", "隐藏群聊消息中的艾特文字，保留艾特名单",
            FeatureSettingsProvider.CATEGORY_ENHANCE, true
        ) {}, enabledCheck = { HiddenAtSettings.preferences(context.hostContext()).getBoolean(HiddenAtSettings.ENABLE, false) })
    }
    override fun onFeatureInstall(context: FeatureContext) {
        if (context.loadPackageParam().processName != context.loadPackageParam().packageName) return
        runtime = HiddenAtRuntime(context).also { it.install() }
    }
    override fun onFeatureDestroy(context: FeatureContext) { runtime?.destroy(); runtime = null }
}

/** Type57 replacement using a verified synchronous AppMsg insertion path. */
internal class HiddenAtRuntime(private val context: FeatureContext) {
    private val prefs = HiddenAtSettings.preferences(context.hostContext())
    @Volatile private var enabled = prefs.getBoolean(HiddenAtSettings.ENABLE, false)
    @Volatile private var alive = true
    private var handle: java.io.Closeable? = null
    private var generation = 0
    private val replacing = ThreadLocal<Boolean>()
    private data class Replacement(val talker: String, val users: List<String>)
    private val replacement = ThreadLocal<Replacement>()
    private val pending = HiddenAtPending { android.os.SystemClock.elapsedRealtime() }
    private var sourceHook: de.robv.android.xposed.XC_MethodHook.Unhook? = null
    private var appRouteHook: de.robv.android.xposed.XC_MethodHook.Unhook? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == HiddenAtSettings.ENABLE) {
            enabled = prefs.getBoolean(key, false)
            if (enabled) schedule() else stop()
        }
    }
    fun install() {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        if (enabled) schedule()
    }
    private fun call(message: Any, name: String, vararg args: Any?): Any? {
        val method = KavaReflector.findMethodRecursive(message.javaClass, name, *args.map { it?.javaClass ?: Any::class.java }.toTypedArray())
            ?: error("消息方法缺失: $name")
        return KavaReflector.invokeOrThrow(method, message, *args)
    }
    private fun schedule() {
        val epoch = generation
        t.mma.hooks.core.DexInstallScheduler.schedule("hidden_at_$epoch", "隐藏艾特") {
            synchronized(this) {
                if (!alive || !enabled || epoch != generation || handle != null) true
                else try {
                    val method = t.mma.hooks.api.message.OutgoingAtMessageHook.locate(context)
                    val appOwner = KavaReflector.loadClass("com.tencent.mm.pluginsdk.model.app.k0", context.hostClassLoader()) ?: error("AppMsg owner缺失")
                    val routeArg = KavaReflector.loadClass("com.tencent.mm.pluginsdk.model.app.s0", context.hostClassLoader()) ?: error("AppMsg route参数缺失")
                    val route = KavaReflector.findMethodRecursive(appOwner, "U", routeArg) ?: error("AppMsg U路由缺失")
                    check(route.returnType == Boolean::class.javaPrimitiveType)
                    appRouteHook = t.mma.hooks.core.HookRegistry.get().hook(route, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            // This exact call scope belongs only to our replacement; all ordinary sends keep their route.
                            if (alive && enabled && epoch == generation && replacing.get() == true) param.result = false
                        }
                    })
                    val sourceOwner = KavaReflector.loadClass("vu1.d1", context.hostClassLoader()) ?: error("上行source owner缺失")
                    val sourceMethod = KavaReflector.findMethodRecursive(sourceOwner, "a", method.parameterTypes[0]) ?: error("上行source入口缺失")
                    check(sourceMethod.returnType == String::class.java && !KavaReflector.isAbstract(sourceMethod))
                    sourceHook = t.mma.hooks.core.HookRegistry.get().hook(sourceMethod, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!alive || !enabled || epoch != generation || param.hasThrowable()) return
                            try {
                                val message = param.args[0] ?: return
                                val talker = call(message, "N0") as? String ?: return
                                val id = (call(message, "getMsgId") as? Number)?.toLong() ?: return
                                val users = pending.get(talker, id) ?: return
                                check(talker.endsWith("@chatroom") && (call(message, "z0") as? Number)?.toInt() == 1)
                                // d4.doScene uses nonempty a(message) directly for AppMsg field index 11.
                                // Only exact replacement (talker, localId) records enter this branch.
                                param.result = HiddenAtSource.merge(param.result as? String, users)
                            } catch (e: Throwable) { HLog.e("[Hchat:隐藏艾特] 上行source补入失败，未确认提醒", e) }
                        }
                    })
                    handle = t.mma.hooks.api.message.OutgoingAtMessageHook.register(method, "hidden_at", object : t.mma.hooks.api.message.OutgoingAtMessageHook.Observer {
                        override fun before(param: XC_MethodHook.MethodHookParam) {
                            if (!alive || !enabled || epoch != generation) return
                            val message = param.args.firstOrNull() ?: return
                            if (replacing.get() == true) {
                                val scope = replacement.get() ?: return
                                try {
                                    val talker = call(message, "N0") as? String ?: return
                                    val type = (call(message, "getType") as? Number)?.toInt() ?: return
                                    val direction = (call(message, "z0") as? Number)?.toInt() ?: return
                                    if (talker != scope.talker || type != 0x31000031 || direction != 1) return
                                    val field = KavaReflector.findFieldRecursive(message.javaClass, "G") ?: error("MsgSource字段缺失")
                                    val before = KavaReflector.readField(field, message) as? String
                                    val merged = HiddenAtSource.merge(before, scope.users)
                                    val setter = KavaReflector.findMethodRecursive(message.javaClass, "r3", String::class.java)
                                        ?: error("MsgSource原生setter缺失")
                                    // Run before Bb's source handlers and native convertTo/lvbuffer serialization.
                                    KavaReflector.invokeOrThrow(setter, message, merged)
                                    val verified = HiddenAtText.users((KavaReflector.readField(field, message) as? String).orEmpty()
                                        .toByteArray(Charsets.UTF_8), "").toSet() == scope.users.toSet()
                                    check(verified) { "替代消息本地名单写入校验失败" }
                                } catch (e: Throwable) {
                                    // Stop this replacement before insertion; outer text send retains its original message.
                                    param.result = -1L
                                    HLog.e("[Hchat:隐藏艾特] 本地名单补齐失败，阻止替代入库", e)
                                }
                                return
                            }
                            try {
                                // Use throwing reflection: invokeMethod used to turn lookup/invocation failures into silent null.
                                val talker = call(message, "N0") as? String ?: error("talker非字符串")
                                val type = (call(message, "getType") as? Number)?.toInt() ?: error("type非数字")
                                val direction = (call(message, "z0") as? Number)?.toInt() ?: error("isSend非数字")
                                if (!talker.endsWith("@chatroom") || type != 1 || direction != 1) return
                                val content = call(message, "j") as? String ?: error("正文非字符串")
                                if ('@' !in content) return
                                val sourceField = KavaReflector.findFieldRecursive(message.javaClass, "G") ?: error("MsgSource字段缺失")
                                val source = KavaReflector.readField(sourceField, message) as? String
                                if (source.isNullOrBlank()) return
                                val self = WeChatApis.contact().account()?.selfWxId().orEmpty()
                                if (self.isBlank()) return
                                val users = HiddenAtText.users(source.toByteArray(Charsets.UTF_8), self)
                                if (users.isEmpty()) return
                                val names = linkedSetOf<String>()
                                // Never enumerate all group members; look up only actual recipients, with a bounded synchronous budget.
                                if (users.size <= 16) {
                                    val contacts = WeChatApis.contact().contacts()
                                    for (id in users) {
                                        if (id == "notify@all") continue
                                        contacts?.getGroupMemberDisplayName(talker, id)?.takeIf { it.isNotEmpty() && it != id }?.let { names += it }
                                        contacts?.getDisplayName(id)?.takeIf { it.isNotEmpty() && it != id }?.let { names += it }
                                    }
                                }
                                val result = HiddenAtText.match(content, users.size, names)
                                if (result == null) return
                                val cleaned = result.text
                                val api = WeChatApis.messages() ?: return
                                if (!api.canSendXml()) return
                                replacement.set(Replacement(talker, users))
                                replacing.set(true)
                                val id = try { api.sendAppMsgXmlWithLocalId(talker, HiddenAtText.xml(cleaned, source)) }
                                    finally { replacing.remove(); replacement.remove() }
                                if (id > 0) {
                                    // Bb returns the actual replacement row ID; original native text is never modified.
                                    param.result = id
                                } else HLog.e("[Hchat:隐藏艾特] Type57未返回有效ID，保留原消息", IllegalStateException("AppMsg localId=$id"))
                            } catch (e: Throwable) { HLog.e("[Hchat:隐藏艾特] 原生消息处理失败", e) }
                        }
                        override fun after(param: XC_MethodHook.MethodHookParam) {
                            val scope = replacement.get() ?: return
                            if (!enabled || !alive || epoch != generation || param.hasThrowable()) return
                            try {
                                val message = param.args.firstOrNull() ?: return
                                val talker = call(message, "N0") as? String ?: return
                                if (talker != scope.talker || (call(message, "getType") as? Number)?.toInt()?.and(0xffff) != 49) return
                                val id = (param.result as? Number)?.toLong() ?: return
                                if (id <= 0) return
                                // Bb completes before k0.M queues d4, so bind before any network task starts.
                                pending.put(talker, id, scope.users)
                            } catch (e: Throwable) { HLog.e("[Hchat:隐藏艾特] 替代入库关联失败", e) }
                        }
                    })
                    true
                } catch (e: Throwable) {
                    handle?.close(); handle = null
                    sourceHook?.let { t.mma.hooks.core.HookRegistry.get().unhook(it) }; sourceHook = null
                    pending.clear()
                    appRouteHook?.let { t.mma.hooks.core.HookRegistry.get().unhook(it) }; appRouteHook = null
                    HLog.e("[Hchat:隐藏艾特] 8.0.78入口适配失败", e); false
                }
            }
        }
    }
    @Synchronized private fun stop() {
        generation++
        handle?.close(); handle = null
        sourceHook?.let { t.mma.hooks.core.HookRegistry.get().unhook(it) }; sourceHook = null
        appRouteHook?.let { t.mma.hooks.core.HookRegistry.get().unhook(it) }; appRouteHook = null
        pending.clear()
        replacement.remove()
        replacing.remove()
    }
    fun destroy() { alive = false; enabled = false; prefs.unregisterOnSharedPreferenceChangeListener(listener); stop() }
}
