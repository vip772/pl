package t.mma.hooks.items.mentionextension

import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.message.WeChatMessageObserveApi
import t.mma.hooks.api.model.WeChatMessage
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.preferences.HchatStorage
import t.mma.utils.HLog
import t.mma.utils.KavaReflector
import t.mma.hooks.core.HookRegistry
import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Modifier

object MentionNoticeSettings {
    const val PREFS = "Hchat_mention_notice_config"
    const val ENABLE = "mention_notice_enable"
    const val PREFIX = "[艾特提醒] "
    fun enabled(context: Context) = HchatStorage.preferences(context, PREFS).getBoolean(ENABLE, false)

    const val COLOR = "mention_notice_color"
    const val DEFAULT_COLOR = "#E53935,#E53935,#E53935"
    fun colors(context: Context): List<String> = t.mma.hooks.items.antirecall.DynamicRecallGradient.parts(
        HchatStorage.preferences(context, PREFS).getString(COLOR, DEFAULT_COLOR).orEmpty().ifBlank { DEFAULT_COLOR })
    const val MARKER = "\u2063\u2060\u2063"
    private fun known(text: String): Boolean = text.startsWith(MARKER) || text.startsWith(PREFIX)
    fun highlight(context: Context, text: CharSequence): CharSequence? {
        val raw = text.toString()
        if (!known(raw) || !enabled(context)) return null
        // NeatTextView keeps offsets from its original input. Never shorten or
        // replace its character sequence, even for invisible module markers.
        return style(context, text)
    }
    private fun style(context: Context, text: CharSequence): CharSequence {
        val raw = text.toString()
        val prefixLength = when {
            raw.startsWith(MARKER) -> MARKER.length
            raw.startsWith(PREFIX) -> PREFIX.length
            else -> 0
        }
        val stops = colors(context).map { Color.parseColor(it) }
        return SpannableStringBuilder(text).apply {
            getSpans(0, length, android.text.style.ForegroundColorSpan::class.java).forEach { removeSpan(it) }
            getSpans(0, length, HiddenPrefixSpan::class.java).forEach { removeSpan(it) }
            if (prefixLength > 0) setSpan(HiddenPrefixSpan(), 0, prefixLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            var index = prefixLength
            while (index < length) {
                val end = (index + Character.charCount(raw.codePointAt(index))).coerceAtMost(length)
                val fraction = (index - prefixLength).toFloat() / (length - prefixLength - 1).coerceAtLeast(1)
                val section = if (fraction < .5f) 0 else 1
                val blend = if (section == 0) fraction * 2 else (fraction - .5f) * 2
                val from = stops[section]
                val to = stops[section + 1]
                fun channel(a: Int, b: Int) = (a + (b - a) * blend).toInt()
                val color = Color.argb(channel(Color.alpha(from), Color.alpha(to)),
                    channel(Color.red(from), Color.red(to)), channel(Color.green(from), Color.green(to)),
                    channel(Color.blue(from), Color.blue(to)))
                setSpan(android.text.style.ForegroundColorSpan(color), index, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                index = end
            }
        }
    }
    private val renderMethods = HashSet<java.lang.reflect.Method>()
    @Synchronized
    fun installRenderer(context: FeatureContext): Boolean {
        val classes = listOfNotNull(
            KavaReflector.loadClass("com.tencent.mm.view.x2c.X2CTextView", context.hostClassLoader()),
            KavaReflector.loadClass("com.tencent.neattextview.textview.view.NeatTextView", context.hostClassLoader()),
            KavaReflector.loadClass("com.tencent.mm.ui.widget.MMNeat7extView", context.hostClassLoader()))
        var found = false
        for (clazz in classes) {
            var current: Class<*>? = clazz
            while (current != null && current != android.view.View::class.java && current != android.widget.TextView::class.java) {
                for (method in KavaReflector.declaredMethods(current)) {
                    val types = method.parameterTypes
                    if (Modifier.isStatic(method.modifiers) || Modifier.isAbstract(method.modifiers) ||
                        types.firstOrNull() != CharSequence::class.java) continue
                    val supported = types.size == 1 || (types.size == 2 && types[1] == java.lang.Boolean.TYPE) ||
                        (types.size == 3 && types[1] == android.widget.TextView.BufferType::class.java && types[2] == java.lang.Boolean::class.java)
                    if (!supported) continue
                    found = true
                    if (method in renderMethods) continue
                    val hook = HookRegistry.get().hook(method, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val input = param.args?.getOrNull(0) as? CharSequence ?: return
                            // Exact module prefix only; ordinary messages and timestamps pass unchanged.
                            if (!known(input.toString())) return
                            try {
                                val styled = highlight(context.hostContext(), input) ?: return
                                check(styled.length == input.length && styled.toString() == input.toString())
                                param.args[0] = styled
                            } catch (error: Throwable) {
                                HLog.e("[Hchat:艾特提醒] 渲染失败: ${method.toGenericString()}", error)
                            }
                        }
                    })
                    if (hook != null) renderMethods.add(method) else return false
                }
                current = current.superclass
            }
        }
        return found
    }
    fun bind(root: android.view.View, content: String) {
        if (!enabled(root.context) || !known(content)) return
        val pending = java.util.ArrayDeque<android.view.View>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val view = pending.removeFirst()
            if (view is android.widget.TextView) {
                val raw = view.text?.toString().orEmpty()
                if (raw == content || raw == content.removePrefix(MARKER).removePrefix(PREFIX)) {
                    view.text = style(root.context, view.text)
                }
            }
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) pending.add(view.getChildAt(i))
        }
    }
    // Hide legacy visible prefixes without changing offsets or deleting host spans.
    private class HiddenPrefixSpan : android.text.style.ReplacementSpan() {
        override fun getSize(paint: android.graphics.Paint, text: CharSequence, start: Int,
            end: Int, fm: android.graphics.Paint.FontMetricsInt?): Int = 0
        override fun draw(canvas: android.graphics.Canvas, text: CharSequence, start: Int,
            end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: android.graphics.Paint) = Unit
    }

}

class MentionNoticeFeature : BaseFeature() {
    override fun featureId() = "mention_notice"
    override fun name() = "艾特提醒"
    private var observing = false
    private val pending = HashSet<String>()
    private val worker = java.util.concurrent.ThreadPoolExecutor(
        1, 1, 30, java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.ArrayBlockingQueue<Runnable>(256),
        java.util.concurrent.ThreadFactory { task -> Thread(task, "Hchat-MentionNotice").apply { isDaemon = true } }
    ).apply { allowCoreThreadTimeOut(true) }
    override fun onFeatureInstall(context: FeatureContext) {
        if (context.loadPackageParam().processName != context.loadPackageParam().packageName) return
        subscribe(Events.DexReady::class.java) {
            DexInstallScheduler.schedule(featureId(), name()) {
                if (!MentionNoticeSettings.installRenderer(context)) return@schedule false
                val local = WeChatApis.localMessages() ?: return@schedule false
                local.ensureReady()
                val observe = WeChatApis.message().observe() ?: return@schedule false
                if (!local.isAvailable()) return@schedule false
                if (!observing) {
                    trackSubscription(observe.subscribe { event ->
                        if (MentionNoticeSettings.enabled(context.hostContext()) && event.group && !event.outgoing) {
                            try { worker.execute { handle(context.hostContext(), event) } }
                            catch (error: java.util.concurrent.RejectedExecutionException) {
                                HLog.e("[Hchat:艾特提醒] 消息处理队列已满或关闭", error)
                            }
                        }
                    })
                    observing = true
                }
                true
            }
        }
    }
    override fun onFeatureDestroy(context: FeatureContext) {
        worker.shutdownNow()
        observing = false
    }
    private fun handle(context: Context, event: WeChatMessageObserveApi.ObservedMessage) {
        if (!MentionNoticeSettings.enabled(context) || !event.group || event.outgoing) return
        val msg = event.message ?: return
        if (!msg.isIncoming() || !msg.isGroupChat()) return
        val self = WeChatApis.account()?.selfWxId().orEmpty()
        if (self.isBlank()) return
        val sender = event.sender.ifBlank { msg.getSendTalker() }
        if (sender.isBlank() || sender == self || sender == event.talker) return
        val type = WeChatMessage.classifyAtMention(msg.getMsgSource(), msg.bodyContent(), self)
        if (type != WeChatMessage.AtMentionType.AT_ME && type != WeChatMessage.AtMentionType.AT_ALL) return
        // Stable server identity is shared by add_msg and fresh database-insert observations.
        // Unknown identity fails closed rather than merging unrelated messages by content.
        val identity = when {
            msg.msgSvrId > 0 -> "svr:${msg.msgSvrId}"
            else -> return
        }
        val key = "$self|${event.talker}|$identity"
        val store = HchatStorage.preferences(context, MentionNoticeSettings.PREFS + "_seen")
        synchronized(pending) {
            if (store.getBoolean(key, false) || !pending.add(key)) return
        }
        try {
            val contacts = WeChatApis.contact().contacts()
            val name = contacts?.getGroupMemberDisplayName(event.talker, sender).orEmpty()
                .ifBlank { contacts?.getDisplayName(sender).orEmpty() }.ifBlank { sender }
                .replace('\n', ' ').replace('\r', ' ')
            val suffix = if (type == WeChatMessage.AtMentionType.AT_ALL) "艾特了所有人" else "艾特了你"
            if (Thread.currentThread().isInterrupted || !MentionNoticeSettings.enabled(context) || WeChatApis.account()?.selfWxId() != self) return
            // Reserve persistently before insertion: process restart must not duplicate a notice.
            if (!store.edit().putBoolean(key, true).commit()) return
            val notice = MentionNoticeSettings.MARKER + name + " " + suffix
            val inserted = WeChatApis.localMessages()?.insertSystemMessageAt(
                event.talker, notice,
                msg.createTime.let { if (it in 1..99_999_999_999L) it * 1000 else it }
                    .takeIf { it > 0 }?.plus(1) ?: System.currentTimeMillis()
            ) ?: 0L
            if (inserted <= 0) {
                store.edit().remove(key).commit()
                HLog.e("[Hchat:艾特提醒] 本地提示插入失败: $identity")
            }
        } catch (error: Throwable) {
            HLog.e("[Hchat:艾特提醒] 处理失败: $identity", error)
        } finally {
            synchronized(pending) { pending.remove(key) }
        }
    }
}
