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
            if (enabled) schedule() else stop()
        }
    }
    fun install() { prefs.registerOnSharedPreferenceChangeListener(listener); if (enabled) schedule() }
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
                                val talker = KavaReflector.invokeMethod(message, "N0") as? String ?: return
                                if (!talker.endsWith("@chatroom") || (KavaReflector.invokeMethod(message, "getType") as? Number)?.toInt() != 1 ||
                                    (KavaReflector.invokeMethod(message, "z0") as? Number)?.toInt() != 1) return
                                val content = KavaReflector.invokeMethod(message, "j") as? String ?: return
                                if ('@' !in content) return
                                val source = KavaReflector.readField(message, "G") as? String
                                if (source.isNullOrBlank()) { HLog.d("[Hchat:隐藏艾特] 跳过: 原生消息无MsgSource"); return }
                                val self = WeChatApis.contact().account()?.selfWxId().orEmpty()
                                if (self.isBlank()) { HLog.d("[Hchat:隐藏艾特] 跳过: 当前账号未就绪"); return }
                                val users = HiddenAtText.users(source.toByteArray(Charsets.UTF_8), self)
                                if (users.isEmpty()) { HLog.d("[Hchat:隐藏艾特] 跳过: MsgSource无可用艾特名单"); return }
                                val cleaned = HiddenAtText.clean(content, users.size)
                                if (cleaned == null) { HLog.d("[Hchat:隐藏艾特] 跳过: 艾特片段不匹配 count=${users.size}"); return }
                                val setter = KavaReflector.findMethodRecursive(message.javaClass, "b1", String::class.java) ?: error("正文setter缺失")
                                KavaReflector.invokeOrThrow(setter, message, cleaned)
                                check(KavaReflector.invokeMethod(message, "j") == cleaned)
                                HLog.d("[Hchat:隐藏艾特] 原生正文清理成功 count=${users.size} MsgSource保持原样")
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
