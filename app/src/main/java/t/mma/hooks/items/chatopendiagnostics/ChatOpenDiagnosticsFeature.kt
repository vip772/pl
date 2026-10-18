package t.mma.hooks.items.chatopendiagnostics

import android.app.Activity
import android.os.Handler
import android.os.Looper
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.model.WeChatMessage
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.FeatureContext
import t.mma.utils.HLog
import t.mma.utils.RuntimeLogStore
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * 只在运行日志开启时记录聊天页打开失败的诊断信息，不参与聊天页业务逻辑。
 */
class ChatOpenDiagnosticsFeature : BaseFeature() {
    private var runtime: Runtime? = null

    override fun featureId(): String = ID
    override fun name(): String = "聊天页打开诊断"

    override fun onFeatureInstall(context: FeatureContext) {
        if (!RuntimeLogStore.isEnabled(context.hostContext())) return
        val chatPage = WeChatApis.chatPage() ?: return
        val installed = Runtime(context, chatPage)
        runtime = installed
        trackSubscription(chatPage.subscribe(installed::onChatPageChanged))
        installed.install()
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }

    companion object {
        const val ID = "chat_open_diagnostics"
    }

    private class Runtime(
        private val context: FeatureContext,
        private val chatPage: t.mma.hooks.api.ui.WeChatChatPageApi
    ) {
        private val mainHandler = Handler(Looper.getMainLooper())
        private val worker = Executors.newSingleThreadExecutor { task ->
            Thread(task, "Hchat-ChatDiag").apply { isDaemon = true }
        }
        private val sequence = AtomicLong(0L)
        private var activeToken = 0L
        private var activeTalker = ""
        private var destroyed = false

        fun install() {
            log("installed")
        }

        fun onChatPageChanged(event: t.mma.hooks.api.ui.WeChatChatPageApi.ChatPageEvent) {
            if (destroyed) return
            if (event.isExit()) {
                if (event.talker == activeTalker) {
                    log("chatOpenState token=$activeToken state=exit talkerHash=${hash(activeTalker)}")
                }
                return
            }
            val talker = event.talker.trim()
            if (talker.isEmpty()) return
            val token = sequence.incrementAndGet()
            activeToken = token
            activeTalker = talker
            val requestedAt = android.os.SystemClock.uptimeMillis()
            log("chatOpenRequested token=$token talkerHash=${hash(talker)} group=${event.group} source=${event.activityName}")
            snapshot(token, talker, "requested")
            scheduleTimeout(token, talker, requestedAt, 2500L)
            scheduleTimeout(token, talker, requestedAt, 6000L)
        }

        private fun scheduleTimeout(token: Long, talker: String, startedAt: Long, delay: Long) {
            mainHandler.postDelayed({
                if (destroyed || token != activeToken || talker != activeTalker) return@postDelayed
                val activity = runCatching { WeChatApis.currentActivity()?.currentActivity() }.getOrNull()
                val inChat = activity?.javaClass?.name?.contains(".ui.chatting.") == true
                if (inChat) {
                    log("chatOpenState token=$token state=entered elapsedMs=${android.os.SystemClock.uptimeMillis() - startedAt} activity=${activity.javaClass.name}")
                } else {
                    log("chatOpenTimeout token=$token elapsedMs=${android.os.SystemClock.uptimeMillis() - startedAt} currentActivity=${activity?.javaClass?.name ?: "null"} talkerHash=${hash(talker)}")
                    captureMainStack(token, "timeout_${delay}ms")
                    snapshot(token, talker, "timeout_${delay}ms")
                }
            }, delay)
        }

        private fun snapshot(token: Long, talker: String, stage: String) {
            worker.execute {
                if (destroyed) return@execute
                runCatching {
                    val messages = WeChatApis.messageStore()?.getRecentMessages(talker, 12).orEmpty()
                    val summary = messages.joinToString(";") { summarize(it) }
                    log("chatMessageSnapshot token=$token stage=$stage talkerHash=${hash(talker)} count=${messages.size} messages=$summary")
                }.onFailure {
                    log("chatMessageSnapshot token=$token stage=$stage failed=${it.javaClass.name}:${it.message ?: ""}")
                }
            }
        }

        private fun summarize(message: WeChatMessage): String {
            val content = message.content
            val xml = message.xml()
            val tags = Regex("<([A-Za-z][A-Za-z0-9_-]*)\\b", RegexOption.IGNORE_CASE)
                .findAll(xml.take(4096))
                .map { it.groupValues[1].lowercase() }
                .distinct()
                .take(24)
                .joinToString(",")
            val appType = message.appMsgType()
            val kind = when {
                message.isMiniProgram() -> "mini_program"
                message.isFile() -> "file"
                message.isLink() -> "link"
                message.isRedPacket() -> "red_packet"
                message.isTransfer() -> "transfer"
                message.isQuote() -> "quote"
                message.isVideoNumberVideo() -> "video_number"
                message.isApp() -> "app"
                message.isImage() -> "image"
                message.isVideo() -> "video"
                message.isVoice() -> "voice"
                message.isEmoji() -> "emoji"
                message.isSystem() -> "system"
                message.isText() -> "text"
                else -> "unknown"
            }
            return "id=${message.msgId} type=${message.type} kind=$kind appType=$appType send=${message.isSend} time=${message.createTime} contentLen=${content.length} xmlLen=${xml.length} xmlTags=${tags.ifBlank { "none" }} contentHash=${hash(content)} xmlHash=${hash(xml)}"
        }

        private fun captureMainStack(token: Long, reason: String) {
            val main = Thread.getAllStackTraces().entries.firstOrNull { it.key.name == "main" } ?: return
            val stack = main.value.take(40).joinToString(" <- ") { frame ->
                "${frame.className}.${frame.methodName}:${frame.lineNumber}"
            }
            log("chatMainStack token=$token reason=$reason state=${main.key.state} stack=$stack")
        }

        fun destroy() {
            destroyed = true
            mainHandler.removeCallbacksAndMessages(null)
            worker.shutdownNow()
        }

        private fun log(message: String) {
            HLog.i("[Hchat:ChatOpenDiag] $message")
        }

        private fun hash(value: String): String {
            return runCatching {
                MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte) }.take(16)
            }.getOrDefault("hash_error")
        }
    }
}
