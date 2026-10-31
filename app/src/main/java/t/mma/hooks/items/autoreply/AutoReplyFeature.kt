package t.mma.hooks.items.autoreply
import android.content.SharedPreferences
import t.mma.utils.HLog
import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.FeatureResourceScope
import t.mma.hooks.items.script.ScriptNewFriendHook
import t.mma.preferences.HchatStorage
class AutoReplyFeature : BaseFeature() {
    private var running: FeatureResourceScope? = null
    private var observeMessages: (() -> Unit)? = null
    override fun featureId(): String = ID
    override fun name(): String = "自动回复"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(AutoReplySettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), AutoReplySettings.PREFS_NAME)
        var active = true
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == AutoReplySettings.KEY_ENABLE || key == AutoReplySettings.KEY_AUTO_ACCEPT_ENABLE) {
                synchronized(this) { if (active) { stopRuntime(); updateRuntime(context, prefs) } }
            }
        }
        trackResource {
            synchronized(this) {
                active = false
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
                stopRuntime()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        subscribe(Events.DexReady::class.java) { synchronized(this) { if (active) observeMessages?.invoke() } }
        updateRuntime(context, prefs)
    }
    @Synchronized
    private fun updateRuntime(context: FeatureContext, prefs: SharedPreferences) {
        val enabled = prefs.getBoolean(AutoReplySettings.KEY_ENABLE, AutoReplySettings.DEFAULT_ENABLE) ||
            prefs.getBoolean(AutoReplySettings.KEY_AUTO_ACCEPT_ENABLE, false)
        if (!enabled) { stopRuntime(); return }
        if (running != null) return
        val resources = FeatureResourceScope { logError("自动回复资源释放失败", it) }
        running = resources
        try {
            AutoReplyRuntime.start()
            resources.register { AutoReplyRuntime.stop() }
            var observing = false
            observeMessages = {
                if (!resources.isClosed() && !observing) {
                    val observe = WeChatApis.message().observe()
                    if (observe != null) {
                        val subscription = observe.subscribe { message ->
                            if (!resources.isClosed()) AutoReplyRuntime.handleMessage(context.hostContext(), message)
                        }
                        resources.register { subscription?.unsubscribe() }
                        observing = true
                    }
                }
            }
            ScriptNewFriendHook.install(context)
            val subscription = ScriptNewFriendHook.subscribe { event ->
                if (!resources.isClosed()) AutoReplyRuntime.handleNewFriend(context.hostContext(), event.wxid, event.ticket, event.scene)
            }
            resources.register { subscription.unsubscribe() }
            observeMessages?.invoke()
        } catch (error: Throwable) { stopRuntime(); HLog.e("$TAG 启动失败", error) }
    }
    private fun stopRuntime() {
        observeMessages = null
        val previous = running
        running = null
        previous?.close()
    }
    companion object {
        const val ID = "auto_reply"
        private const val TAG = "[Hchat:AutoReply]"
    }
}