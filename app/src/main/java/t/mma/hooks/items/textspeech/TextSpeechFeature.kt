package t.mma.hooks.items.textspeech
import t.mma.utils.HLog

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

    override fun featureId(): String = ID

    override fun name(): String = "文字转语音播报"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(TextSpeechSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val installed = TextSpeechRuntime(context.hostContext())
        runtime = installed
        trackResource { installed.shutdown(); if (runtime === installed) runtime = null }
        installVolumeKeyHook(installed)
        val registerSubscription = subscriptionRegistrar()
        val observing = AtomicBoolean(false)
        subscribe(Events.DexReady::class.java) {
            val observe = WeChatApis.message().observe()
            if (observe == null) {
                HLog.e("$TAG 消息观察 API 未就绪")
                return@subscribe
            }
            if (!observing.compareAndSet(false, true)) return@subscribe
            try {
                registerSubscription(observe.subscribe { message -> installed.handleMessage(message) })
            } catch (error: Throwable) {
                observing.set(false)
                throw error
            }
        }
    }


    private fun installVolumeKeyHook(installed: TextSpeechRuntime) {
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
            trackResource { HookRegistry.get().unhook(unhook) }
        }.onFailure {
            HLog.e("$TAG 音量键 Hook 安装失败: ${it.message}", it)
        }
    }

    companion object {
        const val ID = "text_speech"
        private const val TAG = "[Hchat:TextSpeech]"
    }
}
