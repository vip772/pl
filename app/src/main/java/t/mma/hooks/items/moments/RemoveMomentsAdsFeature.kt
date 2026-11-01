package t.mma.hooks.items.moments
import t.mma.utils.HLog

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.HchatStorage
import t.mma.utils.KavaReflector

class RemoveMomentsAdsFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "去除朋友圈广告"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(RemoveMomentsAdsSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val scope = t.mma.hooks.core.FeatureResourceScope { HLog.e("$TAG 资源释放失败", it) }
        trackResource { scope.close() }
        installOwned(context.hostContext(), context.hostClassLoader(), scope)
    }

    companion object {
        const val ID = "remove_moments_ads"
        private const val TAG = "[Hchat:MomentsAds]"
        private const val AD_INFO_CLASS = "com.tencent.mm.plugin.sns.storage.ADInfo"

        @JvmStatic
        fun isEnabled(context: Context?): Boolean {
            if (context == null) return false
            val sp = HchatStorage.preferences(context, RemoveMomentsAdsSettings.PREFS_NAME)
            return sp.getBoolean(
                RemoveMomentsAdsSettings.KEY_ENABLE,
                RemoveMomentsAdsSettings.DEFAULT_ENABLE
            )
        }

        private fun installOwned(context: Context?, classLoader: ClassLoader?, scope: t.mma.hooks.core.FeatureResourceScope) {
            if (context == null || classLoader == null || scope.isClosed()) return
            synchronized(this) {
                val clazz = KavaReflector.loadClass(AD_INFO_CLASS, classLoader)
                if (clazz == null) {
                    HLog.e("$TAG 未找到朋友圈广告信息类: $AD_INFO_CLASS")
                    return
                }
                val constructor = KavaReflector.findConstructor(clazz, String::class.java)
                if (constructor == null) {
                    HLog.e("$TAG 未找到朋友圈广告信息构造方法: $AD_INFO_CLASS(String)")
                    return
                }
                runCatching {
                    val handle = HookRegistry.get().hook(constructor, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (scope.isClosed() || !isEnabled(context)) return
                            param.result = null
                        }
                    })
                    scope.register { HookRegistry.get().unhook(handle) }
                }.onFailure {
                    HLog.e("$TAG Hook 朋友圈广告信息失败: ${it.message}", it)
                }
            }
        }
    }
}
