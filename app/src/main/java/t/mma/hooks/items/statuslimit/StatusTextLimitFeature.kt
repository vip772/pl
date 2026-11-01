package t.mma.hooks.items.statuslimit
import t.mma.utils.HLog

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.HchatStorage
import t.mma.utils.KavaReflector

class StatusTextLimitFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "解除状态词长度限制"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(StatusTextLimitSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val scope = t.mma.hooks.core.FeatureResourceScope { HLog.e("$TAG 资源释放失败", it) }
        trackResource { scope.close() }
        installOwned(context.hostContext(), context.hostClassLoader(), scope)
    }

    companion object {
        const val ID = "status_text_limit"
        private const val TAG = "[Hchat:StatusTextLimit]"
        private const val STATUS_ACTIVITY = "com.tencent.mm.plugin.textstatus.ui.TextStatusDoWhatActivityV2"
        private const val WECHAT_DEFAULT_LIMIT = 10
        private const val HCHAT_LIMIT = 2000


        @JvmStatic
        fun isEnabled(context: Context?): Boolean {
            if (context == null) return false
            val sp = HchatStorage.preferences(context, StatusTextLimitSettings.PREFS_NAME)
            return sp.getBoolean(StatusTextLimitSettings.KEY_ENABLE, StatusTextLimitSettings.DEFAULT_ENABLE)
        }

        private fun installOwned(context: Context?, classLoader: ClassLoader?, scope: t.mma.hooks.core.FeatureResourceScope) {
            if (context == null || classLoader == null || scope.isClosed()) return
            synchronized(this) {
                val activityClass = KavaReflector.loadClass(STATUS_ACTIVITY, classLoader)
                if (activityClass == null) {
                    HLog.e("$TAG 未找到状态词编辑页类")
                    return
                }
                val constructors = KavaReflector.declaredConstructors(activityClass)
                if (constructors.isEmpty()) {
                    HLog.e("$TAG 未找到状态词编辑页构造方法")
                    return
                }
                constructors.forEach { constructor ->
                    val handle = HookRegistry.get().hook(constructor, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (scope.isClosed() || !isEnabled(context)) return
                            expandLimit(param.thisObject)
                        }
                    })
                    scope.register { HookRegistry.get().unhook(handle) }
                }
            }
        }

        private fun expandLimit(activity: Any?) {
            if (activity == null) return
            var changed = false
            KavaReflector.declaredFields(activity.javaClass)
                .asSequence()
                .filter { it.type == Integer.TYPE && !KavaReflector.isStatic(it) }
                .filter { KavaReflector.readField(it, activity) == WECHAT_DEFAULT_LIMIT }
                .forEach { field ->
                    if (KavaReflector.writeField(field, activity, HCHAT_LIMIT)) {
                        changed = true
                    }
                }
            if (!changed) {
                HLog.e("$TAG 未找到可修改的状态词长度字段")
            }
        }
    }
}
