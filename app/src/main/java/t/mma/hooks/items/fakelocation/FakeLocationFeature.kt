package t.mma.hooks.items.fakelocation
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
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class FakeLocationFeature : BaseFeature() {
    override fun featureId(): String = ID
    override fun name(): String = "虚拟定位"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(FakeLocationSettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), FakeLocationSettings.PREFS_NAME)
        val runtime = PreferenceRuntimeOwner(
            prefs, setOf(FakeLocationSettings.KEY_ENABLE),
            enabled = { isEnabled(context.hostContext()) },
            create = { resources ->
                val owner = FakeLocationHooker(context, ::logFeatureError)
                resources.register { owner.close() }
                val installation = DexInstallScheduler.installation(ID, name())
                resources.register { installation.close() }
                installation.schedule { owner.install() }
                val subscription = context.eventBus().subscribe(Events.DexReady::class.java, {
                    if (!resources.isClosed()) installation.schedule { owner.install() }
                })
                resources.register { context.eventBus().unsubscribe(subscription) }
            },
            onError = { logFeatureError("虚拟定位启停失败", it) }
        )
        trackResource { runtime.close() }
        runtime.start()
    }

    private fun logFeatureError(message: String, throwable: Throwable?) {
        logError(message, throwable)
    }

    companion object {
        const val ID = "fake_location"
        internal const val CACHE_PREFS_NAME = "Hchat_fake_location_method_cache"
        internal const val CACHE_LOCATION_CALLBACKS = "location_callbacks_v1"

        @JvmStatic
        fun isEnabled(context: Context?): Boolean {
            if (context == null) return false
            return HchatStorage.preferences(context, FakeLocationSettings.PREFS_NAME)
                .getBoolean(FakeLocationSettings.KEY_ENABLE, FakeLocationSettings.DEFAULT_ENABLE)
        }

        private var appBrandOwner: FakeLocationProcessHooker? = null
        private var appBrandLoader: ClassLoader? = null
        @JvmStatic
        @Synchronized
        fun destroyProcessHooks() {
            val old = appBrandOwner
            appBrandOwner = null
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
            if (appBrandLoader !== classLoader) destroyProcessHooks()
            val prefs = DexMethodCache.prefs(context, CACHE_PREFS_NAME)
            val runtimeKey = DexMethodCache.runtimeKey(context, classLoader)
            val callbacks = DexMethodCache.loadListCrossProcess(
                prefs,
                runtimeKey,
                classLoader,
                CACHE_LOCATION_CALLBACKS
            ).filter(::isLocationCallback)
            if (callbacks.isEmpty()) return false
            val owner = appBrandOwner ?: FakeLocationProcessHooker(context) { message, throwable ->
                HLog.e("[Hchat:FakeLocation] $message", throwable)
            }.also {
                appBrandOwner = it
                appBrandLoader = classLoader
            }
            return owner.install(callbacks)
        }
    }
}

private class FakeLocationHooker(
    private val context: FeatureContext,
    private val logger: (String, Throwable?) -> Unit
) {
    private val cache = DexMethodCache.prefs(context.hostContext(), FakeLocationFeature.CACHE_PREFS_NAME)

    private val owner = FakeLocationProcessHooker(context.hostContext(), logger)
    private var closed = false
    @Volatile private var installed = false
    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        installed = false
        owner.close()
    }
    @Synchronized
    fun install(): Boolean {
        if (closed) return false
        if (installed) return true
        val callbacks = locateLocationCallbacks()
        if (callbacks.isEmpty()) return false
        installed = owner.install(callbacks)
        return installed
    }

    private fun locateLocationCallbacks(): List<Method> {
        val runtimeKey = DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader())
        DexMethodCache.loadList(
            cache,
            runtimeKey,
            context.hostClassLoader(),
            FakeLocationFeature.CACHE_LOCATION_CALLBACKS
        )
            .filter(::isLocationCallback)
            .takeIf { it.isNotEmpty() }
            ?.let { return it }

        val methods = LinkedHashSet<Method>()
        CALLBACK_ANCHORS.forEach { anchors ->
            val candidates = runCatching {
                context.dexKitManager().withCurrentBridge { bridge ->
                    bridge.findMethod(
                        FindMethod().apply {
                            matcher(MethodMatcher().apply { usingEqStrings(*anchors) })
                        }
                    ).mapNotNull { data ->
                        runCatching { data.getMethodInstance(context.hostClassLoader()) }.getOrNull()
                            ?.takeIf(::isLocationCallback)
                    }
                }
            }.getOrElse {
                logger("定位微信定位回调失败: ${anchors.joinToString()}", it)
                emptyList()
            }
            methods.addAll(candidates)
        }

        val result = methods.distinctBy { it.toGenericString() }
        if (result.isEmpty()) {
            DexMethodCache.clear(cache, runtimeKey, FakeLocationFeature.CACHE_LOCATION_CALLBACKS)
            logger("未找到微信定位回调方法", null)
        } else {
            DexMethodCache.saveList(
                cache,
                runtimeKey,
                FakeLocationFeature.CACHE_LOCATION_CALLBACKS,
                result
            )
        }
        return result
    }

    companion object {
        private val CALLBACK_ANCHORS = listOf(
            arrayOf("MicroMsg.SLocationListener"),
            arrayOf("MicroMsg.SLocationListenerWgs84"),
            arrayOf("MicroMsg.DefaultTencentLocationManager", "[mlocationListener]error:%d, reason:%s")
        )
    }
}

/** One process installation; callbacks, dynamic getters and rejection cache share its lifetime. */
private class FakeLocationProcessHooker(
    context: Context,
    private val logger: (String, Throwable?) -> Unit
) : AutoCloseable {
    private val context = context.applicationContext ?: context
    private var closed = false
    private val hookedCallbacks = linkedMapOf<Method, XC_MethodHook.Unhook>()
    private val hookedGetters = linkedMapOf<Method, XC_MethodHook.Unhook>()
    private val rejectedLocationClasses = linkedSetOf<Class<*>>()
    private fun report(message: String, error: Throwable?) {
        runCatching { logger(message, error) }
    }
    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        val handles = hookedCallbacks.values.toList() + hookedGetters.values.toList()
        hookedCallbacks.clear()
        hookedGetters.clear()
        rejectedLocationClasses.clear()
        handles.forEach { handle ->
            runCatching { HookRegistry.get().unhook(handle) }
                .onFailure { report("虚拟定位 Hook 释放失败", it) }
        }
    }
    @Synchronized
    fun install(callbacks: List<Method>): Boolean {
        if (closed || callbacks.isEmpty()) return false
        callbacks.forEach { callback ->
            if (hookedCallbacks.containsKey(callback) || hookedCallbacks.size >= 128) return@forEach
            runCatching {
                hookedCallbacks[callback] = HookRegistry.get().hook(callback, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        synchronized(this@FakeLocationProcessHooker) {
                            if (closed || !FakeLocationFeature.isEnabled(context)) return
                            runCatching { hookLocationGetters(param.args?.firstOrNull()) }
                                .onFailure { report("虚拟定位运行时 Hook 失败", it) }
                        }
                    }
                })
            }.onFailure { report("虚拟定位回调 Hook 安装失败: ${callback.toGenericString()}", it) }
        }
        return callbacks.all(hookedCallbacks::containsKey)
    }
    private fun hookLocationGetters(location: Any?) {
        if (closed || location == null) return
        val clazz = location.javaClass
        if (clazz in rejectedLocationClasses) return
        val latitudeMethod = KavaReflector.findMethodRecursive(clazz, "getLatitude")
        val longitudeMethod = KavaReflector.findMethodRecursive(clazz, "getLongitude")
        if (!isConcreteDoubleGetter(latitudeMethod) || !isConcreteDoubleGetter(longitudeMethod)) {
            if (rejectedLocationClasses.size < 64 && rejectedLocationClasses.add(clazz)) {
                report("TencentLocation 经纬度 getter 不匹配: ${clazz.name}", null)
            }
            return
        }
        hookGetter(latitudeMethod!!) { FakeLocationSettings.latitude(context) }
        hookGetter(longitudeMethod!!) { FakeLocationSettings.longitude(context) }
    }
    private fun hookGetter(method: Method, value: () -> Double) {
        if (closed || hookedGetters.containsKey(method) || hookedGetters.size >= 256) return
        hookedGetters[method] = HookRegistry.get().hook(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                synchronized(this@FakeLocationProcessHooker) {
                    if (closed || !FakeLocationFeature.isEnabled(context)) return
                    param.result = value()
                }
            }
        })
    }
    private fun isConcreteDoubleGetter(method: Method?): Boolean {
        if (method == null) return false
        return !Modifier.isAbstract(method.modifiers) &&
            !Modifier.isStatic(method.modifiers) &&
            method.parameterTypes.isEmpty() &&
            (method.returnType == java.lang.Double.TYPE || method.returnType == java.lang.Double::class.java)
    }
}
private fun isLocationCallback(method: Method): Boolean {
    val types = method.parameterTypes
    return !Modifier.isAbstract(method.modifiers) &&
        !Modifier.isStatic(method.modifiers) &&
        method.name == "onLocationChanged" &&
        method.returnType == Void.TYPE &&
        types.size == 3 &&
        types[0].name.contains("TencentLocation") &&
        types[1] == Integer.TYPE &&
        types[2] == String::class.java
}
