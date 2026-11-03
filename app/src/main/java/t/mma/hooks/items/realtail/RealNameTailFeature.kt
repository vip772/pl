package t.mma.hooks.items.realtail

import t.mma.hooks.api.core.WeChatApis
import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.preferences.HchatStorage
import t.mma.hooks.items.groupnicknamecolor.GroupNicknameColorSettings
import t.mma.utils.KavaReflector
import t.mma.hooks.items.groupnicknamecolor.GroupNicknameColorSettingsProvider
import t.mma.hooks.items.groupnicknamecolor.GroupNicknameColorStore

class RealNameTailFeature : BaseFeature() {
    private var store: RealNameTailStore? = null
    private var renderer: RealNameTailRenderer? = null
    private var profilePrefetcher: ContactProfilePrefetcher? = null

    override fun featureId(): String = ID

    override fun name(): String = "实名尾字"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(RealNameTailSettingsProvider())
        registerSettingsProvider(GroupNicknameColorSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), RealNameTailSettings.PREFS_NAME)
        val colorPrefs = HchatStorage.preferences(context.hostContext(), GroupNicknameColorSettings.PREFS_NAME)
        var scheduleRuntime: (() -> Unit)? = null
        val owner = PreferenceRuntimeOwner(prefs, setOf(RealNameTailSettings.KEY_ENABLE),
            enabled = {
                prefs.getBoolean(RealNameTailSettings.KEY_ENABLE, RealNameTailSettings.DEFAULT_ENABLE) ||
                    colorPrefs.getBoolean(GroupNicknameColorSettings.KEY_ENABLE, GroupNicknameColorSettings.DEFAULT_ENABLE)
            }, create = createRuntime@ { resources ->
                val localStore = RealNameTailStore(context.hostContext())
                val nicknameColorStore = GroupNicknameColorStore(context.hostContext())
                store = localStore
                resources.register { if (store === localStore) store = null }
                val query = BeforeTransferNameQuery(context, ::logFeatureError)
                resources.register { query.destroy() }
                var ownedRenderer: RealNameTailRenderer? = null
                val scheduler = RealNameTailScheduler(localStore, query, ::logFeatureError) { wxid ->
                    ownedRenderer?.applyForSender(wxid)
                }
                val prefetcher = ContactProfilePrefetcher(context, ::logFeatureError) { wxid ->
                    ownedRenderer?.applyForSender(wxid)
                }
                resources.register { scheduler.destroy() }
                resources.register { try { prefetcher.destroy() } finally { if (profilePrefetcher === prefetcher) profilePrefetcher = null } }
                profilePrefetcher = prefetcher
                renderer = RealNameTailRenderer(context, localStore, nicknameColorStore, scheduler, ::logFeatureError).also {
                    it.profilePrefetcher = prefetcher
                }
                val installedRenderer = renderer!!
                ownedRenderer = installedRenderer
                resources.register { try { installedRenderer.destroy() } finally { ownedRenderer = null; if (renderer === installedRenderer) renderer = null; if (store === localStore) store = null } }
                val ticket = DexInstallScheduler.installation(ID, name())
                resources.register { try { ticket.close() } finally { scheduleRuntime = null } }
                scheduleRuntime = { if (!resources.isClosed()) ticket.schedule { installedRenderer.install() } }
                scheduleRuntime?.invoke()
                val observer = WeChatApis.message().observe() ?: return@createRuntime
                val subscription = observer.subscribe { message ->
                    if (resources.isClosed()) return@subscribe
                    if (!localStore.isEnabled() || !localStore.messageQueryEnabled()) return@subscribe
                    if (!message.isGroupChat() || message.isSend()) return@subscribe
                    val roomId = message.talker
                    val sender = message.sender
                    if (sender.isBlank() || isSelf(sender)) return@subscribe
                    scheduler.onMessage(roomId, sender)
                    if (localStore.hasTail(sender)) installedRenderer.applyForSender(sender)
                }
                resources.register {
                    KavaReflector.invoke(KavaReflector.findMethod(subscription.javaClass, "unsubscribe"), subscription)
                }
            }, onError = { logError("实名尾字热启停失败", it) })
        trackResource { owner.close() }
        val colorListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == GroupNicknameColorSettings.KEY_ENABLE) owner.refresh()
        }
        trackResource { colorPrefs.unregisterOnSharedPreferenceChangeListener(colorListener) }
        colorPrefs.registerOnSharedPreferenceChangeListener(colorListener)
        subscribe(Events.DexReady::class.java) { scheduleRuntime?.invoke() }
        owner.start()
    }
    private fun logFeatureError(message: String, throwable: Throwable?) {
        logError(message, throwable)
    }

private fun isSelf(wxid: String): Boolean {
        val self = WeChatApis.account()?.selfWxId().orEmpty()
        return self.isNotEmpty() && self == wxid
    }

    companion object {
        const val ID = "real_name_tail"
    }
}
