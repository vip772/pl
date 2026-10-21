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

/** Modify the original native text object before insert; no second send and no fabricated lvbuffer. */
internal class HiddenAtRuntime(private val context: FeatureContext) {
    private val prefs = HiddenAtSettings.preferences(context.hostContext())
    @Volatile private var enabled = prefs.getBoolean(HiddenAtSettings.ENABLE, false)
    @Volatile private var alive = true
    private var handle: java.io.Closeable? = null
    private var generation = 0
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == HiddenAtSettings.ENABLE) {
            enabled = prefs.getBoolean(key, false)
            HLog.d("[Hchat:隐藏艾特] 配置变更 enabled=$enabled")
            if (enabled) schedule() else stop()
        }
    }
    fun install() {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        HLog.d("[Hchat:隐藏艾特] 初始化 enabled=$enabled process=${context.loadPackageParam().processName}")
        if (enabled) schedule()
    }
    private fun call(message: Any, name: String, vararg args: Any?): Any? {
        val method = KavaReflector.findMethodRecursive(message.javaClass, name, *args.map { it?.javaClass ?: Any::class.java }.toTypedArray())
            ?: error("消息方法缺失: $name")
        return KavaReflector.invokeOrThrow(method, message, *args)
    }
    private var observed = 0
    private fun diagnostic(stage: String) {
        if (observed++ < 40) HLog.d("[Hchat:隐藏艾特] $stage")
    }
    private fun schedule() {
        val epoch = generation
        t.mma.hooks.core.DexInstallScheduler.schedule("hidden_at_$epoch", "隐藏艾特") {
            synchronized(this) {
                if (!alive || !enabled || epoch != generation || handle != null) true
                else try {
                    val method = t.mma.hooks.api.message.OutgoingAtMessageHook.locate(context)
                    handle = t.mma.hooks.api.message.OutgoingAtMessageHook.register(method, "hidden_at", object : t.mma.hooks.api.message.OutgoingAtMessageHook.Observer {
                        override fun before(param: XC_MethodHook.MethodHookParam) {
                            if (!enabled) return
                            val message = param.args.firstOrNull() ?: return
                            try {
                                // Use throwing reflection: invokeMethod used to turn lookup/invocation failures into silent null.
                                val talker = call(message, "N0") as? String ?: error("talker非字符串")
                                val type = (call(message, "getType") as? Number)?.toInt() ?: error("type非数字")
                                val direction = (call(message, "z0") as? Number)?.toInt() ?: error("isSend非数字")
                                diagnostic("入库观察 type=$type direction=$direction group=${talker.endsWith("@chatroom")}")
                                if (!talker.endsWith("@chatroom") || type != 1 || direction != 1) return
                                val content = call(message, "j") as? String ?: error("正文非字符串")
                                if ('@' !in content) { diagnostic("跳过: 正文无艾特符号 length=${content.length}"); return }
                                val sourceField = KavaReflector.findFieldRecursive(message.javaClass, "G") ?: error("MsgSource字段缺失")
                                val source = KavaReflector.readField(sourceField, message) as? String
                                if (source.isNullOrBlank()) { HLog.d("[Hchat:隐藏艾特] 跳过: 原生消息无MsgSource"); return }
                                val self = WeChatApis.contact().account()?.selfWxId().orEmpty()
                                if (self.isBlank()) { HLog.d("[Hchat:隐藏艾特] 跳过: 当前账号未就绪"); return }
                                val users = HiddenAtText.users(source.toByteArray(Charsets.UTF_8), self)
                                if (users.isEmpty()) { HLog.d("[Hchat:隐藏艾特] 跳过: MsgSource无可用艾特名单"); return }
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
                                if (result == null) {
                                    val separators = content.filter { it in "\u2005\u200B\u2009\u200D\u2060" }.groupingBy { "U+%04X".format(it.code) }.eachCount()
                                    HLog.d("[Hchat:隐藏艾特] 跳过: 无可信艾特片段 count=${users.size} names=${names.size} separators=$separators")
                                    return
                                }
                                val cleaned = result.text
                                val setter = KavaReflector.findMethodRecursive(message.javaClass, "b1", String::class.java) ?: error("正文setter缺失")
                                try {
                                    KavaReflector.invokeOrThrow(setter, message, cleaned)
                                    check(call(message, "j") == cleaned) { "正文setter读回不符" }
                                    check(KavaReflector.readField(sourceField, message) == source) { "MsgSource发生变化" }
                                } catch (e: Throwable) {
                                    try { KavaReflector.invokeOrThrow(setter, message, content) }
                                    catch (restore: Throwable) { e.addSuppressed(restore) }
                                    throw e
                                }
                                HLog.d("[Hchat:隐藏艾特] 原生正文清理成功 count=${users.size} removed=${result.removed} exact=${result.exact} MsgSource保持原样")
                            } catch (e: Throwable) { HLog.e("[Hchat:隐藏艾特] 原生消息处理失败", e) }
                        }
                    })
                    HLog.d("[Hchat:隐藏艾特] 安装成功 entry=$method"); true
                } catch (e: Throwable) { HLog.e("[Hchat:隐藏艾特] 8.0.78入口适配失败", e); false }
            }
        }
    }
    @Synchronized private fun stop() { generation++; handle?.close(); handle = null }
    fun destroy() { alive = false; enabled = false; prefs.unregisterOnSharedPreferenceChangeListener(listener); stop() }
}
