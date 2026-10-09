package t.mma.hooks.items.messagemenuextension

import de.robv.android.xposed.XC_MethodHook
import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.utils.HLog
import t.mma.utils.KavaReflector
import java.lang.reflect.Modifier
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Lightweight diagnostic only; never change native views, callbacks or popup placement. */
class MessageMenuExtensionFeature : BaseFeature() {
    private var runtime: MessageMenuExtensionRuntime? = null
    override fun featureId() = MessageMenuExtensionSettings.ID
    override fun name() = "消息菜单扩展"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(MessageMenuExtensionSettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        if (!MessageMenuExtensionSettings.preferences(context.hostContext())
                .getBoolean(MessageMenuExtensionSettings.KEY_ENABLE, false)) {
            HLog.i("[Hchat:消息菜单扩展诊断] stage=installSkipped reason=disabled runtimeCreated=false hooks=0")
            return
        }
        runtime = MessageMenuExtensionRuntime(context)
        scheduleInstall()
        subscribe(Events.DexReady::class.java) { scheduleInstall() }
    }
    private fun scheduleInstall() {
        DexInstallScheduler.schedule(featureId(), name()) { runtime?.install() == true }
    }
    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }
}

private class MessageMenuExtensionRuntime(private val context: FeatureContext) {
    private data class Session(val id: Int, val previous: Session?, val started: Long, var listenerCount: Int = 0)
    private val prefs = MessageMenuExtensionSettings.preferences(context.hostContext())
    private val sequence = AtomicInteger()
    private val currentSession = ThreadLocal<Session>()
    private val pendingLog = AtomicBoolean(false)
    private val logger = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Hchat-MenuDiagnostic").apply { isDaemon = true }
    }
    private val unhooks = mutableListOf<XC_MethodHook.Unhook>()
    @Volatile private var active = true
    private var installed = false
    private var version = "unknown"

    @Synchronized
    fun install(): Boolean {
        if (installed) return true
        if (!active) return false
        // The switch is an installation gate. Disabled means no reflective lookup or WeChat hook.
        if (!prefs.getBoolean(MessageMenuExtensionSettings.KEY_ENABLE, false)) {
            HLog.i("$TAG stage=installSkipped reason=disabled")
            return true
        }
        try {
            val host = context.hostContext()
            val info = host.packageManager.getPackageInfo(host.packageName, 0)
            @Suppress("DEPRECATION")
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            version = "${info.versionName}/$versionCode"
            if (info.versionName != "8.0.78") return missing("unsupportedVersion")
            val loader = context.hostClassLoader()
            val owner = KavaReflector.loadClass("eu5.s0", loader) ?: return missing("eu5.s0")
            val show = KavaReflector.findDeclaredMethod(owner, "p", Integer.TYPE, Integer.TYPE)
                ?: return missing("eu5.s0.p(int,int)")
            if (Modifier.isAbstract(show.modifiers) || Modifier.isStatic(show.modifiers)) return missing("instanceShow")
            // l0 is only the click listener; do not hook every menu item. Record its
            // verified signature together with s0's helper signatures for the next stage.
            val listener = KavaReflector.loadClass("eu5.l0", loader)
            val constructor = listener?.let { KavaReflector.findConstructor(it, owner, Integer.TYPE) }
            val methods = KavaReflector.declaredMethods(owner)
                .filterNot { Modifier.isStatic(it.modifiers) }
                .take(80)
                .joinToString(" || ") { it.toGenericString() }
            val fields = KavaReflector.declaredFields(owner)
                .filterNot { Modifier.isStatic(it.modifiers) }
                .take(80)
                .joinToString(" || ") { "${it.name}:${it.type.name}" }
            HLog.i("$TAG stage=signatures version=$version s0Methods=$methods s0Fields=$fields l0Constructor=${constructor?.toGenericString() ?: "missing"}")
            unhooks += HookRegistry.get().hook(show, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!active || !prefs.getBoolean(MessageMenuExtensionSettings.KEY_ENABLE, false)) return
                    val session = Session(sequence.incrementAndGet(), currentSession.get(), android.os.SystemClock.uptimeMillis())
                    param.setObjectExtra(EXTRA, session)
                    currentSession.set(session)
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    val session = param.getObjectExtra(EXTRA) as? Session ?: return
                    if (session.previous == null) currentSession.remove() else currentSession.set(session.previous)
                    val duration = android.os.SystemClock.uptimeMillis() - session.started
                    // Immutable summary only; do not transfer host objects or perform log I/O on the UI thread.
                    val summary = "$TAG version=$version session=${session.id} listenerCount=${session.listenerCount} nativeCallMs=$duration nativeFailed=${param.hasThrowable()} mode=lightweight nativeUnmodified=true capacityExpansion=NOT_IMPLEMENTED"
                    enqueueSummary(summary)
                }
            })
            installed = true
            HLog.i("$TAG stage=install version=$version mode=lightweight target=${show.toGenericString()}")
            return true
        } catch (error: Throwable) {
            unhooks.forEach { HookRegistry.get().unhook(it) }
            unhooks.clear()
            HLog.e("$TAG stage=install version=$version", error)
            return false
        }
    }

    private fun enqueueSummary(summary: String) {
        if (!active || !pendingLog.compareAndSet(false, true)) return
        try {
            logger.execute {
                try { if (active) HLog.i(summary) } finally { pendingLog.set(false) }
            }
        } catch (error: java.util.concurrent.RejectedExecutionException) {
            pendingLog.set(false)
            // Expected only while destroying; do not introduce synchronous UI logging.
        }
    }
    private fun missing(target: String): Boolean {
        HLog.e("$TAG stage=locate version=$version missing=$target native retained")
        return false
    }
    @Synchronized
    fun destroy() {
        active = false
        unhooks.forEach { HookRegistry.get().unhook(it) }
        unhooks.clear()
        currentSession.remove()
        logger.shutdownNow()
        installed = false
    }
    companion object {
        private const val TAG = "[Hchat:消息菜单扩展诊断]"
        private const val EXTRA = "hchat.menuExtension.session"
    }
}
