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
        val clean = raw.removePrefix(MARKER).removePrefix(PREFIX)
        return SpannableStringBuilder(clean).apply {
            getSpans(0, length, android.text.style.CharacterStyle::class.java).forEach { removeSpan(it) }
            setSpan(NoticeGradientSpan(colors(context).map { Color.parseColor(it) }.toIntArray(), clean),
                0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or (255 shl Spanned.SPAN_PRIORITY_SHIFT))
        }
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
                    highlight(root.context, content)?.let { view.text = it }
                }
            }
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) pending.add(view.getChildAt(i))
        }
    }
    private class NoticeGradientSpan(private val stops: IntArray, private val text: String) : android.text.style.CharacterStyle() {
        override fun updateDrawState(paint: android.text.TextPaint) {
            paint.color = stops[0]
            paint.shader = if (stops.all { it == stops[0] }) null else android.graphics.LinearGradient(
                0f, 0f, paint.measureText(text).coerceAtLeast(1f), 0f,
                stops, floatArrayOf(0f, .5f, 1f), android.graphics.Shader.TileMode.CLAMP)
        }
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
        t.mma.hooks.items.groupleave.GroupLeaveMonitorHighlighter.installTextCleanup(context.hostContext()) { message, error ->
            if (error != null) HLog.e(message, error) else HLog.e(message)
        }
        subscribe(Events.DexReady::class.java) {
            DexInstallScheduler.schedule(featureId(), name()) {
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
