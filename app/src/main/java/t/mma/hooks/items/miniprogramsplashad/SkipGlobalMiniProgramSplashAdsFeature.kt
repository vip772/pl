package t.mma.hooks.items.miniprogramsplashad
import t.mma.utils.HLog

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import t.mma.dexkit.DexMethodCache
import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.HchatStorage
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method

class SkipGlobalMiniProgramSplashAdsFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "跳过全局小程序开屏广告"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(SkipGlobalMiniProgramSplashAdsSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(
            context.hostContext(),
            SkipGlobalMiniProgramSplashAdsSettings.PREFS_NAME
        )
        val runtime = PreferenceRuntimeOwner(
            prefs, setOf(SkipGlobalMiniProgramSplashAdsSettings.KEY_ENABLE),
            enabled = { isEnabled(context.hostContext()) },
            create = { resources ->
                val owner = SkipGlobalMiniProgramSplashAdsRuntime(context, ::logFeatureError)
                resources.register { owner.close() }
                val installation = DexInstallScheduler.installation(
                    ID, name(), stage = DexInstallScheduler.Stage.BRIDGE, priority = -100
                )
                resources.register { installation.close() }
                installation.schedule { owner.install() }
                val subscription = context.eventBus().subscribe(Events.DexReady::class.java, {
                    if (!resources.isClosed()) installation.schedule { owner.install() }
                })
                resources.register { context.eventBus().unsubscribe(subscription) }
            },
            onError = { logFeatureError("小程序开屏广告启停失败", it) }
        )
        trackResource { runtime.close() }
        runtime.start()
    }

    private fun logFeatureError(message: String, throwable: Throwable?) {
        logError(message, throwable)
    }

    companion object {
        const val ID = "skip_global_mini_program_splash_ads"
        private const val TAG = "[Hchat:MiniProgramSplashAds]"
        internal const val CACHE_PREFS_NAME =
            "Hchat_skip_global_mini_program_splash_ads_method_cache"
        internal const val CACHE_SPLASH_AD_CHECK_METHOD = "splash_ad_check_method"
        private var appBrandHooker: MiniProgramSplashAdProcessOwner? = null
        private var appBrandLoader: ClassLoader? = null

        private fun processLogger(message: String, throwable: Throwable?) {
            HLog.e("$TAG $message", throwable)
        }

        @JvmStatic
        fun isEnabled(context: Context?): Boolean {
            if (context == null) return false
            return HchatStorage.preferences(
                context,
                SkipGlobalMiniProgramSplashAdsSettings.PREFS_NAME
            ).getBoolean(
                SkipGlobalMiniProgramSplashAdsSettings.KEY_ENABLE,
                SkipGlobalMiniProgramSplashAdsSettings.DEFAULT_ENABLE
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
            val owner = appBrandHooker ?: MiniProgramSplashAdProcessOwner(
                context, classLoader, ::processLogger
            ).also {
                appBrandHooker = it
                appBrandLoader = classLoader
            }
            return owner.requestInstall()
        }
    }
}

/** 主进程开屏广告资格 Hook 的所有者：一次启用周期一个运行时、一个句柄。 */
private class SkipGlobalMiniProgramSplashAdsRuntime(
    private val context: FeatureContext,
    private val logger: (String, Throwable?) -> Unit
) : AutoCloseable {
    private val cachePrefs = DexMethodCache.prefs(
        context.hostContext(),
        SkipGlobalMiniProgramSplashAdsFeature.CACHE_PREFS_NAME
    )
    private var closed = false
    private var installed = false
    private var hook: XC_MethodHook.Unhook? = null

    private fun report(message: String, error: Throwable?) {
        runCatching { logger(message, error) }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        installed = false
        val oldHook = hook
        hook = null
        runCatching { HookRegistry.get().unhook(oldHook) }
            .onFailure { report("小程序开屏广告Hook释放失败", it) }
    }

    @Synchronized
    fun install(): Boolean {
        if (closed) return false
        if (installed) return true
        return runCatching {
            val method = locateSplashAdCheckMethod() ?: return false
            hook = HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // 只取关闭状态快照；设置读取与结果改写不占运行时监视器。
                    synchronized(this@SkipGlobalMiniProgramSplashAdsRuntime) {
                        if (closed) return
                    }
                    if (!SkipGlobalMiniProgramSplashAdsFeature.isEnabled(context.hostContext())) return
                    param.result = false
                }
            })
            installed = true
            true
        }.getOrElse {
            hook = null
            report("小程序开屏广告 Hook 安装失败", it)
            false
        }
    }

    private fun locateSplashAdCheckMethod(): Method? {
        val runtimeKey = DexMethodCache.runtimeKey(
            context.hostContext(),
            context.hostClassLoader()
        )
        DexMethodCache.load(
            cachePrefs,
            runtimeKey,
            context.hostClassLoader(),
            SkipGlobalMiniProgramSplashAdsFeature.CACHE_SPLASH_AD_CHECK_METHOD
        )?.takeIf(::isSplashAdCheckMethod)?.let { return it }

        val candidates = runCatching {
            context.dexKitManager().withCurrentBridge { bridge ->
                bridge.findMethod(
                    FindMethod().apply {
                        matcher(
                            MethodMatcher().apply {
                                usingEqStrings(SPLASH_AD_LOG_TAG, SPLASH_AD_RESULT_LOG)
                            }
                        )
                    }
                ).mapNotNull { data ->
                    if (data.isConstructor) return@mapNotNull null
                    runCatching { data.getMethodInstance(context.hostClassLoader()) }.getOrNull()
                }.filter(::isSplashAdCheckMethod)
                    .distinctBy { it.toGenericString() }
            }
        }.getOrElse {
            report("定位小程序开屏广告资格方法失败", it)
            emptyList()
        }
        val method = candidates.singleOrNull()
        if (method != null) {
            DexMethodCache.save(
                cachePrefs,
                runtimeKey,
                SkipGlobalMiniProgramSplashAdsFeature.CACHE_SPLASH_AD_CHECK_METHOD,
                method
            )
        } else {
            DexMethodCache.clear(
                cachePrefs,
                runtimeKey,
                SkipGlobalMiniProgramSplashAdsFeature.CACHE_SPLASH_AD_CHECK_METHOD
            )
            if (candidates.size > 1) {
                report("小程序开屏广告资格方法候选不唯一: ${candidates.joinToString { it.toGenericString() }}", null)
            }
        }
        return method
    }

    private companion object {
        const val SPLASH_AD_LOG_TAG = "MicroMsg.AppBrandAdUtils[AppBrandSplashAd]"
        const val SPLASH_AD_RESULT_LOG = "isAdContact, appId:%s, canShowAd:%s"
    }
}

/** 小程序(appbrand)进程开屏广告资格 Hook 的独立所有者：句柄、重试票根都归它。 */
private class MiniProgramSplashAdProcessOwner(
    private val context: Context,
    private val classLoader: ClassLoader,
    private val logger: (String, Throwable?) -> Unit
) : AutoCloseable {
    private val cachePrefs = DexMethodCache.prefs(
        context,
        SkipGlobalMiniProgramSplashAdsFeature.CACHE_PREFS_NAME
    )
    private var closed = false
    private var installed = false
    private var hook: XC_MethodHook.Unhook? = null
    private var ticket: DexInstallScheduler.Installation? = null

    private fun report(message: String, error: Throwable?) {
        runCatching { logger(message, error) }
    }

    @Synchronized
    fun isInstalled(loader: ClassLoader): Boolean =
        installed && !closed && classLoader === loader

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        installed = false
        val oldTicket = ticket
        ticket = null
        val oldHook = hook
        hook = null
        oldTicket?.let { pending ->
            runCatching { pending.close() }
                .onFailure { report("小程序进程开屏广告调度释放失败", it) }
        }
        runCatching { HookRegistry.get().unhook(oldHook) }
            .onFailure { report("小程序进程开屏广告Hook释放失败", it) }
    }

    /** 描述符已就绪则当场安装；否则登记进程内的重试票根，等跨进程缓存补齐。 */
    @Synchronized
    fun requestInstall(): Boolean {
        if (closed) return false
        if (installed) return true
        val method = loadCrossProcessDescriptor()
        if (method != null && install(method)) return true
        scheduleRetry()
        return false
    }

    private fun loadCrossProcessDescriptor(): Method? {
        val runtimeKey = DexMethodCache.runtimeKey(context, classLoader)
        return DexMethodCache.loadCrossProcess(
            cachePrefs,
            runtimeKey,
            classLoader,
            SkipGlobalMiniProgramSplashAdsFeature.CACHE_SPLASH_AD_CHECK_METHOD
        )?.takeIf(::isSplashAdCheckMethod)
    }

    private fun scheduleRetry() {
        val pending = ticket ?: DexInstallScheduler.installation(
            "${SkipGlobalMiniProgramSplashAdsFeature.ID}:appbrand:" +
                "${classLoader.javaClass.name}@${System.identityHashCode(classLoader)}",
            "小程序开屏广告子进程 Hook",
            stage = DexInstallScheduler.Stage.EARLY,
            priority = -100
        ).also { ticket = it }
        pending.schedule { installWhenDescriptorReady() }
    }

    @Synchronized
    private fun installWhenDescriptorReady(): Boolean {
        if (closed) return false
        if (installed) return true
        val method = loadCrossProcessDescriptor() ?: return false
        return install(method)
    }

    @Synchronized
    private fun install(method: Method): Boolean {
        if (closed) return false
        if (installed) return true
        return runCatching {
            hook = HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    synchronized(this@MiniProgramSplashAdProcessOwner) {
                        if (closed) return
                    }
                    if (!SkipGlobalMiniProgramSplashAdsFeature.isEnabled(context)) return
                    param.result = false
                }
            })
            installed = true
            true
        }.getOrElse {
            hook = null
            report("小程序进程开屏广告 Hook 安装失败", it)
            false
        }
    }
}

private fun isSplashAdCheckMethod(method: Method): Boolean {
    return method.returnType == java.lang.Boolean.TYPE &&
        method.parameterCount == 1 &&
        method.parameterTypes[0].name.startsWith("com.tencent.mm.plugin.appbrand.")
}