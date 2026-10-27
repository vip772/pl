package t.mma.hooks.items.nicknamewidth

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider
import t.mma.utils.HLog
import java.util.WeakHashMap

object NicknameWidthSettings {
    const val ID = "group_nickname_width"
    const val PREFS = "Hchat_group_nickname_width_config"
    const val ENABLE = "enable"
    const val TITLE = "解除群成员昵称长度限制"
    const val SUMMARY = "移除群成员昵称的固定最大宽度，使用消息区域可用空间"
}

class NicknameWidthSettingsProvider : SimpleFeatureSettingsProvider(
    NicknameWidthSettings.ID, NicknameWidthSettings.TITLE, NicknameWidthSettings.SUMMARY,
    FeatureSettingsProvider.CATEGORY_ENHANCE, true
)

/** Main-thread only. Own maxWidth, never text, ellipsis, line count or parent geometry. */
internal class NicknameWidthState {
    private val originals = WeakHashMap<TextView, Int>()
    fun bind(view: TextView, enabled: Boolean, group: Boolean) {
        if (!enabled || !group) {
            originals.remove(view)?.let { original ->
                if (view.maxWidth == Int.MAX_VALUE) view.maxWidth = original
            }
            return
        }
        val width = view.maxWidth
        if (width < 0 || width == Int.MAX_VALUE) return
        originals[view] = width
        view.maxWidth = Int.MAX_VALUE
    }
    fun restoreAll() {
        originals.forEach { (view, original) ->
            if (view.maxWidth == Int.MAX_VALUE) view.maxWidth = original
        }
        originals.clear()
    }
}

/** Verified 8.0.78 username binder; no global TextView hook or per-frame work. */
class NicknameWidthFeature : BaseFeature() {
    private val state = NicknameWidthState()
    private val main = Handler(Looper.getMainLooper())
    private var prefs: SharedPreferences? = null
    private var hook: XC_MethodHook.Unhook? = null
    @Volatile private var enabled = false
    @Volatile private var destroyed = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
        if (key == NicknameWidthSettings.ENABLE || key == null) {
            enabled = preferences.getBoolean(NicknameWidthSettings.ENABLE, false)
            if (!enabled) main.post { if (!enabled) state.restoreAll() }
        }
    }
    override fun featureId() = NicknameWidthSettings.ID
    override fun name() = NicknameWidthSettings.TITLE
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(NicknameWidthSettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        val preferences = HchatStorage.preferences(context.hostContext(), NicknameWidthSettings.PREFS)
        prefs = preferences
        enabled = preferences.getBoolean(NicknameWidthSettings.ENABLE, false)
        preferences.registerOnSharedPreferenceChangeListener(listener)
        try {
            val host = context.hostContext()
            val version = host.packageManager.getPackageInfo(host.packageName, 0).versionName
            if (version != "8.0.78") {
                HLog.w("[Hchat:NicknameWidth] 未验证微信版本 $version，未安装昵称宽度 Hook")
                return
            }
            val loader = context.hostClassLoader()
            val holder = Class.forName("com.tencent.mm.ui.chatting.viewitems.h0", false, loader)
            val chat = Class.forName("gk5.d", false, loader)
            val message = Class.forName("com.tencent.mm.storage.e9", false, loader)
            val owner = Class.forName("com.tencent.mm.ui.chatting.viewitems.b0", false, loader)
            val field = holder.getDeclaredField("userTV").apply { isAccessible = true }
            check(field.type == TextView::class.java)
            val talker = chat.getDeclaredMethod("x").apply { isAccessible = true }
            check(talker.returnType == String::class.java)
            val method = owner.getDeclaredMethod("p", holder, chat, message, String::class.java)
            check(method.returnType == Void.TYPE)
            hook = HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (destroyed || !enabled || Looper.myLooper() != Looper.getMainLooper()) return
                    try {
                        val view = field.get(param.args[0]) as? TextView ?: return
                        val room = talker.invoke(param.args[1]) as? String ?: ""
                        state.bind(view, enabled, room.endsWith("@chatroom") || room.endsWith("@im.chatroom"))
                    } catch (error: Throwable) {
                        // Stop a broken binder once; never spam logs while scrolling.
                        enabled = false
                        state.restoreAll()
                        HLog.e("[Hchat:NicknameWidth] 昵称宽度调整失败，已停止本次运行", error)
                    }
                }
            })
        } catch (error: Throwable) {
            HLog.e("[Hchat:NicknameWidth] 安装昵称宽度 Hook 失败", error)
        }
    }
    override fun onFeatureDestroy(context: FeatureContext) {
        destroyed = true
        enabled = false
        prefs?.unregisterOnSharedPreferenceChangeListener(listener)
        HookRegistry.get().unhook(hook)
        hook = null
        main.post { state.restoreAll() }
    }
}
