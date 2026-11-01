package t.mma.hooks.items.hidemenu
import t.mma.utils.HLog

import android.view.MenuItem
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XCallback
import t.mma.event.Events
import t.mma.hooks.api.message.SingleMessageMenuLocator
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.HchatStorage
import t.mma.utils.KavaReflector
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.hooks.core.FeatureResourceScope

class HideChatMenuFeature : BaseFeature() {
    override fun featureId(): String = ID
    override fun name(): String = "隐藏长按菜单"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(HideChatMenuSettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), HideChatMenuSettings.PREFS_NAME)
        var schedule: (() -> Unit)? = null
        val owner = PreferenceRuntimeOwner(prefs, setOf(HideChatMenuSettings.KEY_ENABLE),
            enabled = { prefs.getBoolean(HideChatMenuSettings.KEY_ENABLE, HideChatMenuSettings.DEFAULT_ENABLE) },
            create = { resources ->
                val runtime = HideChatMenuRuntime(context, resources)
                val ticket = DexInstallScheduler.installation(ID, name())
                val install = { ticket.schedule { runtime.install() } }
                schedule = install
                resources.register { if (schedule === install) schedule = null }
                resources.register { ticket.close() }
                install()
            }, onError = { logError("隐藏菜单启停失败", it) })
        trackResource { owner.close() }
        owner.start()
        subscribe(Events.DexReady::class.java) { schedule?.invoke() }
    }
    companion object {
        const val ID = "hide_chat_menu"
    }
}

private class HideChatMenuRuntime(
    private val context: FeatureContext,
    private val resources: FeatureResourceScope
) {
    private val prefs = HchatStorage.preferences(context.hostContext(), HideChatMenuSettings.PREFS_NAME)
    private val hookedMethods = HashSet<Method>()

    @Synchronized
    fun install(): Boolean {
        if (resources.isClosed()) return true
        val methods = SingleMessageMenuLocator.menuCreateMethods(context) { message, throwable ->
            HLog.e("$TAG $message", throwable)
        }
        var installed = 0
        methods.forEach { method ->
            if (hookedMethods.contains(method)) {
                installed++
            } else if (hookedMethods.add(method) && hookMenuCreate(method)) {
                installed++
            }
        }
        if (installed == 0) {
            HLog.e("$TAG 定位或安装聊天长按菜单 Hook 失败")
        }
        return installed > 0
    }

    private fun hookMenuCreate(method: Method): Boolean {
        if (Modifier.isAbstract(method.modifiers) || method.declaringClass.isInterface) {
            hookedMethods.remove(method)
            return false
        }
        return runCatching {
            val handle = HookRegistry.get().hook(method, object : XC_MethodHook(XCallback.PRIORITY_HIGHEST) {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!resources.isClosed()) hideConfiguredItems(param.args?.getOrNull(0))
                }
            })
            resources.register { HookRegistry.get().unhook(handle) }
            true
        }.getOrElse {
            hookedMethods.remove(method)
            HLog.e("$TAG 安装聊天长按菜单 Hook 失败: ${method.toGenericString()}", it)
            false
        }
    }

    private fun hideConfiguredItems(menu: Any?) {
        if (menu == null || !prefs.getBoolean(HideChatMenuSettings.KEY_ENABLE, HideChatMenuSettings.DEFAULT_ENABLE)) {
            return
        }
        val hiddenTitles = HideChatMenuSettings.parseTitles(
            prefs.getString(HideChatMenuSettings.KEY_TITLES, HideChatMenuSettings.DEFAULT_TITLES)
        )
        if (hiddenTitles.isEmpty()) return
        val size = (KavaReflector.invokeMethod(menu, "size") as? Number)?.toInt() ?: return
        val hiddenItemIds = LinkedHashSet<Int>()
        for (index in 0 until size) {
            val item = KavaReflector.invokeMethod(menu, "getItem", index) as? MenuItem ?: continue
            val title = item.title?.toString()?.trim().orEmpty()
            if (title in hiddenTitles) {
                hiddenItemIds += item.itemId
            }
        }
        hiddenItemIds.forEach { itemId ->
            KavaReflector.invokeMethod(menu, "removeItem", itemId)
        }
    }

    companion object {
        private const val TAG = "[Hchat:HideChatMenu]"
    }
}
