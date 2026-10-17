package t.mma.hooks.items.homesidepanel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Fixed outlined gear geometry; avoids device-dependent emoji/font rendering. */
internal class SidePanelSettingsIcon(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val gear = Path().apply {
        // Eight flat-ended teeth, with a recessed shoulder between adjacent teeth.
        val radii = floatArrayOf(9f, 11f, 11f, 9f, 8f, 8f)
        for (tooth in 0 until 8) for (point in radii.indices) {
            val angle = Math.toRadians(tooth * 45.0 + doubleArrayOf(-17.0,-12.0,12.0,17.0,19.0,26.0)[point] - 90.0)
            val x = cos(angle).toFloat() * radii[point]
            val y = sin(angle).toFloat() * radii[point]
            if (tooth == 0 && point == 0) moveTo(x,y) else lineTo(x,y)
        }
        close()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.color = if ((resources.configuration.uiMode and 0x30) == 0x20) Color.rgb(242,244,242) else Color.rgb(28,31,28)
        val saved = canvas.save()
        canvas.translate(width / 2f,height / 2f)
        val scale = resources.displayMetrics.density
        canvas.scale(scale,scale)
        paint.strokeWidth = 1.65f
        canvas.drawPath(gear,paint)
        canvas.drawCircle(0f,0f,3.6f,paint)
        canvas.restoreToCount(saved)
    }
}