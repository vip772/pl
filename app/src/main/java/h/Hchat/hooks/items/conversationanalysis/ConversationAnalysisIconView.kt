package h.Hchat.hooks.items.conversationanalysis

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Small vector-like icon used by the native report UI; no bitmap resource required. */
internal class ConversationAnalysisIconView(
    context: Context,
    private val kind: Kind
) : View(context) {
    enum class Kind { PEOPLE, MESSAGE, HISTORY, REPORT }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2.4f * resources.displayMetrics.density
        color = 0xffff7a00.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density
        val cx = width * 0.5f
        val cy = height * 0.5f
        when (kind) {
            Kind.PEOPLE -> {
                canvas.drawCircle(cx, cy - 8 * d, 5 * d, paint)
                val shoulders = Path().apply {
                    moveTo(cx - 12 * d, cy + 12 * d)
                    lineTo(cx - 9 * d, cy + 2 * d)
                    cubicTo(cx - 6 * d, cy - 2 * d, cx + 6 * d, cy - 2 * d, cx + 9 * d, cy + 2 * d)
                    lineTo(cx + 12 * d, cy + 12 * d)
                    close()
                }
                canvas.drawPath(shoulders, paint)
                canvas.drawLine(cx - 12 * d, cy + 12 * d, cx + 12 * d, cy + 12 * d, paint)
            }
            Kind.MESSAGE -> {
                canvas.drawRoundRect(cx - 14 * d, cy - 10 * d, cx + 14 * d, cy + 8 * d, 5 * d, 5 * d, paint)
                val tail = Path().apply {
                    moveTo(cx - 5 * d, cy + 8 * d)
                    lineTo(cx - 9 * d, cy + 14 * d)
                    lineTo(cx + 1 * d, cy + 8 * d)
                }
                canvas.drawPath(tail, paint)
                canvas.drawLine(cx - 7 * d, cy - 2 * d, cx + 7 * d, cy - 2 * d, paint)
                canvas.drawLine(cx - 7 * d, cy + 3 * d, cx + 3 * d, cy + 3 * d, paint)
            }
            Kind.HISTORY -> {
                canvas.drawCircle(cx, cy, 12 * d, paint)
                canvas.drawLine(cx, cy, cx, cy - 7 * d, paint)
                canvas.drawLine(cx, cy, cx + 6 * d, cy + 3 * d, paint)
                canvas.drawLine(cx - 14 * d, cy - 5 * d, cx - 14 * d, cy - 13 * d, paint)
                canvas.drawLine(cx - 14 * d, cy - 13 * d, cx - 7 * d, cy - 10 * d, paint)
            }
            Kind.REPORT -> {
                canvas.drawLine(cx - 12 * d, cy + 9 * d, cx - 12 * d, cy - 3 * d, paint)
                canvas.drawLine(cx, cy + 9 * d, cx, cy - 10 * d, paint)
                canvas.drawLine(cx + 12 * d, cy + 9 * d, cx + 12 * d, cy - 16 * d, paint)
                canvas.drawLine(cx - 15 * d, cy + 14 * d, cx + 15 * d, cy + 14 * d, paint)
            }
        }
    }
}