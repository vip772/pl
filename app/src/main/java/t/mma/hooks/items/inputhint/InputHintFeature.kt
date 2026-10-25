package t.mma.hooks.items.inputhint

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.hooks.items.script.ScriptSendButtonHook
import t.mma.utils.KavaReflector
import java.util.Collections
import java.util.WeakHashMap
import java.lang.ref.WeakReference

class InputHintFeature : BaseFeature() {
    private var runtime: InputHintRuntime? = null
    private var statisticsInstalled = false

    override fun featureId(): String = ID

    override fun name(): String = "输入框提示"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(InputHintSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        runtime = InputHintRuntime(context.hostContext(), context.hostClassLoader()) { message, throwable ->
            if (throwable == null) logIneffective(message) else logError(message, throwable)
        }
        runtime?.installInputHook()
        installStatistics()
        subscribe(Events.DexReady::class.java) {
            runtime?.installInputHook()
            installStatistics()
        }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
        statisticsInstalled = false
    }

    private fun installStatistics() {
        if (statisticsInstalled) return
        val subscription = runtime?.installStatistics() ?: return
        trackSubscription(subscription)
        statisticsInstalled = true
    }

    companion object {
        const val ID = "input_hint"
    }
}

private class InputHintRuntime(
    context: Context,
    private val classLoader: ClassLoader,
    private val logger: (String, Throwable?) -> Unit
) {
    private val appContext = context.applicationContext ?: context
    private val prefs = InputHintSettings.preferences(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val inputBindings = Collections.synchronizedMap(
        WeakHashMap<Any, InputHintBinding>()
    )
    // Listeners belong only to verified ChatFooter instances, never global TextViews.
    private val footerListeners = WeakHashMap<View, View.OnLayoutChangeListener>()
    private val queuedFooters = WeakHashMap<View, Boolean>()
    private val ownedHooks = mutableListOf<XC_MethodHook.Unhook>()
    private var writingHint = false
    private var renderRevision = 0L
    private var renderedHintCache: CharSequence? = null
    private var renderedHintExpiresAt = Long.MAX_VALUE
    private var refreshQueued = false
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == InputHintSettings.KEY_ENABLE ||
            key == InputHintSettings.KEY_TEMPLATE ||
            key == InputHintSettings.KEY_STATISTICS_ENABLE ||
            key?.startsWith("input_hint_color_") == true
        ) {
            refreshAttachedInputs()
        }
    }

    @Volatile
    private var active = true

    @Volatile
    private var inputHookInstalled = false

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    @Synchronized
    fun installInputHook(): Boolean {
        if (inputHookInstalled) return true
        val footerClass = KavaReflector.loadClass(CHAT_FOOTER_CLASS, classLoader) ?: run {
            logger("未找到微信聊天输入区类", null)
            return false
        }
        val attachMethod = KavaReflector.declaredMethods(footerClass).singleOrNull { method ->
            method.name == "onAttachedToWindow" &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.isEmpty()
        } ?: run {
            logger("未找到聊天输入区挂载方法", null)
            return false
        }
        val hintMethod = KavaReflector.declaredMethods(footerClass).singleOrNull {
            it.name == "setHint" && it.returnType == Void.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(CharSequence::class.java))
        } ?: run { logger("未找到聊天输入区提示更新方法", null); return false }
        val installed = mutableListOf<XC_MethodHook.Unhook>()
        return runCatching {
            installed += HookRegistry.get().hook(attachMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) { safelyBindFooter(param.thisObject) }
            })
            installed += HookRegistry.get().hook(hintMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!writingHint) safelyBindFooter(param.thisObject)
                }
            })
            ownedHooks += installed
            inputHookInstalled = true
            true
        }.getOrElse {
            installed.forEach { hook -> runCatching { HookRegistry.get().unhook(hook) } }
            logger("聊天输入框提示 Hook 安装失败", it)
            false
        }
    }
    private fun safelyBindFooter(footer: Any?, retry: Boolean = true) {
        if (!active || writingHint) return
        runCatching { bindFooter(footer, retry) }.onFailure { logger("聊天输入框提示绑定失败", it) }
    }

    fun installStatistics(): OutgoingMessageStatsRepository.Subscription? {
        if (!OutgoingMessageStatsRepository.install(logger)) {
            // 启动早期数据库监听可能尚未完成，DexReady 后会再次尝试安装。
            logger("消息变更 API 尚未就绪，等待后续初始化", null)
            return null
        }
        return OutgoingMessageStatsRepository.subscribe(::refreshAttachedInputs)
    }

    fun destroy() {
        active = false
        ownedHooks.forEach { runCatching { HookRegistry.get().unhook(it) } }; ownedHooks.clear()
        footerListeners.forEach { (view, listener) -> view.removeOnLayoutChangeListener(listener) }
        footerListeners.clear(); queuedFooters.clear()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        val inputs = synchronized(inputBindings) {
            inputBindings.entries.map { it.key to it.value }.also { inputBindings.clear() }
        }
        inputs.forEach { (input, binding) ->
            postToInput(input) {
                if (binding.appliedHint != null && TextUtils.equals(readHint(input), binding.appliedHint)) {
                    writeHint(input, binding.originalHint)
                }
            }
        }
    }

    private fun bindFooter(footer: Any?, retry: Boolean) {
        if (!active || footer == null) return
        val view = footer as? View
        if (view != null && !footerListeners.containsKey(view)) {
            val listener = View.OnLayoutChangeListener { changed, _, _, _, _, _, _, _, _ ->
                safelyBindFooter(changed, retry = false)
            }
            footerListeners[view] = listener
            view.addOnLayoutChangeListener(listener)
        }
        ScriptSendButtonHook.findInputView(footer)?.let(::bindInput)
        // At most one retry per event burst. Later readiness/replacement is handled
        // by footer layout events, not arbitrary sleeps or pre-draw polling.
        if (retry && view != null && !queuedFooters.containsKey(view)) {
            queuedFooters[view] = true
            val reference = WeakReference(view)
            view.post {
                val owner = reference.get() ?: return@post
                queuedFooters.remove(owner)
                if (active && owner.isAttachedToWindow) runCatching {
                    ScriptSendButtonHook.findInputView(owner)?.let(::bindInput)
                }.onFailure { logger("聊天输入框提示延后绑定失败", it) }
            }
        }
    }
    private fun bindInput(input: Any) {
        if (System.currentTimeMillis() >= renderedHintExpiresAt) {
            renderedHintCache = null
            renderedHintExpiresAt = Long.MAX_VALUE
            renderRevision++
        }
        ScriptSendButtonHook.trackInputView(input)
        if (!supportsHint(input)) return
        synchronized(inputBindings) {
            if (!inputBindings.containsKey(input)) {
                inputBindings[input] = InputHintBinding(readHint(input))
            }
        }
        val binding = synchronized(inputBindings) { inputBindings[input] }
        if (binding?.appliedHint != null && binding.revision == renderRevision &&
            sameStyledHint(readHint(input), binding.appliedHint)) return
        applyHint(input)
    }

    private fun refreshAttachedInputs() {
        // Coalesce preference/statistics bursts; consume the latest state on main.
        synchronized(inputBindings) {
            if (!active || refreshQueued) return
            refreshQueued = true
        }
        mainHandler.post {
            synchronized(inputBindings) { refreshQueued = false }
            if (!active) return@post
            renderRevision++
            renderedHintCache = null
            val inputs = synchronized(inputBindings) { inputBindings.keys.toList() }
            inputs.forEach { input ->
                if ((input as? View)?.isAttachedToWindow != false) applyHint(input)
            }
        }
    }

    // TextView may copy Spanned hints. Preserve style semantics, not just text.
    private fun sameStyledHint(a: CharSequence?, b: CharSequence?): Boolean {
        if (a === b) return true
        if (!TextUtils.equals(a, b)) return false
        val sa = a as? Spanned
        val sb = b as? Spanned
        val left = sa?.getSpans(0, sa.length, Any::class.java).orEmpty()
        val right = sb?.getSpans(0, sb.length, Any::class.java).orEmpty()
        if (left.size != right.size) return false
        val used = BooleanArray(right.size)
        return left.all { span ->
            val match = right.indices.firstOrNull { i ->
                val other = right[i]
                !used[i] && span.javaClass == other.javaClass &&
                    (span === other || (span is ForegroundColorSpan && other is ForegroundColorSpan &&
                        span.foregroundColor == other.foregroundColor) || span == other) &&
                    sa!!.getSpanStart(span) == sb!!.getSpanStart(other) &&
                    sa.getSpanEnd(span) == sb.getSpanEnd(other) &&
                    sa.getSpanFlags(span) == sb.getSpanFlags(other)
            } ?: return@all false
            used[match] = true
            true
        }
    }

    private fun applyHint(input: Any) {
        if (!active) return
        val binding = synchronized(inputBindings) { inputBindings[input] } ?: return
        if (!prefs.getBoolean(InputHintSettings.KEY_ENABLE, InputHintSettings.DEFAULT_ENABLE)) {
            if (binding.appliedHint != null && TextUtils.equals(readHint(input), binding.appliedHint)) {
                writeHint(input, binding.originalHint)
            }
            binding.appliedHint = null
            return
        }
        val renderedHint = renderedHintCache ?: run {
            val statisticsEnabled = prefs.getBoolean(
                InputHintSettings.KEY_STATISTICS_ENABLE,
                InputHintSettings.DEFAULT_STATISTICS_ENABLE
            )
            val stats = if (statisticsEnabled) {
                OutgoingMessageStatsRepository.current()
            } else {
                InputHintStats()
            }
            renderedHintExpiresAt = if (statisticsEnabled) java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis else Long.MAX_VALUE
            InputHintSettings.renderTemplateSpanned(
                InputHintSettings.template(prefs), stats, InputHintSettings.variableColors(prefs)
            ).also { renderedHintCache = it }
        }
        val currentHint = readHint(input)
        if (binding.appliedHint == null || !sameStyledHint(currentHint, binding.appliedHint)) {
            binding.originalHint = currentHint
        }
        binding.appliedHint = renderedHint
        binding.revision = renderRevision
        if (!sameStyledHint(currentHint, renderedHint)) writeHint(input, renderedHint)
    }

    private fun supportsHint(input: Any): Boolean {
        return input is TextView || KavaReflector.findMethodRecursive(
            input.javaClass,
            "setHint",
            CharSequence::class.java
        ) != null
    }

    private fun readHint(input: Any): CharSequence? {
        if (input is TextView) return input.hint
        return KavaReflector.invoke(
            KavaReflector.findMethodRecursive(input.javaClass, "getHint"),
            input
        ) as? CharSequence
    }

    private fun writeHint(input: Any, hint: CharSequence?) {
        if (writingHint) return
        writingHint = true
        try {
            if (input is TextView) {
                input.hint = hint
                return
            }
            KavaReflector.invokeSuccessfully(
                KavaReflector.findMethodRecursive(input.javaClass, "setHint", CharSequence::class.java), input, hint
            )
        } finally { writingHint = false }
    }
    private fun postToInput(input: Any, block: () -> Unit) {
        val view = input as? View
        if (view != null && view.isAttachedToWindow) {
            view.post { block() }
        } else {
            mainHandler.post { block() }
        }
    }

    companion object {
        private const val CHAT_FOOTER_CLASS = "com.tencent.mm.pluginsdk.ui.chat.ChatFooter"
    }

    private data class InputHintBinding(
        var originalHint: CharSequence?,
        var appliedHint: CharSequence? = null,
        var revision: Long = -1L
    )
}
