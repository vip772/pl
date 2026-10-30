package t.mma.hooks.items.chattoolbar

import android.content.SharedPreferences
import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.FeatureResourceScope
import t.mma.utils.KavaReflector

class ChatToolbarFeature : BaseFeature() {
    private var runtime: ChatToolbarRuntime? = null
    private var runningResources: FeatureResourceScope? = null
    private var installation: DexInstallScheduler.Installation? = null
    override fun featureId(): String = ID
    override fun name(): String = "聊天工具栏"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(ChatToolbarSettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        val preferences = ChatToolbarSettings.preferences(context.hostContext())
        var active = true
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == ChatToolbarSettings.KEY_ENABLE || key == null) {
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
            synchronized(this) {
                val current = runtime
                if (active && current != null) installation?.schedule { current.installDexHooks() }
            }
        }
        updateRuntime(context, preferences)
    }
    @Synchronized
    private fun updateRuntime(context: FeatureContext, preferences: SharedPreferences) {
        if (!preferences.getBoolean(ChatToolbarSettings.KEY_ENABLE, ChatToolbarSettings.DEFAULT_ENABLE)) {
            stopRuntime()
            return
        }
        if (runningResources != null) return
        val resources = FeatureResourceScope { logError("工具栏运行时释放失败", it) }
        runningResources = resources
        try {
            val current = ChatToolbarRuntime(context, ::logRuntimeError)
            runtime = current
            resources.register { current.destroy(); if (runtime === current) runtime = null }
            val ownedInstall = DexInstallScheduler.installation(ID, name())
            installation = ownedInstall
            resources.register { ownedInstall.close(); if (installation === ownedInstall) installation = null }
            current.installBaseHooks()
            val subscription = WeChatApis.lifecycle()?.subscribe { event ->
                if (event.isDestroy) current.onActivityDestroyed(event.activity)
            }
            if (subscription != null) resources.register {
                KavaReflector.invoke(KavaReflector.findMethod(subscription.javaClass, "unsubscribe"), subscription)
            }
            ownedInstall.schedule { current.installDexHooks() }
        } catch (error: Throwable) {
            stopRuntime()
            logError("工具栏运行时启动失败", error)
        }
    }
    private fun stopRuntime() {
        val resources = runningResources
        runningResources = null
        resources?.close()
    }
    private fun logRuntimeError(message: String, throwable: Throwable?) { logError(message, throwable) }
    companion object { const val ID = "chat_toolbar" }
}
