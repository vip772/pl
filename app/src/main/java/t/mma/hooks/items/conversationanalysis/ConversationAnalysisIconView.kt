package t.mma.hooks.items.conversationanalysis

import android.content.Context
import android.graphics.Canvas
import android.view.View

/** Canvas icons, independent of installed fonts and emoji rendering. */
internal class ConversationAnalysisIconView(
    context: Context,
    kind: Kind,
    private val iconColor: Int = 0xffff7a00.toInt(),
    private val iconSizeDp: Float = 28f
) : View(context) {
    enum class Kind { PEOPLE, MESSAGE, HISTORY, REPORT, LIST, SETTINGS, SPARKLE, CHEVRON_UP, CHEVRON_DOWN }
    private val painter = ConversationAnalysisIconPainter(kind)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width.toFloat(), height.toFloat(), iconSizeDp * resources.displayMetrics.density)
        painter.draw(canvas, (width - size) / 2f, (height - size) / 2f, size, size, iconColor)
    }
}
