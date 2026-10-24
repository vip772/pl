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
    private var sourceWindowAt = -1L
    private var sourceWindowSamples = 0
    private val snapshots = HashMap<String, Long>()
    private var nativePhase: String? = null
    private var nativeStarted = 0L
    private var lastRequestAt = 0L
    private var lastRequestPhase = "none"
    private val recentStages = ArrayDeque<String>(16)
    private var droppedStages = 0
    // Main-thread only, bounded to one callback interval; values retain no Views.
    fun stage(name: String, started: Long, ended: Long) {
        if (!ChatScrollDiagnostic.isActive() || Looper.myLooper() != Looper.getMainLooper()) return
        if (name != "scroll.nativeLayoutDuration" && name != "underlay.apply" &&
            name != "underlay.measureLayout" && name != "background.footer" && name != "background.repair") return
        if (recentStages.size == 16) { recentStages.removeFirst(); droppedStages++ }
        recentStages.addLast("$name[startMs=${started/1_000_000},endMs=${ended/1_000_000},elapsedUs=${(ended-started).coerceAtLeast(0)/1000}]")
    }
    private val phase = ThreadLocal<String>()
    private var callback: Choreographer.FrameCallback? = null
    fun start() = onMain {
        cleanup()
        if (!ChatScrollDiagnostic.isActive()) return@onMain
        budget = 60; sourceWindowAt = -1L; sourceWindowSamples = 0
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
                        lastRequestAt = now; lastRequestPhase = tag
                        val window = now / 5_000L
                        if (window != sourceWindowAt) { sourceWindowAt = window; sourceWindowSamples = 0 }
                        if (budget > 0 && sourceWindowSamples < 1) {
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
                            val now = android.os.SystemClock.elapsedRealtime()
                            ChatScrollDiagnostic.detail("frame.latestLong", "atMs=$now intervalUs=${delta/1000}")
                            ChatScrollDiagnostic.detail("frame.longContext", "observedAtMs=$now intervalUs=${delta/1000} requestAtMs=$lastRequestAt requestPhase=$lastRequestPhase dropped=$droppedStages recentCompleted=" + recentStages.joinToString(" "))
                        }
                        if (delta >= 50_000_000L) ChatScrollDiagnostic.hit("frame.ge50ms")
                    }
                    lastFrame = time
                } else lastFrame = 0L
                recentStages.clear(); droppedStages = 0
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        callback = frame; Choreographer.getInstance().postFrameCallback(frame)
    }
    fun watch(scroll: View, content: View? = null, list: View? = null, footer: View? = null) {
        if (!ChatScrollDiagnostic.isActive() || Looper.myLooper() != Looper.getMainLooper()) return
        if (anchor.get() !== scroll) {
            watched.clear(); lastFrame = 0L; recentStages.clear(); droppedStages = 0
            lastRequestAt = 0L; lastRequestPhase = "none"; anchor = WeakReference(scroll)
        }
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
    fun nativeBegin() {
        if (ChatScrollDiagnostic.isActive()) {
            nativePhase = enter("nativeLayout")
            nativeStarted = ChatScrollDiagnostic.begin()
        }
    }
    fun nativeEnd() {
        ChatScrollDiagnostic.end("scroll.nativeLayoutDuration", nativeStarted)
        nativeStarted = 0L
        leave(nativePhase); nativePhase = null
    }
    fun enter(value: String): String? { val old = phase.get(); phase.set(value); return old }
    fun leave(old: String?) { if (old == null) phase.remove() else phase.set(old) }
    fun stop() = onMain { cleanup() }
    private fun visible() = anchor.get()?.let { it.isAttachedToWindow && it.isShown && it.windowVisibility == View.VISIBLE } == true
    private fun cleanup() {
        generation++
        callback?.let { Choreographer.getInstance().removeFrameCallback(it) }; callback = null
        try { unhook?.unhook() } catch (_: Throwable) { } finally { unhook = null; watched.clear(); anchor.clear(); lastFrame = 0L; phase.remove(); snapshots.clear(); nativePhase = null; nativeStarted = 0L; recentStages.clear(); droppedStages = 0; lastRequestAt = 0L; lastRequestPhase = "none" }
    }
    private fun onMain(block: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() } }
}
