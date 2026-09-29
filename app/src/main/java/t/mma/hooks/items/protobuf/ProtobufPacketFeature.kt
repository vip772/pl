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
        scheduleInstall(context)
        subscribe(Events.DexReady::class.java) {
            scheduleInstall(context)
        }
    }

    private fun scheduleInstall(context: FeatureContext) {
        DexInstallScheduler.schedule(ID, name(), stage = DexInstallScheduler.Stage.WARMUP) {
            try {
                val prefs = HchatStorage.preferences(context.hostContext(), ProtobufPacketSettings.PREFS_NAME)
                val hook = ProtobufPacketHook(
                    context.hostClassLoader(),
                    context.dexFinder(),
                    prefs,
                    ProtobufPacketFileLogger(context.hostContext())
                ) { message -> XposedBridge.log("[Hchat:Protobuf] $message") }
                val ok = hook.install()
                if (ok) ProtobufPacketRuntime.install(hook)
                ok
            } catch (e: Throwable) {
                logError("Protobuf 安装失败", e)
                false
            }
        }
    }

    companion object {
        const val ID = "protobuf_packet"
    }
}
