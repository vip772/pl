package t.mma.hooks.items.conversationanalysis

import java.io.BufferedReader

/** SSE framing and throttled snapshots. No UI, connections or persistent cache owned here. */
internal object ConversationAnalysisStream {
    data class Piece(val text: String, val finished: Boolean = false)

    fun read(
        reader: BufferedReader,
        checkActive: () -> Unit,
        decode: (String) -> Piece,
        clock: () -> Long = System::nanoTime,
        onUpdate: (String) -> Unit
    ): String {
        val text = StringBuilder()
        val data = StringBuilder()
        var lastUpdate = 0L
        var emitted = false
        var terminal = false
        var finished = false
        fun dispatch() {
            if (data.isEmpty()) return
            val event = data.toString()
            data.setLength(0)
            if (event.trim() == "[DONE]") { terminal = true; return }
            val piece = decode(event)
            finished = finished || piece.finished
            if (piece.text.isEmpty()) return
            text.append(piece.text)
            val now = clock()
            if (!emitted || now - lastUpdate >= 250_000_000L) {
                checkActive()
                onUpdate(text.toString())
                emitted = true
                lastUpdate = now
            }
        }
        while (!terminal && !finished) {
            checkActive()
            val line = reader.readLine() ?: break
            if (line.isEmpty()) dispatch()
            else if (line.startsWith("data:")) {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.substring(5).removePrefix(" "))
            }
        }
        if (!terminal) dispatch()
        checkActive()
        check(terminal || finished) { "AI流式响应中断，请重新生成" }
        return text.toString()
    }
}
