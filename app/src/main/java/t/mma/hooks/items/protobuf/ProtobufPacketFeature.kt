package t.mma.hooks.items.protobuf

import de.robv.android.xposed.XposedBridge
import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.preferences.HchatStorage

class ProtobufPacketFeature : BaseFeature() {
    override fun featureId(): String = ID

    override fun name(): String = "Protobuf"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(ProtobufPacketSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), ProtobufPacketSettings.PREFS_NAME)
        val hook = ProtobufPacketHook(context.hostClassLoader(), context.dexFinder(), prefs,
            ProtobufPacketFileLogger(context.hostContext())) { message -> XposedBridge.log("[Hchat:Protobuf] $message") }
        val lock = Any()
        var closed = false
        val ticket = DexInstallScheduler.installation(ID, name(), DexInstallScheduler.Stage.WARMUP)
        trackResource {
            synchronized(lock) {
                closed = true
                ticket.close()
                ProtobufPacketRuntime.detach(hook)
                hook.close()
            }
        }
        fun schedule() {
            ticket.schedule {
                synchronized(lock) {
                    if (closed) true else try {
                        val ok = hook.install()
                        if (ok) ProtobufPacketRuntime.install(hook)
                        ok
                    } catch (error: Throwable) { logError("Protobuf 安装失败", error); false }
                }
            }
        }
        schedule()
        subscribe(Events.DexReady::class.java) { schedule() }
    }
    companion object {
        const val ID = "protobuf_packet"
    }
}
