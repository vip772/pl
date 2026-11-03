package t.mma.hooks.items.membertitle

import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.preferences.HchatStorage
import t.mma.utils.KavaReflector

class MemberTitleFeature : BaseFeature() {
    private var renderer: MemberTitleRenderer? = null

    override fun featureId(): String = ID

    override fun name(): String = "群员头衔"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(MemberTitleSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), MemberTitleSettings.PREFS_NAME)
        var scheduleRuntime: (() -> Unit)? = null
        val owner = PreferenceRuntimeOwner(prefs, setOf(MemberTitleSettings.KEY_ENABLE),
            enabled = { prefs.getBoolean(MemberTitleSettings.KEY_ENABLE, MemberTitleSettings.DEFAULT_ENABLE) },
            create = { resources ->
                val store = MemberTitleStore(context.hostContext())
                val installed = MemberTitleRenderer(context, store, ::logFeatureError)
                renderer = installed
                resources.register {
                    try { installed.destroy() } finally { if (renderer === installed) renderer = null }
                }
                val installation = DexInstallScheduler.installation(ID, name())
                resources.register { try { installation.close() } finally { scheduleRuntime = null } }
                scheduleRuntime = { if (!resources.isClosed()) installation.schedule { installed.install() } }
                scheduleRuntime?.invoke()
                val chatroomChanges = WeChatApis.contact().chatroomChanges()
                if (chatroomChanges != null) {
                    val subscription = chatroomChanges.subscribe { change ->
                        if (resources.isClosed() || !store.isEnabled()) return@subscribe
                        val roomId = change.chatroomId()
                        if (roomId.isNotBlank()) installed.refreshRoom(roomId)
                    }
                    resources.register {
                        KavaReflector.invoke(KavaReflector.findMethod(subscription.javaClass, "unsubscribe"), subscription)
                    }
                }
            }, onError = { logError("群员头衔热启停失败", it) })
        trackResource { owner.close() }
        subscribe(Events.DexReady::class.java) { scheduleRuntime?.invoke() }
        owner.start()
    }
    private fun logFeatureError(message: String, throwable: Throwable?) {
        logError(message, throwable)
    }

    companion object {
        const val ID = "member_title"
    }
}
