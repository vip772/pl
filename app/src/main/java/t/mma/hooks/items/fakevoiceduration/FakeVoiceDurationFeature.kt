package t.mma.hooks.items.fakevoiceduration

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import t.mma.dexkit.DexMethodCache
import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.HchatStorage
import t.mma.utils.KavaReflector
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method

class FakeVoiceDurationFeature : BaseFeature() {

    override fun featureId(): String = ID

    override fun name(): String = "伪造语音时长"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(FakeVoiceDurationSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val installed = FakeVoiceDurationRuntime(context, ::logFeatureError)
        val ticket = DexInstallScheduler.installation(ID, name(), stage = DexInstallScheduler.Stage.BRIDGE)
        trackResource { installed.destroy() }
        trackResource { ticket.close() }
        ticket.schedule { installed.install() }
        subscribe(Events.DexReady::class.java) { ticket.schedule { installed.install() } }
    }
    private fun logFeatureError(message: String, throwable: Throwable?) {
        logError(message, throwable)
    }

    companion object {
        const val ID = "fake_voice_duration"

        @JvmStatic
        fun isEnabled(context: Context?): Boolean {
            if (context == null) return false
            val sp = HchatStorage.preferences(context, FakeVoiceDurationSettings.PREFS_NAME)
            return sp.getBoolean(
                FakeVoiceDurationSettings.KEY_ENABLE,
                FakeVoiceDurationSettings.DEFAULT_ENABLE
            )
        }

        @JvmStatic
        fun durationMillis(context: Context?): Int {
            if (context == null) return FakeVoiceDurationSettings.DEFAULT_DURATION_SECONDS * 1000
            val sp = HchatStorage.preferences(context, FakeVoiceDurationSettings.PREFS_NAME)
            val seconds = sp.getInt(
                FakeVoiceDurationSettings.KEY_DURATION_SECONDS,
                FakeVoiceDurationSettings.DEFAULT_DURATION_SECONDS
            ).coerceIn(
                FakeVoiceDurationSettings.MIN_DURATION_SECONDS,
                FakeVoiceDurationSettings.MAX_DURATION_SECONDS
            )
            return seconds * 1000
        }
    }
}

private class FakeVoiceDurationRuntime(
    private val context: FeatureContext,
    private val logger: (String, Throwable?) -> Unit
) {
    private val methodPrefs = DexMethodCache.prefs(
        context.hostContext(),
        "Hchat_fake_voice_duration_recorder_method_cache"
    )
    @Volatile private var installed = false
    private val resources = t.mma.hooks.core.FeatureResourceScope { logger("语音时长资源释放失败", it) }
    @Synchronized
    fun destroy() { resources.close() }

    @Synchronized
    fun install(): Boolean {
        if (resources.isClosed()) return false
        if (installed) return true
        return runCatching {
            val method = locateRecorderDurationMethod() ?: return false
            val handle = HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (resources.isClosed() || !FakeVoiceDurationFeature.isEnabled(context.hostContext())) return
                    param.result = FakeVoiceDurationFeature.durationMillis(context.hostContext()).toLong()
                }
            })
            resources.register { HookRegistry.get().unhook(handle) }
            installed = true
            true
        }.getOrElse {
            logger("伪造语音时长录音长度Hook失败", it)
            false
        }
    }

    private fun locateRecorderDurationMethod(): Method? {
        val runtimeKey = methodCacheKey()
        DexMethodCache.load(
            methodPrefs,
            runtimeKey,
            context.hostClassLoader(),
            CACHE_RECORDER_DURATION
        )?.takeIf { isRecorderDurationMethod(it) }?.let { return it }

        val candidates = runCatching {
            context.dexKitManager().withCurrentBridge { bridge ->
                bridge.findMethod(
                FindMethod().apply {
                    matcher(
                        MethodMatcher().apply {
                            usingEqStrings(RECORDER_LOG_TAG, STOP_SUCCESS_LOG)
                        }
                    )
                }
            ).mapNotNull { data ->
                runCatching { data.getMethodInstance(context.hostClassLoader()).declaringClass }.getOrNull()
            }.distinctBy { it.name }
                .flatMap { KavaReflector.declaredMethods(it) }
                .filter { isRecorderDurationMethod(it) }
                .distinctBy { it.toGenericString() }
            }
        }.getOrElse {
            logger("伪造语音时长定位录音器失败", it)
            emptyList()
        }

        val method = candidates.singleOrNull()
        if (method != null) {
            DexMethodCache.save(methodPrefs, runtimeKey, CACHE_RECORDER_DURATION, method)
        } else {
            DexMethodCache.clear(methodPrefs, runtimeKey, CACHE_RECORDER_DURATION)
            if (candidates.size > 1) {
                logger("伪造语音时长录音长度方法候选不唯一: ${candidates.joinToString { it.toGenericString() }}", null)
            }
        }
        return method
    }

    private fun isRecorderDurationMethod(method: Method): Boolean {
        return !KavaReflector.isStatic(method) &&
            method.parameterCount == 0 &&
            method.returnType == java.lang.Long.TYPE
    }

    private fun methodCacheKey(): String {
        return DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader())
    }

    companion object {
        private const val CACHE_RECORDER_DURATION = "recorder_duration"
        private const val RECORDER_LOG_TAG = "MicroMsg.SceneVoice.Recorder"
        private const val STOP_SUCCESS_LOG = "Stop file success: "
    }
}
