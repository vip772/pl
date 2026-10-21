package t.mma.hooks.items.hiddenat

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
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

/** Only intercept inserts; status updates must never trigger a second send. */
internal class HiddenAtRuntime(private val context: FeatureContext) {
    private val prefs = HiddenAtSettings.preferences(context.hostContext())
    @Volatile private var enabled = prefs.getBoolean(HiddenAtSettings.ENABLE, false)
    private val handles = mutableListOf<XC_MethodHook.Unhook>()
    private data class Sending(val talker: String, val title: String, val users: List<String>, var rowId: Long = 0)
    private val sending = ThreadLocal<Sending>()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == HiddenAtSettings.ENABLE) {
            enabled = prefs.getBoolean(HiddenAtSettings.ENABLE, false)
            if (enabled) installHooks() else uninstallHooks()
        }
    }
    fun install() { prefs.registerOnSharedPreferenceChangeListener(listener); if (enabled) installHooks() }
    @Synchronized private fun installHooks() {
        if (handles.isNotEmpty()) return
        try {
            val database = KavaReflector.loadClass("com.tencent.wcdb.database.SQLiteDatabase", context.hostClassLoader()) ?: error("微信 WCDB 类不可用")
            val method = KavaReflector.findMethodRecursive(database, "insertWithOnConflict", String::class.java, String::class.java, ContentValues::class.java, Int::class.javaPrimitiveType!!) ?: error("WCDB insertWithOnConflict 签名不可用")
            handles += HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[0] != "message") return
                    val values = param.args[2] as? ContentValues ?: return
                    val active = sending.get()
                    if (active != null) {
                        if (matchesReplacement(values, active)) values.put("lvbuffer", HiddenAtText.buffer(active.users))
                        return
                    }
                    if (!enabled) return
                    try { convert(values, param) } catch (e: Throwable) { HLog.e("[Hchat:隐藏艾特] 转换失败，保留原消息", e) }
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.args[0] != "message" || param.hasThrowable()) return
                    val active = sending.get() ?: return
                    val values = param.args[2] as? ContentValues ?: return
                    if (matchesReplacement(values, active)) active.rowId = (param.result as? Number)?.toLong()?.takeIf { it > 0 } ?: active.rowId
                }
            })
        } catch (e: Throwable) { HLog.e("[Hchat:隐藏艾特] 安装失败，未修改消息；宿主=${context.hostContext().packageName}", e) }
    }
    private fun matchesReplacement(v: ContentValues, active: Sending): Boolean =
        v.getAsString("talker") == active.talker && v.getAsInteger("isSend") == 1 && v.getAsInteger("type") != 1 &&
            v.getAsString("content")?.let { it.contains("<type>57</type>") && it.contains("<title>${escape(active.title)}</title>") } == true
    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun convert(v: ContentValues, param: XC_MethodHook.MethodHookParam) {
        if (v.getAsInteger("isSend") != 1 || v.getAsInteger("type") != 1) return
        val talker = v.getAsString("talker")?.takeIf { it.endsWith("@chatroom") } ?: return
        val content = v.getAsString("content")?.takeIf { '@' in it } ?: return
        val buffer = v.getAsByteArray("lvbuffer") ?: return
        val self = WeChatApis.contact().account()?.selfWxId()?.takeIf { it.isNotBlank() } ?: return
        val users = HiddenAtText.users(buffer, self)
        if (users.isEmpty()) return
        val title = HiddenAtText.clean(content, users.size) ?: return
        val api = WeChatApis.messages() ?: return
        if (!api.canSendXml()) return
        val active = Sending(talker, title, users)
        sending.set(active)
        try {
            val accepted = api.sendXml(talker, HiddenAtText.xml(title, users))
            if (accepted && active.rowId > 0) param.result = active.rowId
            else if (accepted) HLog.e("[Hchat:隐藏艾特] 发送接口已接受但未捕获替代消息写库，保留原消息；需实机核查发送时序", IllegalStateException("replacement row missing"))
        } finally { sending.remove() }
    }
    @Synchronized private fun uninstallHooks() {
        handles.forEach { HookRegistry.get().unhook(it) }; handles.clear()
    }
    fun destroy() {
        enabled = false; prefs.unregisterOnSharedPreferenceChangeListener(listener)
        uninstallHooks()
        sending.remove()
    }
}