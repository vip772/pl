package t.mma.hooks.items.miniprogrambaselib
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
import t.mma.utils.KavaReflector
import org.json.JSONObject
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Constructor
import java.lang.reflect.Method

class FakeMiniProgramBaseLibFeature : BaseFeature() {
    override fun featureId(): String = ID
    override fun name(): String = "兼容低版本小程序"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(FakeMiniProgramBaseLibSettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(
            context.hostContext(),
            FakeMiniProgramBaseLibSettings.PREFS_NAME
        )
        val runtime = PreferenceRuntimeOwner(
            prefs, setOf(FakeMiniProgramBaseLibSettings.KEY_ENABLE),
            enabled = { isEnabled(context.hostContext()) },
            create = { resources ->
                val owner = MiniProgramCompatibilityRuntime(context, ::logFeatureError)
                resources.register { owner.close() }
                val installation = DexInstallScheduler.installation(ID, name(), stage = DexInstallScheduler.Stage.BRIDGE)
                resources.register { installation.close() }
                installation.schedule { owner.install() }
                val subscription = context.eventBus().subscribe(Events.DexReady::class.java, {
                    if (!resources.isClosed()) installation.schedule { owner.install() }
                })
                resources.register { context.eventBus().unsubscribe(subscription) }
            },
            onError = { logFeatureError("兼容低版本小程序启停失败", it) }
        )
        trackResource { runtime.close() }
        runtime.start()
    }

    private fun logFeatureError(message: String, throwable: Throwable?) {
        logError(message, throwable)
    }

    companion object {
        const val ID = "fake_mini_program_base_lib"
        private const val TAG = "[Hchat:FakeMiniProgramHostVersion]"
        internal const val CACHE_PREFS_NAME =
            "Hchat_fake_mini_program_host_version_member_cache"
        internal const val CACHE_PRIVATE_OPEN_URL_METHOD = "private_open_url_method"
        private fun processLogger(message: String, throwable: Throwable?) {
            HLog.e("$TAG $message", throwable)
        }
        private var appBrandHooker: MiniProgramAppBrandHooker? = null
        private var appBrandLoader: ClassLoader? = null

        @JvmStatic
        fun isEnabled(context: Context?): Boolean {
            if (context == null) return false
            return HchatStorage.preferences(context, FakeMiniProgramBaseLibSettings.PREFS_NAME)
                .getBoolean(
                    FakeMiniProgramBaseLibSettings.KEY_ENABLE,
                    FakeMiniProgramBaseLibSettings.DEFAULT_ENABLE
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
            if (existing != null && appBrandLoader === classLoader && existing.isInstalled()) return true
            // loader 换代时先释放旧所有者，避免新安装失败后还留着旧进程 Hook。
            if (appBrandLoader !== classLoader) destroyProcessHooks()
            val prefs = DexMethodCache.prefs(context, CACHE_PREFS_NAME)
            val runtimeKey = DexMethodCache.runtimeKey(context, classLoader)
            val privateOpenUrlMethod = DexMethodCache.loadCrossProcess(
                prefs,
                runtimeKey,
                classLoader,
                CACHE_PRIVATE_OPEN_URL_METHOD
            )?.takeIf(::isPrivateOpenUrlMethod) ?: return false
            val owner = appBrandHooker ?: MiniProgramAppBrandHooker(context, ::processLogger).also {
                appBrandHooker = it
                appBrandLoader = classLoader
            }
            return owner.install(privateOpenUrlMethod)
        }
    }
}

private class MiniProgramCompatibilityRuntime(
    private val context: FeatureContext,
    private val logger: (String, Throwable?) -> Unit
) {
    private val memberPrefs = DexMethodCache.prefs(
        context.hostContext(),
        FakeMiniProgramBaseLibFeature.CACHE_PREFS_NAME
    )
    private var closed = false
    private var installed = false
    private var hook: XC_MethodHook.Unhook? = null

    private fun report(message: String, error: Throwable?) {
        runCatching { logger(message, error) }
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        installed = false
        val oldHook = hook
        hook = null
        runCatching { HookRegistry.get().unhook(oldHook) }
            .onFailure { report("兼容低版本小程序 Hook 释放失败", it) }
    }

    @Synchronized
    fun install(): Boolean {
        if (closed) return false
        if (installed) return true
        return runCatching {
            val launchConstructor = locateLaunchConstructor() ?: return false
            locatePrivateOpenUrlMethod() ?: return false
            hook = HookRegistry.get().hook(launchConstructor, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // 只取关闭状态快照；设置读取与参数改写不占 runtime 监视器。
                    synchronized(this@MiniProgramCompatibilityRuntime) {
                        if (closed) return
                    }
                    if (!FakeMiniProgramBaseLibFeature.isEnabled(context.hostContext())) return
                    if (param.args.size <= LIB_VERSION_ARG_INDEX) return
                    param.args[LIB_VERSION_ARG_INDEX] = SPOOFED_LIB_VERSION
                }
            })
            installed = true
            true
        }.getOrElse {
            hook = null
            report("兼容低版本小程序Hook失败", it)
            false
        }
    }

    private fun locateLaunchConstructor(): Constructor<*>? {
        val runtimeKey = DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader())
        DexMethodCache.loadConstructor(
            memberPrefs,
            runtimeKey,
            context.hostClassLoader(),
            CACHE_LAUNCH_CONSTRUCTOR
        )?.takeIf(::isLaunchConstructor)?.let { return it }

        val candidates = runCatching {
            context.dexKitManager().withCurrentBridge { bridge ->
                bridge.findMethod(
                FindMethod().apply {
                    matcher(
                        MethodMatcher().apply {
                            usingEqStrings(CGI_LOG_TAG, CGI_CONSTRUCTOR_LOG)
                        }
                    )
                }
            ).mapNotNull { data ->
                if (!data.isConstructor) return@mapNotNull null
                runCatching {
                    data.getConstructorInstance(context.hostClassLoader())
                }.getOrNull()
            }.filter(::isLaunchConstructor)
                .distinctBy { it.toGenericString() }
            }
        }.getOrElse {
            logger("定位小程序启动请求构造器失败", it)
            emptyList()
        }

        val constructor = candidates.singleOrNull()
        if (constructor != null) {
            DexMethodCache.saveConstructor(
                memberPrefs,
                runtimeKey,
                CACHE_LAUNCH_CONSTRUCTOR,
                constructor
            )
        } else {
            DexMethodCache.clear(memberPrefs, runtimeKey, CACHE_LAUNCH_CONSTRUCTOR)
            if (candidates.size > 1) {
                logger("小程序启动请求构造器候选不唯一: ${candidates.joinToString { it.toGenericString() }}", null)
            }
        }
        return constructor
    }

    private fun locatePrivateOpenUrlMethod(): Method? {
        val runtimeKey = DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader())
        DexMethodCache.load(
            memberPrefs,
            runtimeKey,
            context.hostClassLoader(),
            FakeMiniProgramBaseLibFeature.CACHE_PRIVATE_OPEN_URL_METHOD
        )?.takeIf(::isPrivateOpenUrlMethod)?.let { return it }

        val candidates = runCatching {
            context.dexKitManager().withCurrentBridge { bridge ->
                bridge.findMethod(
                FindMethod().apply {
                    matcher(
                        MethodMatcher().apply {
                            usingEqStrings(
                                PRIVATE_OPEN_URL_API,
                                PRIVATE_OPEN_URL_RAW_URL_KEY,
                                PRIVATE_OPEN_URL_APP_ID_KEY
                            )
                        }
                    )
                }
            ).mapNotNull { data ->
                if (data.isConstructor) return@mapNotNull null
                runCatching { data.getMethodInstance(context.hostClassLoader()) }.getOrNull()
            }.filter(::isPrivateOpenUrlMethod)
                .distinctBy { it.toGenericString() }
            }
        }.getOrElse {
            logger("定位小程序私有网页跳转方法失败", it)
            emptyList()
        }

        val method = candidates.singleOrNull()
        if (method != null) {
            DexMethodCache.save(
                memberPrefs,
                runtimeKey,
                FakeMiniProgramBaseLibFeature.CACHE_PRIVATE_OPEN_URL_METHOD,
                method
            )
        } else {
            DexMethodCache.clear(
                memberPrefs,
                runtimeKey,
                FakeMiniProgramBaseLibFeature.CACHE_PRIVATE_OPEN_URL_METHOD
            )
            if (candidates.size > 1) {
                logger("小程序私有网页跳转方法候选不唯一: ${candidates.joinToString { it.toGenericString() }}", null)
            }
        }
        return method
    }

    private fun isLaunchConstructor(constructor: Constructor<*>): Boolean {
        val types = constructor.parameterTypes
        return types.size == 17 &&
            types[0] == String::class.java &&
            types[1] == java.lang.Boolean.TYPE &&
            types[5] == String::class.java &&
            types[LIB_VERSION_ARG_INDEX] == Integer.TYPE &&
            types[7].name == QUALITY_SESSION_CLASS &&
            types[8] == String::class.java &&
            types[9].name == LAUNCH_REFERRER_CLASS &&
            types[10] == String::class.java &&
            types[12] == Integer.TYPE &&
            types[13] == java.lang.Boolean.TYPE &&
            types[14] == String::class.java &&
            types[15] == java.lang.Boolean.TYPE &&
            types[16] == java.lang.Boolean.TYPE
    }

    companion object {
        private const val CACHE_LAUNCH_CONSTRUCTOR = "launch_wxa_app_constructor"
        private const val LIB_VERSION_ARG_INDEX = 6
        private const val SPOOFED_LIB_VERSION = 9999
        private const val QUALITY_SESSION_CLASS =
            "com.tencent.mm.plugin.appbrand.report.quality.QualitySession"
        private const val LAUNCH_REFERRER_CLASS =
            "com.tencent.mm.plugin.appbrand.config.AppBrandLaunchFromNotifyReferrer"
        private const val CGI_LOG_TAG = "MicroMsg.AppBrand.CgiLaunchWxaApp|func:1122"
        private const val CGI_CONSTRUCTOR_LOG =
            "<init> cgiHash[%d], username[%s] appId[%s] sync[%b] sessionId[%s] instanceId[%s] libVersion[%d], source:%s, launchMode:%d, migrate:%b, fallback:%b"
        private const val PRIVATE_OPEN_URL_API = "private_openUrl"
        private const val PRIVATE_OPEN_URL_RAW_URL_KEY = "rawUrl"
        private const val PRIVATE_OPEN_URL_APP_ID_KEY = "geta8key_open_webview_appid"
    }
}

/** 小程序(appbrand)进程私有网页跳转 Hook 的独立所有者。 */
private class MiniProgramAppBrandHooker(
    private val context: Context,
    private val logger: (String, Throwable?) -> Unit
) {
    private var closed = false
    private var installed = false
    private var hook: XC_MethodHook.Unhook? = null

    private fun report(message: String, error: Throwable?) {
        runCatching { logger(message, error) }
    }

    @Synchronized
    fun isInstalled(): Boolean = installed && !closed

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        installed = false
        val oldHook = hook
        hook = null
        runCatching { HookRegistry.get().unhook(oldHook) }
            .onFailure { report("小程序进程升级跳转Hook释放失败", it) }
    }

    @Synchronized
    fun install(method: Method): Boolean {
        if (closed) return false
        if (installed) return true
        return runCatching {
            hook = HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    synchronized(this@MiniProgramAppBrandHooker) {
                        if (closed) return
                    }
                    if (!FakeMiniProgramBaseLibFeature.isEnabled(context)) return
                    val request = param.args.getOrNull(1) as? JSONObject ?: return
                    val url = request.optString("url")
                    if (!isWeChatUpdateUrl(url)) return
                    request.put("url", "")
                }
            })
            installed = true
            true
        }.getOrElse {
            hook = null
            report("小程序进程升级跳转Hook安装失败", it)
            false
        }
    }
}

private fun isPrivateOpenUrlMethod(method: Method): Boolean {
    return !KavaReflector.isStatic(method) &&
        method.returnType == java.lang.Void.TYPE &&
        method.parameterCount == 3 &&
        method.parameterTypes[1] == JSONObject::class.java &&
        method.parameterTypes[2] == Integer.TYPE &&
        method.declaringClass.name.startsWith("com.tencent.mm.plugin.appbrand.jsapi.")
}

private fun isWeChatUpdateUrl(url: String): Boolean {
    return url == "https://support.weixin.qq.com/update" ||
        url.startsWith("https://support.weixin.qq.com/update/") ||
        url == "https://szsupport.weixin.qq.com/update" ||
        url.startsWith("https://szsupport.weixin.qq.com/update/")
}
