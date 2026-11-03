package t.mma.hooks.items.realtail

import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.OwnedHooks
import t.mma.utils.KavaReflector
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

class BeforeTransferNameQuery(
    private val context: FeatureContext,
    private val logger: (String, Throwable?) -> Unit
) {
    @Volatile private var closed = false
    private var ownedHook: XC_MethodHook.Unhook? = null
    private val hooks = OwnedHooks { error ->
        try { logger("实名查询Hook操作失败", error) } catch (_: Throwable) { }
    }
    @Synchronized fun destroy() {
        if (closed) return
        closed = true
        try { hooks.close() } finally {
            ownedHook = null; pending.clear(); initialized = false
            sceneClass = null; sceneCtor = null; callbackMethod = null
        }
    }
    fun interface Callback {
        fun onResult(maskedName: String)
    }

    private var sceneClass: Class<*>? = null
    private var sceneCtor: Constructor<*>? = null
    private var callbackMethod: Method? = null
    private val pending = Collections.synchronizedMap(WeakHashMap<Any, Callback>())
    private val featurePrefs = context.configStore().getFeaturePrefs("real_name_tail_runtime")
    @Volatile private var initialized = false

    fun ensureReady(): Boolean = DexInstallScheduler.queryDexKit { ensureReadyLocked() }
    @Synchronized
    private fun ensureReadyLocked(): Boolean {
        if (closed) return false
        if (initialized && sceneCtor != null && callbackMethod != null) return true
        return runCatching {
            locateScene()
            setupCallbackHook()
            initialized = !closed && sceneCtor != null && callbackMethod != null && ownedHook != null
            initialized
        }.getOrElse {
            logger("实名查询初始化失败", it)
            false
        }
    }

    fun query(wxid: String, roomId: String, callback: Callback): Boolean {
        if (closed || !RealNameTailStore.isSupportedQueryId(wxid)) return false
        // DexKit's serial lock must be acquired before this instance monitor.
        if (!ensureReady()) return false
        return synchronized(this) {
            if (closed) return@synchronized false
            val ctor = sceneCtor ?: return@synchronized false
            runCatching {
                val scene = KavaReflector.newInstance(ctor, wxid, roomId) ?: return@runCatching false
                pending[scene] = callback
                var sent = false
                try {
                    sent = WeChatApis.network()?.sendRequest(scene) == true
                    sent
                } finally {
                    if (!sent) pending.remove(scene)
                }
            }.getOrElse {
                try { logger("实名查询请求失败", it) } catch (_: Throwable) { }
                false
            }
        }
    }

    private fun locateScene() {
        sceneClass = null
        sceneCtor = null
        callbackMethod = null

        loadCachedClass("beforetransfer_class")?.let {
            if (selectSceneClass(it)) return@let
        }

        if (sceneClass == null) {
            val matches = runCatching {
                context.dexKitManager().withCurrentBridge { bridge ->
                    bridge.findClass(
                    org.luckypray.dexkit.query.FindClass().apply {
                        matcher(org.luckypray.dexkit.query.matchers.ClassMatcher().apply {
                            usingStrings(listOf("/cgi-bin/mmpay-bin/beforetransfer"))
                        })
                    }
                    ).map { it.name }
                }
            }.getOrDefault(emptyList())
            for (name in matches) {
                val clazz = KavaReflector.loadClass(name, context.hostClassLoader())
                if (selectSceneClass(clazz)) break
            }
        }

        if (sceneClass == null) {
            val direct = KavaReflector.loadClass("com.tencent.mm.plugin.remittance.model.i", context.hostClassLoader())
            selectSceneClass(direct)
        }

        sceneClass?.let {
            saveCachedClass("beforetransfer_class", it)
            locateCallbackMethod(it)
        }
    }

    private fun selectSceneClass(clazz: Class<*>?): Boolean {
        if (clazz == null) return false
        for (ctor in KavaReflector.declaredConstructors(clazz)) {
            val types = ctor.parameterTypes
            if (types.size == 2 && types[0] == String::class.java && types[1] == String::class.java) {
                sceneClass = clazz
                sceneCtor = KavaReflector.accessible(ctor)
                return true
            }
        }
        return false
    }

    private fun locateCallbackMethod(clazz: Class<*>) {
        for (method in KavaReflector.declaredMethods(clazz)) {
            val types = method.parameterTypes
            if (types.size >= 4
                && types[0] == Integer.TYPE
                && types[1] == Integer.TYPE
                && types[2] == Integer.TYPE
                && types[3] == String::class.java
            ) {
                callbackMethod = KavaReflector.accessible(method)
                return
            }
        }
    }

    private fun setupCallbackHook() {
        val method = callbackMethod ?: return
        if (closed || ownedHook != null) return
        ownedHook = hooks.hook(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (closed) return
                val scene = param.thisObject ?: return
                val callback = pending.remove(scene) ?: return
                val maskedName = extractMaskedName(scene)
                callback.onResult(maskedName)
            }
        })
    }

    private fun extractMaskedName(scene: Any): String {
        val response = KavaReflector.readField(scene, "r") ?: return ""
        val fieldFour = KavaReflector.readField(response, "f") as? String
        return RealNameTailStore.normalizeMaskedName(fieldFour)
    }

    private fun loadCachedClass(key: String): Class<*>? {
        val className = featurePrefs.getString(cacheKey(key), "") ?: ""
        return KavaReflector.loadClass(className, context.hostClassLoader())
    }

    private fun saveCachedClass(key: String, clazz: Class<*>) {
        featurePrefs.edit().putString(cacheKey(key), clazz.name).apply()
    }

    private fun cacheKey(name: String): String {
        val version = WeChatApis.version()?.versionName() ?: "unknown"
        return "feature_${version}_$name"
    }
}
