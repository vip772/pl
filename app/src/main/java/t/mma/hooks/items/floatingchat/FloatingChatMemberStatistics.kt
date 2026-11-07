package t.mma.hooks.items.floatingchat

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.ui.WeChatChatPageApi
import t.mma.utils.HLog
import java.lang.ref.WeakReference
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One visible room, one running read and at most one queued replacement. No disk cache. */
internal class FloatingChatMemberStatistics(private val changed: () -> Unit) : AutoCloseable {
    companion object { const val KEY = "floating_chat_header_member_statistics_enabled" }
    private val main = Handler(Looper.getMainLooper())
    private var executor: ThreadPoolExecutor? = null
    private var task: Future<*>? = null
    private var header = WeakReference<ViewGroup>(null)
    private var room = ""
    private var generation = 0L
    private var closed = false
    private var text: String? = null
    private var pageApi: WeChatChatPageApi? = null
    private var subscription: WeChatChatPageApi.Subscription? = null
    private val detach = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) { if (header.get() === view) reset() }
    }

    fun bind(view: ViewGroup, enabled: Boolean): String? {
        if (closed) return null
        if (!enabled) { stop(); return null }
        val api = WeChatApis.chatPage()
        if (pageApi !== api) {
            subscription?.unsubscribe()
            pageApi = api
            subscription = api?.subscribe { event ->
                main.post {
                    if (!closed) {
                        if (event.isExit()) reset()
                        changed()
                    }
                }
            }
        }
        val current = api?.currentTalker().orEmpty()
        if (!current.endsWith("@chatroom") && !current.endsWith("@im.chatroom")) {
            reset()
            return null
        }
        if (room == current && header.get() === view) return text
        reset()
        room = current
        header = WeakReference(view)
        view.addOnAttachStateChangeListener(detach)
        text = "男 — · 女 — · 未知 — · 好友 —"
        val ticket = generation
        val worker = executor ?: ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS,
            ArrayBlockingQueue<Runnable>(1), { runnable ->
                Thread(runnable, "Hchat-GroupStatistics").apply { priority = Thread.MIN_PRIORITY }
            }).apply { allowCoreThreadTimeOut(true) }.also { executor = it }
        task = worker.submit {
            val result = try { WeChatApis.contacts()?.getGroupMemberStatistics(current) }
            catch (error: Throwable) { HLog.e("[Hchat:群员性别好友统计] 读取失败", error); null }
            if (!Thread.currentThread().isInterrupted) main.post {
                if (closed || generation != ticket || room != current || header.get()?.isAttachedToWindow != true) return@post
                text = result?.let { "男 ${it.male} · 女 ${it.female} · 未知 ${it.unknown} · 好友 ${it.friends}" }
                    ?: "群员统计暂不可用"
                task = null
                changed()
            }
        }
        return text
    }

    private fun reset() {
        generation++
        task?.cancel(true)
        task = null
        executor?.queue?.clear()
        header.get()?.removeOnAttachStateChangeListener(detach)
        header.clear()
        room = ""
        text = null
    }
    fun stop() {
        reset()
        subscription?.unsubscribe()
        subscription = null
        pageApi = null
        executor?.shutdownNow()
        executor = null
        main.removeCallbacksAndMessages(null)
    }
    override fun close() { if (!closed) { closed = true; stop() } }
}
