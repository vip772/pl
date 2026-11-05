package t.mma.hooks.items.conversationanalysis

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.min

/** Font-independent, 24-unit line icons. Reuses paint/path across redraws. */
internal class ConversationAnalysisIconPainter(private val kind: ConversationAnalysisIconView.Kind) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.8f
    }
    private val path = Path()

    fun draw(canvas: Canvas, left: Float, top: Float, width: Float, height: Float, color: Int,
             alpha: Int = 255, filter: android.graphics.ColorFilter? = null) {
        if (width <= 0f || height <= 0f) return
        val size = min(width, height)
        val checkpoint = canvas.save()
        try {
            canvas.translate(left + (width - size) / 2f, top + (height - size) / 2f)
            canvas.scale(size / 24f, size / 24f)
            paint.color = color
            paint.alpha = alpha
            paint.colorFilter = filter
            path.reset()
            when (kind) {
                ConversationAnalysisIconView.Kind.PEOPLE -> {
                    canvas.drawCircle(12f, 7f, 3.5f, paint)
                    path.moveTo(4f, 21f); path.lineTo(5f, 17f)
                    path.cubicTo(6f, 11f, 18f, 11f, 19f, 17f)
                    path.lineTo(20f, 21f); path.close()
                }
                ConversationAnalysisIconView.Kind.MESSAGE -> {
                    canvas.drawRoundRect(3f, 4f, 21f, 17f, 3f, 3f, paint)
                    path.moveTo(8f, 17f); path.lineTo(6f, 21f); path.lineTo(13f, 17f)
                    canvas.drawLine(7f, 9f, 17f, 9f, paint)
                    canvas.drawLine(7f, 13f, 14f, 13f, paint)
                }
                ConversationAnalysisIconView.Kind.HISTORY -> {
                    canvas.drawArc(4f, 4f, 21f, 21f, -140f, 320f, false, paint)
                    path.moveTo(3f, 4f); path.lineTo(3f, 9f); path.lineTo(8f, 9f)
                    canvas.drawLine(12f, 7f, 12f, 13f, paint)
                    canvas.drawLine(12f, 13f, 16f, 15f, paint)
                }
                ConversationAnalysisIconView.Kind.REPORT -> {
                    path.moveTo(4f, 20f); path.lineTo(4f, 4f)
                    path.moveTo(4f, 20f); path.lineTo(21f, 20f)
                    canvas.drawLine(8f, 16f, 8f, 12f, paint)
                    canvas.drawLine(13f, 16f, 13f, 9f, paint)
                    canvas.drawLine(18f, 16f, 18f, 5f, paint)
                }
                ConversationAnalysisIconView.Kind.LIST -> {
                    canvas.drawRoundRect(4f, 3f, 20f, 21f, 2f, 2f, paint)
                    for (y in floatArrayOf(8f, 12f, 16f)) canvas.drawLine(7f, y, 17f, y, paint)
                }
                ConversationAnalysisIconView.Kind.SETTINGS -> {
                    canvas.drawCircle(12f, 12f, 6.5f, paint)
                    canvas.drawCircle(12f, 12f, 2.5f, paint)
                    for (i in 0 until 8) {
                        val tooth = canvas.save()
                        canvas.rotate(i * 45f, 12f, 12f)
                        canvas.drawLine(12f, 3f, 12f, 5.5f, paint)
                        canvas.restoreToCount(tooth)
                    }
                }
                ConversationAnalysisIconView.Kind.SPARKLE -> {
                    path.moveTo(12f, 3f); path.lineTo(15f, 9f); path.lineTo(21f, 12f)
                    path.lineTo(15f, 15f); path.lineTo(12f, 21f); path.lineTo(9f, 15f)
                    path.lineTo(3f, 12f); path.lineTo(9f, 9f); path.close()
                }
                ConversationAnalysisIconView.Kind.CHEVRON_UP -> {
                    path.moveTo(7f, 14f); path.lineTo(12f, 9f); path.lineTo(17f, 14f)
                }
                ConversationAnalysisIconView.Kind.CHEVRON_DOWN -> {
                    path.moveTo(7f, 10f); path.lineTo(12f, 15f); path.lineTo(17f, 10f)
                }
            }
            canvas.drawPath(path, paint)
        } finally {
            canvas.restoreToCount(checkpoint)
        }
    }
}
