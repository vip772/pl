package t.mma.hooks.items.musicorder

import android.content.SharedPreferences
import t.mma.preferences.HchatStorage
import t.mma.hooks.core.FeatureResourceScope
import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.utils.KavaReflector
import t.mma.hooks.items.script.ScriptSendButtonHook

class QQMusicOrderFeature : BaseFeature() {
    private var runtime: QQMusicOrderRuntime? = null
    private var runningResources: FeatureResourceScope? = null

    override fun featureId(): String = ID

    override fun name(): String = "QQ点歌"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(QQMusicOrderSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val preferences = HchatStorage.preferences(context.hostContext(), QQMusicOrderSettings.PREFS_NAME)
        var active = true
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == QQMusicOrderSettings.KEY_ENABLE || key == null) {
                synchronized(this) {
                    if (active) runCatching { updateRuntime(context, preferences) }
                        .onFailure { logError("QQ点歌开关更新失败", it) }
                }
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
            synchronized(this) { if (active) scheduleSendButtonHook(context) }
        }
        scheduleSendButtonHook(context)
        updateRuntime(context, preferences, propagateFailure = true)
    }

    @Synchronized
    private fun updateRuntime(
        context: FeatureContext,
        preferences: SharedPreferences,
        propagateFailure: Boolean = false
    ) {
        if (!preferences.getBoolean(QQMusicOrderSettings.KEY_ENABLE, QQMusicOrderSettings.DEFAULT_ENABLE)) {
            stopRuntime()
            return
        }
        if (runningResources != null) return
        val resources = FeatureResourceScope { logError("QQ点歌资源释放失败", it) }
        runningResources = resources
        try {
            val installed = QQMusicOrderRuntime(context.hostContext(), ::logError)
            runtime = installed
            resources.register { installed.destroy(); if (runtime === installed) runtime = null }
            installed.install { subscription ->
                resources.register {
                    KavaReflector.invoke(KavaReflector.findMethod(subscription.javaClass, "unsubscribe"), subscription)
                }
            }
        } catch (error: Throwable) {
            stopRuntime()
            if (propagateFailure) throw error
            logError("QQ点歌启动失败", error)
        }
    }

    private fun stopRuntime() {
        val resources = runningResources
        runningResources = null
        resources?.close()
    }

    private fun scheduleSendButtonHook(context: FeatureContext) {
        DexInstallScheduler.schedule("shared:send_button", "聊天发送按钮", stage = DexInstallScheduler.Stage.WARMUP) {
            ScriptSendButtonHook.install(context)
        }
    }

    companion object {
        const val ID = "qq_music_order"
    }
}
