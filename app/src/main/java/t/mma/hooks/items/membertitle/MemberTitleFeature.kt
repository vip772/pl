package t.mma.hooks.items.membertitle

import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext

class MemberTitleFeature : BaseFeature() {
    private var renderer: MemberTitleRenderer? = null

    override fun featureId(): String = ID

    override fun name(): String = "群员头衔"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(MemberTitleSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val store = MemberTitleStore(context.hostContext())
        val installed = MemberTitleRenderer(context, store, ::logFeatureError)
        renderer = installed
        trackResource { installed.destroy(); if (renderer === installed) renderer = null }
        val installation = DexInstallScheduler.installation(ID, name())
        trackResource { installation.close() }
        val install = { installation.schedule { installed.install() } }
        install()
        subscribe(Events.DexReady::class.java) { install() }
        val chatroomChanges = WeChatApis.contact().chatroomChanges() ?: return
        trackSubscription(chatroomChanges.subscribe { change ->
            if (!store.isEnabled()) return@subscribe
            val roomId = change.chatroomId()
            if (roomId.isBlank()) return@subscribe
            installed.refreshRoom(roomId)
        })
    }

    private fun logFeatureError(message: String, throwable: Throwable?) {
        logError(message, throwable)
    }

    companion object {
        const val ID = "member_title"
    }
}
