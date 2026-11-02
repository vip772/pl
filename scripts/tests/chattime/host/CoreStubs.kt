package t.mma.hooks.core

import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Member
import t.mma.utils.HLog

/**
 * Synthetic feature base mirroring the production lifecycle contract: a feature-private
 * resource scope closed on destroy, settings registration and unified logging.
 */
abstract class BaseFeature {
    val tracked = mutableListOf<() -> Unit>()
    val logs = mutableListOf<String>()
    private var resources = FeatureResourceScope { logs.add("release:" + it.message) }

    open fun featureId(): String = "stub"
    open fun name(): String = featureId()
    open fun onFeatureInit(context: FeatureContext) {}
    open fun onFeatureInstall(context: FeatureContext) {}
    open fun onFeatureDestroy(context: FeatureContext) {}

    fun install(context: FeatureContext) {
        onFeatureInstall(context)
    }

    fun trackResource(release: () -> Unit) {
        tracked.add(release)
        resources.register(release)
    }

    fun registerSettingsProvider(provider: Any) {}

    fun logError(message: String, throwable: Throwable?) {
        logs.add(message)
        HLog.e(message, throwable)
    }

    fun logInfo(message: String) {
        logs.add(message)
        HLog.i(message)
    }

    /** Mirrors BaseFeature.onDestroy: close the feature scope, keep nothing alive. */
    fun releaseAll(context: FeatureContext) {
        try {
            onFeatureDestroy(context)
        } finally {
            val scope = resources
            resources = FeatureResourceScope { logs.add("release:" + it.message) }
            scope.close()
            tracked.clear()
        }
    }
}

class FeatureContext(
    private val host: android.content.Context,
    private val loader: ClassLoader
) {
    private val bus = t.mma.event.EventBus()
    private val manager = t.mma.dexkit.DexKitBridgeManager()

    fun hostContext(): android.content.Context = host

    fun hostClassLoader(): ClassLoader = loader

    fun eventBus(): t.mma.event.EventBus = bus

    fun dexKitManager(): t.mma.dexkit.DexKitBridgeManager = manager
}

/** Synthetic scheduler: tickets and queued installers are explicit and drainable. */
object DexInstallScheduler {
    enum class Stage { EARLY, BRIDGE, WARMUP }

    val installations = mutableListOf<Installation>()
    val pending = mutableListOf<Pair<Installation, () -> Boolean>>()

    class Installation {
        var closed = false

        fun schedule(installer: () -> Boolean) {
            if (closed) return
            pending.add(this to installer)
        }

        /** Mirrors the production scheduler: cancelling a ticket drops its queued installer. */
        fun close() {
            closed = true
            pending.removeAll { it.first === this }
        }
    }

    fun installation(
        key: String,
        label: String = key,
        stage: Stage = Stage.BRIDGE,
        priority: Int = 0
    ): Installation = Installation().also { installations.add(it) }

    /** Mirrors the production scheduler: an installer that reports failure is retried. */
    fun drain(): List<Boolean> {
        val snapshot = pending.toList()
        pending.clear()
        return snapshot.map { (ticket, installer) ->
            if (ticket.closed) {
                false
            } else {
                val ok = installer()
                if (!ok && !ticket.closed) pending.add(ticket to installer)
                ok
            }
        }
    }

    fun reset() {
        installations.clear()
        pending.clear()
    }
}

/** Synthetic hook registry: handles stay observable so ownership transfer is assertable. */
class HookRegistry private constructor() {
    val hooks = mutableListOf<XC_MethodHook.Unhook>()
    var failHook = false
    var failUnhook = false

    fun hook(member: Member, callback: XC_MethodHook): XC_MethodHook.Unhook {
        if (failHook) error("synthetic hook failure")
        val unhook = XC_MethodHook.Unhook(callback, member) { released ->
            hooks.removeAll { it === released }
        }
        hooks.add(unhook)
        return unhook
    }

    fun size(): Int = hooks.size

    fun unhook(unhook: XC_MethodHook.Unhook?) {
        if (unhook == null) return
        try {
            if (failUnhook) error("synthetic unhook failure")
            unhook.unhook()
        } finally {
            // Release local ownership even when the synthetic native unhook throws.
            hooks.removeAll { it === unhook }
        }
    }

    fun reset() {
        hooks.clear()
        failHook = false
        failUnhook = false
    }

    companion object {
        private val INSTANCE = HookRegistry()

        @JvmStatic
        fun get(): HookRegistry = INSTANCE
    }
}
