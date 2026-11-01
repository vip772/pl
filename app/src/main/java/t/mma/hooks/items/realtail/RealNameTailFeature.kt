package t.mma.hooks.items.realtail

import t.mma.hooks.api.core.WeChatApis
import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
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
        val localStore = RealNameTailStore(context.hostContext())
        val nicknameColorStore = GroupNicknameColorStore(context.hostContext())
        store = localStore
        val query = BeforeTransferNameQuery(context, ::logFeatureError)
        trackResource { query.destroy() }
        var ownedRenderer: RealNameTailRenderer? = null
        val scheduler = RealNameTailScheduler(localStore, query, ::logFeatureError) { wxid ->
            ownedRenderer?.applyForSender(wxid)
        }
        val prefetcher = ContactProfilePrefetcher(context, ::logFeatureError) { wxid ->
            ownedRenderer?.applyForSender(wxid)
        }
        trackResource { scheduler.destroy() }
        trackResource { prefetcher.destroy(); if (profilePrefetcher === prefetcher) profilePrefetcher = null }
        profilePrefetcher = prefetcher
        renderer = RealNameTailRenderer(context, localStore, nicknameColorStore, scheduler, ::logFeatureError).also {
            it.profilePrefetcher = prefetcher
        }
        val installedRenderer = renderer!!
        ownedRenderer = installedRenderer
        trackResource { try { installedRenderer.destroy() } finally { ownedRenderer = null; if (renderer === installedRenderer) renderer = null; if (store === localStore) store = null } }
        val ticket = DexInstallScheduler.installation(ID, name())
        trackResource { ticket.close() }
        fun schedule() { ticket.schedule { installedRenderer.install() } }
        schedule()
        subscribe(Events.DexReady::class.java) { schedule() }
        val observer = WeChatApis.message().observe() ?: return
        trackSubscription(observer.subscribe { message ->
            if (!localStore.isEnabled() || !localStore.messageQueryEnabled()) return@subscribe
            if (!message.isGroupChat() || message.isSend()) return@subscribe
            val roomId = message.talker
            val sender = message.sender
            if (sender.isBlank() || isSelf(sender)) return@subscribe
            scheduler.onMessage(roomId, sender)
            if (localStore.hasTail(sender)) installedRenderer.applyForSender(sender)
        })
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
