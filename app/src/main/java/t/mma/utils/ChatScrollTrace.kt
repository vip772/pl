package t.mma.utils
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.ref.WeakReference

/** Temporary request observer and frame intervals; never changes a request or callback result. */
internal object ChatScrollTrace {
    private val main = Handler(Looper.getMainLooper())
    private val watched = ArrayList<WeakReference<View>>(32)
    private var anchor = WeakReference<View>(null)
    private var unhook: XC_MethodHook.Unhook? = null
    private var generation = 0
    private var lastFrame = 0L
    private var budget = 0
    private var requests = 0
    private var sourceWindowAt = -1L
    private var sourceWindowSamples = 0
    private val snapshots = HashMap<String, Long>()
    private var nativePhase: String? = null
    private val phase = ThreadLocal<String>()
    private var callback: Choreographer.FrameCallback? = null
    fun start() = onMain {
        cleanup()
        if (!ChatScrollDiagnostic.isActive()) return@onMain
        budget = 60; requests = 0; sourceWindowAt = -1L; sourceWindowSamples = 0
        val token = ++generation
        try {
            val method = KavaReflector.findDeclaredMethod(View::class.java, "requestLayout") ?: error("missing requestLayout")
            unhook = XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!ChatScrollDiagnostic.isActive() || Looper.myLooper() != Looper.getMainLooper()) return
                    val view = param.thisObject as? View ?: return
                    if (!visible() || watched.none { it.get() === view }) return
                    try {
                        val tag = phase.get() ?: "outside"
                        ChatScrollDiagnostic.hit("requestLayout.$tag")
                        val now = android.os.SystemClock.elapsedRealtime()
                        val window = now / 5_000L
                        if (window != sourceWindowAt) { sourceWindowAt = window; sourceWindowSamples = 0 }
                        if (budget > 0 && sourceWindowSamples < 1 && requests++ % 128 == 0) {
                            budget--; sourceWindowSamples++
                            val path = Throwable().stackTrace.asSequence().take(96)
                                .filterNot { frame ->
                                    frame.className.startsWith(ChatScrollTrace::class.java.name) ||
                                        frame.className.startsWith("de.robv.android.xposed.") ||
                                        (frame.methodName == "requestLayout" &&
                                            (frame.className.startsWith("android.") || frame.className.startsWith("androidx.")))
                                }
                                .take(24).joinToString(" <- ") { "${it.className}#${it.methodName}:${it.lineNumber}" }
                            ChatScrollDiagnostic.detail("request.source", "phase=$tag target=${view.javaClass.name} atMs=$now $path")
                        }
                    } catch (_: Throwable) { }
                }
            })
            ChatScrollDiagnostic.detail("requestHook", "installed")
        } catch (error: Throwable) {
            ChatScrollDiagnostic.detail("requestHook", "failed:${error.javaClass.simpleName}")
        }
        val frame = object : Choreographer.FrameCallback {
            override fun doFrame(time: Long) {
                if (token != generation || !ChatScrollDiagnostic.isActive()) {
                    if (token == generation) cleanup()
                    return
                }
                if (visible()) {
                    if (lastFrame > 0 && time > lastFrame) {
                        val delta = time - lastFrame
                        ChatScrollDiagnostic.observe("frame.interval", delta)
                        if (delta >= 20_000_000L) ChatScrollDiagnostic.hit("frame.ge20ms")
                        if (delta >= 34_000_000L) {
                            ChatScrollDiagnostic.hit("frame.ge34ms")
                            ChatScrollDiagnostic.detail("frame.latestLong", "atMs=${android.os.SystemClock.elapsedRealtime()} intervalUs=${delta/1000}")
                        }
                        if (delta >= 50_000_000L) ChatScrollDiagnostic.hit("frame.ge50ms")
                    }
                    lastFrame = time
                } else lastFrame = 0L
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        callback = frame; Choreographer.getInstance().postFrameCallback(frame)
    }
    fun watch(scroll: View, content: View? = null, list: View? = null, footer: View? = null) {
        if (!ChatScrollDiagnostic.isActive() || Looper.myLooper() != Looper.getMainLooper()) return
        if (anchor.get() !== scroll) { watched.clear(); lastFrame = 0L; anchor = WeakReference(scroll) }
        if (content == null && list == null && footer == null && watched.isNotEmpty()) return
        for (target in arrayOf(scroll, content, list, footer)) {
            var node = target
            repeat(12) {
                val view = node ?: return@repeat
                if (watched.size < 32 && watched.none { it.get() === view }) watched.add(WeakReference(view))
                node = if (view === scroll) null else view.parent as? View
            }
        }
    }
    fun snapshot(name: String, view: View) {
        if (!ChatScrollDiagnostic.isActive()) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - (snapshots[name] ?: -1000L) < 250L) return
        snapshots[name] = now
        ChatScrollDiagnostic.detail(name, "atMs=${android.os.SystemClock.elapsedRealtime()} measured=${view.measuredWidth}x${view.measuredHeight} bounds=${view.left},${view.top},${view.width},${view.height} requested=${view.isLayoutRequested}")
    }
    fun nativeBegin() { if (ChatScrollDiagnostic.isActive()) nativePhase = enter("nativeLayout") }
    fun nativeEnd() { leave(nativePhase); nativePhase = null }
    fun enter(value: String): String? { val old = phase.get(); phase.set(value); return old }
    fun leave(old: String?) { if (old == null) phase.remove() else phase.set(old) }
    fun stop() = onMain { cleanup() }
    private fun visible() = anchor.get()?.let { it.isAttachedToWindow && it.isShown && it.windowVisibility == View.VISIBLE } == true
    private fun cleanup() {
        generation++
        callback?.let { Choreographer.getInstance().removeFrameCallback(it) }; callback = null
        try { unhook?.unhook() } catch (_: Throwable) { } finally { unhook = null; watched.clear(); anchor.clear(); lastFrame = 0L; phase.remove(); snapshots.clear(); nativePhase = null }
    }
    private fun onMain(block: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() } }
}
