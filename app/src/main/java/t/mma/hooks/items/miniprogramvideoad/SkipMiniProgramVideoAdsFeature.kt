package t.mma.hooks.items.miniprogramvideoad
import t.mma.utils.HLog

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.HchatStorage
import t.mma.utils.KavaReflector
import org.json.JSONObject

class SkipMiniProgramVideoAdsFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "跳过小程序视频广告"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(SkipMiniProgramVideoAdsSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(
            context.hostContext(),
            SkipMiniProgramVideoAdsSettings.PREFS_NAME
        )
        val runtime = PreferenceRuntimeOwner(
            prefs, setOf(SkipMiniProgramVideoAdsSettings.KEY_ENABLE),
            enabled = { isEnabled(context.hostContext()) },
            create = { resources ->
                val owner = MiniProgramVideoAdsHooker(
                    context.hostContext(),
                    context.hostClassLoader(),
                    ::logFeatureError
                )
                resources.register { owner.close() }
                val installation = DexInstallScheduler.installation(
                    ID, name(), stage = DexInstallScheduler.Stage.BRIDGE
                )
                resources.register { installation.close() }
                installation.schedule { owner.install() }
                val subscription = context.eventBus().subscribe(Events.DexReady::class.java, {
                    if (!resources.isClosed()) installation.schedule { owner.install() }
                })
                resources.register { context.eventBus().unsubscribe(subscription) }
            },
            onError = { logFeatureError("跳过小程序视频广告启停失败", it) }
        )
        trackResource { runtime.close() }
        runtime.start()
    }

    private fun logFeatureError(message: String, throwable: Throwable?) {
        logError(message, throwable)
    }

    companion object {
        const val ID = "skip_mini_program_video_ads"
        private const val TAG = "[Hchat:MiniProgramVideoAds]"
        private var appBrandHooker: MiniProgramVideoAdsHooker? = null
        private var appBrandLoader: ClassLoader? = null

        private fun processLogger(message: String, throwable: Throwable?) {
            HLog.e("$TAG $message", throwable)
        }

        @JvmStatic
        fun isEnabled(context: Context?): Boolean {
            if (context == null) return false
            return HchatStorage.preferences(
                context,
                SkipMiniProgramVideoAdsSettings.PREFS_NAME
            ).getBoolean(
                SkipMiniProgramVideoAdsSettings.KEY_ENABLE,
                SkipMiniProgramVideoAdsSettings.DEFAULT_ENABLE
            )
        }

        /** 小程序进程所有者释放；设置关闭、条款未接受或 loader 更换时调用。 */
        @JvmStatic
        @Synchronized
        fun destroyProcessHooks() {
            val old = appBrandHooker
            appBrandHooker = null
            appBrandLoader = null
            old?.close()
        }

        @JvmStatic
        @Synchronized
        fun installAppBrandProcessHook(context: Context, classLoader: ClassLoader): Boolean {
            if (!isEnabled(context)) {
                destroyProcessHooks()
                return false
            }
            val existing = appBrandHooker
            if (existing != null && appBrandLoader === classLoader &&
                existing.isInstalled(classLoader)
            ) {
                return true
            }
            // loader 换代时先释放旧所有者，避免新安装失败后还留着旧进程 Hook。
            if (appBrandLoader !== classLoader) destroyProcessHooks()
            val owner = appBrandHooker ?: MiniProgramVideoAdsHooker(
                context, classLoader, ::processLogger
            ).also {
                appBrandHooker = it
                appBrandLoader = classLoader
            }
            return owner.install()
        }
    }
}

/** 小程序 JS Bridge 视频进度 Hook 的独立所有者：一个装载器一个句柄，可显式释放。 */
private class MiniProgramVideoAdsHooker(
    private val context: Context,
    private val classLoader: ClassLoader,
    private val logger: (String, Throwable?) -> Unit
) {
    private var closed = false
    private var installed = false
    private var hook: XC_MethodHook.Unhook? = null

    private fun report(message: String, error: Throwable?) {
        runCatching { logger(message, error) }
    }

    @Synchronized
    fun isInstalled(loader: ClassLoader): Boolean =
        installed && !closed && classLoader === loader

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        installed = false
        val oldHook = hook
        hook = null
        runCatching { HookRegistry.get().unhook(oldHook) }
            .onFailure { report("小程序视频广告Hook释放失败", it) }
    }

    @Synchronized
    fun install(): Boolean {
        if (closed) return false
        if (installed) return true
        return runCatching {
            val bridgeClass = KavaReflector.loadClass(BRIDGE_CLASS, classLoader)
            if (bridgeClass == null) {
                HLog.e("$TAG 未找到小程序 JS Bridge: $BRIDGE_CLASS")
                return false
            }
            val subscribeHandler = KavaReflector.findMethod(
                bridgeClass,
                "subscribeHandler",
                String::class.java,
                String::class.java,
                Integer.TYPE,
                String::class.java
            )
            if (subscribeHandler == null || subscribeHandler.returnType != java.lang.Void.TYPE) {
                HLog.e("$TAG 未找到 subscribeHandler(String,String,int,String)")
                return false
            }
            hook = HookRegistry.get().hook(subscribeHandler, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // 只取关闭状态快照；设置读取与参数改写不占 owner 监视器。
                    synchronized(this@MiniProgramVideoAdsHooker) {
                        if (closed) return
                    }
                    if (param.args.getOrNull(0) != EVENT_VIDEO_TIME_UPDATE) return
                    if (!SkipMiniProgramVideoAdsFeature.isEnabled(context)) return
                    val payload = param.args.getOrNull(1) as? String ?: return
                    val json = runCatching { JSONObject(payload) }.getOrNull() ?: return
                    json.put("position", 60)
                    json.put("duration", 1)
                    param.args[1] = json.toString()
                }
            })
            installed = true
            true
        }.getOrElse {
            hook = null
            report("安装小程序视频广告 Hook 失败", it)
            false
        }
    }

    private companion object {
        const val TAG = "[Hchat:MiniProgramVideoAds]"
        const val BRIDGE_CLASS =
            "com.tencent.mm.appbrand.commonjni.AppBrandJsBridgeBinding"
        const val EVENT_VIDEO_TIME_UPDATE = "onVideoTimeUpdate"
    }
}