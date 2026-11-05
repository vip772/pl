package t.mma.hooks.items.backgroundbeauty

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.preferences.HchatStorage
import t.mma.utils.HLog
import t.mma.utils.KavaReflector
import java.lang.reflect.Member
import java.util.Collections
import java.util.WeakHashMap

/** Verified 8.0.78 only. No global View hook, polling, layout listener or worker. */
internal class ExtraEntryVisibilityRuntime(private val context: FeatureContext) {
    private val prefs = HchatStorage.preferences(context.hostContext(), BackgroundBeautySettings.PREFS_NAME)
    private val main = Handler(Looper.getMainLooper())
    private val voice = Ownership()
    private val computer = Ownership()
    private val bannerHelpers = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private var listening = false
    @Volatile private var destroyed = false
    private var voiceRequested = false
    private var computerRequested = false
    private val failures = HashSet<String>()
    private val refresh = Runnable { if (!destroyed) update() }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (!destroyed && (key == null || key in ExtraEntryVisibility.keys)) {
            main.removeCallbacks(refresh)
            main.post(refresh)
        }
    }

    fun install() {
        if (destroyed || listening) return
        // Register cleanup ownership before registration: a partially registered listener
        // must still be removed when a storage implementation throws.
        listening = true
        try {
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onMain { if (!destroyed) update() }
        } catch (error: Throwable) {
            report("preferences/install", error)
            destroy()
        }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        main.removeCallbacksAndMessages(null)
        if (listening) {
            listening = false
            runCatching { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
                .onFailure { report("preferences/release", it) }
        }
        onMain {
            voiceRequested = false
            computerRequested = false
            voice.close()
            closeComputer()
        }
    }
    private fun update() {
        voiceRequested = prefs.getBoolean(ExtraEntryVisibility.VOICE, false)
        computerRequested = prefs.getBoolean(ExtraEntryVisibility.COMPUTER, false)
        if (WeChatApis.version()?.versionName() != "8.0.78") {
            voice.close()
            closeComputer()
            return
        }
        if (voiceRequested) {
            if (!voice.installed) installVoice()
            voice.owners.toList().forEach { safe("SpeechInputLayout/refresh") { applyVoice(it) } }
        } else voice.close()
        if (computerRequested) {
            if (!computer.installed) installComputer()
            computer.owners.toList().forEach { safe("OtherOnlineBanner/refresh") { applyComputer(it) } }
        } else closeComputer()
    }

    private fun installVoice() {
        val attempt = ArrayList<XC_MethodHook.Unhook>()
        try {
            val footerClass = requireNotNull(KavaReflector.loadClass(FOOTER, context.hostClassLoader()))
            val speechClass = requireNotNull(KavaReflector.loadClass(SPEECH_INPUT, context.hostClassLoader()))
            val initialize = requireNotNull(KavaReflector.findMethod(footerClass, "Z0",
                android.content.Context::class.java, android.util.AttributeSet::class.java))
            val attached = requireNotNull(KavaReflector.findMethod(footerClass, "onAttachedToWindow"))
            val status = requireNotNull(KavaReflector.findMethod(speechClass, "c", Int::class.javaPrimitiveType!!))
            val window = requireNotNull(KavaReflector.findMethod(speechClass, "onWindowVisibilityChanged",
                Int::class.javaPrimitiveType!!))
            val callback = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (destroyed || !voiceRequested) return
                    safe("SpeechInputLayout/before") {
                        rightVoiceView(param.thisObject)?.let(voice::restore)
                    }
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (destroyed || !voiceRequested || param.hasThrowable()) return
                    safe("SpeechInputLayout/after") { applyVoice(param.thisObject) }
                }
            }
            listOf(initialize, attached, status, window).forEach { attempt.add(hook(it, callback)) }
            voice.hooks.addAll(attempt)
            voice.installed = true
            // One bounded lookup on enable; never inspect the scrolling message list repeatedly.
            WeChatApis.currentActivity()?.currentActivity()?.window?.decorView?.let { root ->
                findFooter(root)?.let(::applyVoice)
            }
        } catch (error: Throwable) {
            if (!voice.installed) attempt.forEach { unhook(it) }
            voice.close()
            report("ChatFooter.Z0/attach/SpeechInputLayout.c/window/install", error)
        }
    }

    /** Only the right-hand w2n control owned by a ChatFooter, never shared search inputs. */
    private fun rightVoiceView(owner: Any): View? {
        val view = owner as? View ?: return null
        if (view.javaClass.name == FOOTER) {
            return view.findViewById<View>(ExtraEntryVisibility.RIGHT_VOICE_ID)
                ?.takeIf { it.javaClass.name == SPEECH_INPUT }
        }
        if (view.javaClass.name != SPEECH_INPUT || view.id != ExtraEntryVisibility.RIGHT_VOICE_ID) return null
        var parent = view.parent
        while (parent is View) {
            if (parent.javaClass.name == FOOTER) return view
            parent = parent.parent
        }
        return null
    }

    private fun applyVoice(owner: Any) {
        val button = rightVoiceView(owner) ?: return
        voice.owners.add(owner)
        if (voiceRequested && !destroyed) voice.hide(button) else voice.restore(button)
    }

    private fun installComputer() {
        val attempt = ArrayList<XC_MethodHook.Unhook>()
        try {
            val clazz = requireNotNull(KavaReflector.loadClass(BANNER, context.hostClassLoader()))
            val refreshMethod = requireNotNull(KavaReflector.findMethod(clazz, "i"))
            val visibility = requireNotNull(KavaReflector.findMethod(clazz, "k", Int::class.javaPrimitiveType!!))
            requireNotNull(KavaReflector.findFieldRecursive(clazz, "n"))
            requireNotNull(KavaReflector.findFieldRecursive(clazz, "p"))
            val callback = object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (destroyed || !computerRequested) return
                    safe("OtherOnlineBanner/before") { restoreComputer(param.thisObject) }
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (destroyed || !computerRequested || param.hasThrowable()) return
                    safe("OtherOnlineBanner/after") {
                        computer.owners.add(param.thisObject)
                        if (applyComputer(param.thisObject) && param.method == refreshMethod) param.result = false
                    }
                }
            }
            listOf(refreshMethod, visibility).forEach { attempt.add(hook(it, callback)) }
            // MainUI.x is its BannerHelper; resume refreshes existing rows after settings.
            val mainClass = requireNotNull(KavaReflector.loadClass(
                "com.tencent.mm.ui.conversation.MainUI", context.hostClassLoader()))
            val resume = requireNotNull(KavaReflector.findMethod(mainClass, "onResume"))
            val helperField = requireNotNull(KavaReflector.findFieldRecursive(mainClass, "x"))
            attempt.add(hook(resume, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (destroyed || !computerRequested || param.hasThrowable()) return
                    safe("MainUI.x/h/resume") {
                        val helper = KavaReflector.readField(helperField, param.thisObject) ?: return@safe
                        bannerHelpers.add(helper)
                        refreshBannerHelper(helper)
                    }
                }
            }))
            computer.hooks.addAll(attempt)
            computer.installed = true
        } catch (error: Throwable) {
            attempt.forEach { unhook(it) }
            computer.close()
            report("OtherOnlineBanner.i/k/install", error)
        }
    }

    private fun refreshBannerHelper(helper: Any) {
        val rebuild = KavaReflector.findMethod(helper.javaClass, "h") ?: return
        KavaReflector.invokeOrThrow(rebuild, helper)
    }
    private fun closeComputer() {
        val helpers = bannerHelpers.toList()
        bannerHelpers.clear()
        computer.close()
        // i() participates in BannerHelper's shared header/group visibility. Restoring
        // only n/f/h cannot repair that state; rebuild after removing our hooks.
        helpers.forEach { helper -> safe("BannerHelper.h/restore") { refreshBannerHelper(helper) } }
    }
    private fun applyComputer(owner: Any): Boolean {
        val icon = KavaReflector.readField(owner, "p") as? View
        if (destroyed || !computerRequested || !ExtraEntryVisibility.isComputerIcon(icon?.tag)) {
            restoreComputer(owner)
            return false
        }
        // n is the visible status row. f/h are its dedicated wrap-content containers.
        listOf("n", "f", "h").forEach { name ->
            (KavaReflector.readField(owner, name) as? View)?.let(computer::hide)
        }
        return true
    }

    private fun restoreComputer(owner: Any) {
        listOf("n", "f", "h").forEach { name ->
            (KavaReflector.readField(owner, name) as? View)?.let(computer::restore)
        }
    }

    private fun findFooter(root: View): View? {
        val pending = java.util.ArrayDeque<View>()
        pending.add(root)
        var remaining = 4096
        while (pending.isNotEmpty() && remaining-- > 0) {
            val view = pending.removeLast()
            if (view.javaClass.name == FOOTER) return view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    if (pending.size >= remaining) break
                    pending.add(view.getChildAt(i))
                }
            }
        }
        return null
    }

    private fun hook(member: Member, callback: XC_MethodHook) = HookRegistry.get().hook(member, callback)
    private fun unhook(handle: XC_MethodHook.Unhook) {
        runCatching { HookRegistry.get().unhook(handle) }.onFailure { report("unhook", it) }
    }
    private inline fun safe(stage: String, action: () -> Unit) {
        try { action() } catch (error: Throwable) { report(stage, error) }
    }
    private fun report(stage: String, error: Throwable) {
        if (failures.add(stage)) HLog.e("隐藏条目 微信8.0.78 $stage", error)
    }
    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() }
    }

    private inner class Ownership {
        var installed = false
        val hooks = ArrayList<XC_MethodHook.Unhook>()
        val owners = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
        // Weak keys, scalar values: never retain an Activity through its views.
        val originals = WeakHashMap<View, Int>()
        fun hide(view: View) {
            if (!originals.containsKey(view)) originals[view] = view.visibility
            if (view.visibility != View.GONE) view.visibility = View.GONE
        }
        fun restore(view: View) {
            val original = originals.remove(view) ?: return
            // Do not undo an independent native/other-feature write.
            if (view.visibility == View.GONE) view.visibility = original
        }
        fun close() {
            hooks.toList().forEach(::unhook)
            hooks.clear()
            originals.keys.toList().forEach { view -> safe("restore") { restore(view) } }
            originals.clear()
            owners.clear()
            installed = false
        }
    }

    companion object {
        private const val SPEECH_INPUT = "com.tencent.mm.pluginsdk.ui.SpeechInputLayout"
        private const val FOOTER = "com.tencent.mm.pluginsdk.ui.chat.ChatFooter"
        private const val BANNER = "com.tencent.mm.ui.conversation.banner.l0"
    }
}
