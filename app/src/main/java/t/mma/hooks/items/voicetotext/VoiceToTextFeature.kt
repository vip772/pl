package t.mma.hooks.items.voicetotext

import android.os.Handler
import android.os.Looper
import android.view.View
import android.net.ConnectivityManager
import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider
import t.mma.utils.KavaReflector
import java.lang.ref.WeakReference

class VoiceToTextFeature : BaseFeature() {
    private var runtime: VoiceToTextRuntime? = null
    override fun featureId() = ID
    override fun name() = "语音转文字"
    // Install the cheap setting observer even when the user toggle is off.
    override fun isEnabled(context: FeatureContext) = true
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(object : SimpleFeatureSettingsProvider(
            ID, "语音转文字", "聊天中收到的语音自动显示文字", FeatureSettingsProvider.CATEGORY_PRACTICAL, true
        ) {})
    }
    override fun onFeatureInstall(context: FeatureContext) {
        if (context.loadPackageParam().processName != context.loadPackageParam().packageName) return
        runtime = VoiceToTextRuntime(context)
        runtime?.start()
        subscribe(Events.DexReady::class.java) { runtime?.schedule() }
    }
    override fun onFeatureDestroy(context: FeatureContext) { runtime?.destroy(); runtime = null }
    companion object {
        const val ID = "voice_to_text"
        const val PREFS = "Hchat_voice_to_text_config"
        const val ENABLE = "enable"
    }
}

/** 8.0.78 verified incoming voice row -> native TransformComponent queue. */
private class VoiceToTextRuntime(private val context: FeatureContext) {
    private val prefs = HchatStorage.preferences(context.hostContext(), VoiceToTextFeature.PREFS)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var enabled = prefs.getBoolean(VoiceToTextFeature.ENABLE, false)
    @Volatile private var closed = false
    private var unhook: XC_MethodHook.Unhook? = null
    private val gate = VoiceToTextGate()
    private data class Candidate(val root: WeakReference<View>, val chat: Any, val message: Any, val id: Long)
    private val queue = java.util.ArrayDeque<Candidate>()
    private val boundIds = java.util.WeakHashMap<View, Long>()
    private var draining = false
    private var bridge: Bridge? = null
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == VoiceToTextFeature.ENABLE || key == null) {
            enabled = prefs.getBoolean(VoiceToTextFeature.ENABLE, false)
            if (enabled) schedule() else main.post { clear() }
        }
    }
    fun start() { prefs.registerOnSharedPreferenceChangeListener(listener); if (enabled) schedule() }
    fun schedule() {
        if (closed || !enabled) return
        DexInstallScheduler.schedule(VoiceToTextFeature.ID, "语音转文字") { install() }
    }
    private class Bridge(private val loader: ClassLoader) {
        private fun cls(name: String) = requireNotNull(KavaReflector.loadClass(name, loader)) { name }
        val row = cls("com.tencent.mm.ui.chatting.viewitems.bq")
        val holder = cls("com.tencent.mm.ui.chatting.viewitems.h0")
        val chat = cls("gk5.d")
        val wrapper = cls("am5.d")
        val native = cls("com.tencent.mm.storage.e9")
        val base = cls("im.c8")
        val component = cls("com.tencent.mm.ui.chatting.component.vo")
        val binding = requireNotNull(KavaReflector.findMethod(row, "n", holder, chat, wrapper, String::class.java))
        val wrapperData = KavaReflector.findField(wrapper, "d")
        val wrappedMessage = KavaReflector.findField(cls("hn5.a"), "b")
        val root = KavaReflector.findField(holder, "convertView")
        val msgId = KavaReflector.findMethod(base, "getMsgId")
        val send = KavaReflector.findMethod(base, "z0")
        val converted = KavaReflector.findMethod(native, "Z1")
        val downloaded = KavaReflector.findMethod(cls("com.tencent.mm.ui.chatting.viewitems.nq"), "o", native)
        val manager = KavaReflector.findField(chat, "c")
        val getComponent = KavaReflector.findMethod(cls("com.tencent.mm.ui.chatting.manager.c"), "a", Class::class.java)
        val api = cls("ak5.l2")
        val queued = KavaReflector.findMethod(component, "y0", java.lang.Long.TYPE)
        val convert = KavaReflector.findMethod(component, "b", native, java.lang.Boolean.TYPE,
            Integer.TYPE, Integer.TYPE, Integer.TYPE)
        init {
            require(listOf(wrapperData, wrappedMessage, root, msgId, send, converted,
                downloaded, manager, getComponent, queued, convert).all { it != null }) {
                "8.0.78 voice bridge member missing"
            }
        }
    }
    @Synchronized private fun install(): Boolean {
        if (closed || !enabled || unhook != null) return true
        // Descriptors are tied to the supplied 8.0.78 evidence; do not guess other versions.
        if (context.hostContext().packageManager.getPackageInfo("com.tencent.mm", 0).versionName != "8.0.78") return false
        return try {
            val b = bridge ?: Bridge(context.hostClassLoader()).also { bridge = it }
            unhook = HookRegistry.get().hook(b.binding, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!enabled || closed || param.hasThrowable()) return
                    try {
                        val root = KavaReflector.readField(b.root, param.args[0]) as? View ?: return
                        val data = KavaReflector.readField(b.wrapperData, param.args[2]) ?: return
                        val msg = KavaReflector.readField(b.wrappedMessage, data) ?: return
                        val id = (KavaReflector.invoke(b.msgId, msg) as? Number)?.toLong() ?: return
                        boundIds[root] = id
                        val chat = param.args[1] ?: return
                        if ((KavaReflector.invoke(b.send, msg) as? Number)?.toInt() != 0 ||
                            KavaReflector.invoke(b.converted, msg) == true || !gate.offer(id)) return
                        queue.addLast(Candidate(WeakReference(root), chat, msg, id))
                        if (!draining) { draining = true; main.postDelayed(drain, 350) }
                    } catch (error: Throwable) { report(error) }
                }
            })
            unhook != null
        } catch (error: Throwable) { report(error); false }
    }
    private val drain = object : Runnable {
        override fun run() {
            val item = queue.pollFirst()
            if (closed || !enabled || item == null) { clear(); return }
            try {
                val b = bridge ?: return
                val root = item.root.get()
                val online = (context.hostContext().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                    ?.activeNetworkInfo?.isConnected == true
                if (root == null || !root.isAttachedToWindow || !root.isShown || !root.hasWindowFocus() ||
                    boundIds[root] != item.id || !online) { gate.release(item.id); return }
                if (KavaReflector.invoke(b.converted, item.message) == true) return
                if (KavaReflector.invoke(b.downloaded, null, item.message) != true) { gate.release(item.id); return }
                val manager = KavaReflector.readField(b.manager, item.chat) ?: run { gate.release(item.id); return }
                val component = KavaReflector.invoke(b.getComponent, manager, b.api) ?: run { gate.release(item.id); return }
                if (KavaReflector.invoke(b.queued, component, item.id) == true) return
                // Same native request arguments as the long-press conversion path.
                KavaReflector.invokeOrThrow(b.convert, component, item.message, true, 0, 0, 0)
            } catch (error: Throwable) { report(error) }
            finally {
                gate.finish(item.id)
                if (!closed && enabled && queue.isNotEmpty()) main.postDelayed(this, 500)
                else draining = false
            }
        }
    }
    private var reported = false
    private fun report(error: Throwable) {
        if (!reported) { reported = true; t.mma.utils.HLog.e("[Hchat:语音转文字] 自动转换失败", error) }
    }
    private fun clear() { main.removeCallbacks(drain); queue.clear(); gate.clearPending(); draining = false }
    fun destroy() {
        closed = true; prefs.unregisterOnSharedPreferenceChangeListener(listener)
        HookRegistry.get().unhook(unhook); unhook = null; main.removeCallbacksAndMessages(null)
        queue.clear(); boundIds.clear(); gate.clear(); bridge = null
    }
}