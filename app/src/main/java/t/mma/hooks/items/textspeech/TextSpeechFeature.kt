package t.mma.hooks.items.textspeech
import t.mma.utils.HLog

import android.content.SharedPreferences
import t.mma.preferences.HchatStorage
import t.mma.hooks.core.FeatureResourceScope
import android.app.Activity
import java.util.concurrent.atomic.AtomicBoolean
import android.view.KeyEvent
import de.robv.android.xposed.XC_MethodHook
import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.utils.KavaReflector

class TextSpeechFeature : BaseFeature() {
    private var runtime: TextSpeechRuntime? = null
    private var runningResources: FeatureResourceScope? = null
    private var messageInstaller: (() -> Unit)? = null

    override fun featureId(): String = ID

    override fun name(): String = "文字转语音播报"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(TextSpeechSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val preferences = HchatStorage.preferences(context.hostContext(), TextSpeechSettings.PREFS_NAME)
        var active = true
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == TextSpeechSettings.KEY_ENABLE || key == null) {
                synchronized(this) { if (active) updateRuntime(context, preferences) }
            }
        }
        trackResource {
            synchronized(this) {
                active = false
                preferences.unregisterOnSharedPreferenceChangeListener(listener)
                stopRuntime()
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        subscribe(Events.DexReady::class.java) {
            val install = synchronized(this) { if (active) messageInstaller else null }
            install?.invoke()
        }
        updateRuntime(context, preferences)
    }
    @Synchronized
    private fun updateRuntime(context: FeatureContext, preferences: SharedPreferences) {
        if (!preferences.getBoolean(TextSpeechSettings.KEY_ENABLE, TextSpeechSettings.DEFAULT_ENABLE)) {
            stopRuntime()
            return
        }
        if (runningResources != null) return
        val resources = FeatureResourceScope { logError("语音播报释放失败", it) }
        runningResources = resources
        try {
            val installed = TextSpeechRuntime(context.hostContext())
            runtime = installed
            resources.register { installed.shutdown(); if (runtime === installed) runtime = null }
            installVolumeKeyHook(installed, resources)
            val observing = AtomicBoolean(false)
            val install = {
                if (!resources.isClosed()) {
                    val observe = WeChatApis.message().observe()
                    if (observe == null) {
                        HLog.e("$TAG 消息观察 API 未就绪")
                    } else if (observing.compareAndSet(false, true)) {
                        try {
                            val subscription = observe.subscribe { message -> installed.handleMessage(message) }
                            if (subscription != null) resources.register {
                                KavaReflector.invoke(KavaReflector.findMethod(subscription.javaClass, "unsubscribe"), subscription)
                            }
                        } catch (error: Throwable) {
                            observing.set(false)
                            HLog.e("$TAG 消息订阅失败: ${error.message}", error)
                        }
                    }
                }
            }
            messageInstaller = install
            install()
        } catch (error: Throwable) {
            stopRuntime()
            logError("语音播报启动失败", error)
        }
    }
    private fun stopRuntime() {
        messageInstaller = null
        val resources = runningResources
        runningResources = null
        resources?.close()
    }
    private fun installVolumeKeyHook(installed: TextSpeechRuntime, resources: FeatureResourceScope) {
        val method = KavaReflector.findDeclaredMethod(
            Activity::class.java,
            "dispatchKeyEvent",
            KeyEvent::class.java
        )
        if (method == null) {
            HLog.e("$TAG 未找到 Activity.dispatchKeyEvent")
            return
        }
        runCatching {
            val unhook = HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val event = param.args.getOrNull(0) as? KeyEvent ?: return
                    if (installed.handleVolumeKey(event)) param.result = true
                }
            })
            resources.register { HookRegistry.get().unhook(unhook) }
        }.onFailure {
            HLog.e("$TAG 音量键 Hook 安装失败: ${it.message}", it)
        }
    }
    companion object {
        const val ID = "text_speech"
        private const val TAG = "[Hchat:TextSpeech]"
    }
}
