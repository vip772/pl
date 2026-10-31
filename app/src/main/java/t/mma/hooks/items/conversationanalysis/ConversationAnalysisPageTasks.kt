package t.mma.hooks.items.conversationanalysis

import android.os.Handler
import android.os.Looper
import t.mma.hooks.core.IdleSerialExecutor

/** Dialog-private reads only. Explicit host sends must never use this owner. */
internal class ConversationAnalysisPageTasks : AutoCloseable {
    private val executor = IdleSerialExecutor.create("Hchat-AnalysisPage")
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false

    @Synchronized fun execute(block: () -> Unit) {
        if (closed) return
        executor.execute { if (!closed) block() }
    }

    @Synchronized fun post(block: () -> Unit) {
        if (closed) return
        main.post { if (!closed) block() }
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        executor.queue.clear()
        executor.shutdown()
        main.removeCallbacksAndMessages(null)
    }
}
