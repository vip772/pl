package t.mma.hooks.items.chattoolbar

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** All geometry is prepared once in a 24-unit viewport. No bitmap or host icon. */
internal class ChatToolbarGlyph(id: String, color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 1.7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    init {
        fun line(vararg points: Float) {
            path.moveTo(points[0], points[1])
            for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
        }
        fun box(l: Float, t: Float, r: Float, b: Float, radius: Float = 2f) {
            path.addRoundRect(l, t, r, b, radius, radius, Path.Direction.CW)
        }
        fun circle(x: Float, y: Float, radius: Float) { path.addCircle(x, y, radius, Path.Direction.CW) }
        when (id) {
            "quick_reply" -> {
                line(5f,4f,19f,4f,21f,6f,21f,15f,19f,17f,9f,17f,4f,21f,4f,17f,3f,15f,3f,6f,5f,4f)
                line(7f,9f,17f,9f); line(7f,13f,14f,13f)
            }
            "album" -> {
                box(3f,3f,21f,21f); circle(8f,8f,1.5f)
                line(4f,18f,10f,12f,14f,16f,17f,12f,21f,16f)
            }
            "camera", "system_camera" -> {
                line(3f,7f,7f,7f,9f,4f,15f,4f,17f,7f,21f,7f,21f,20f,3f,20f,3f,7f)
                circle(12f,13f,4f)
                if (id == "system_camera") {
                    line(10f,13f,14f,13f); line(12f,11f,12f,15f)
                }
            }
            "video_call" -> {
                box(3f,6f,15f,18f)
                line(15f,10f,21f,7f,21f,17f,15f,14f)
            }
            "voice_call" -> {
                path.moveTo(5f,3f); path.lineTo(9f,3f); path.lineTo(11f,8f)
                path.lineTo(8f,10f); path.cubicTo(9f,13f,11f,15f,14f,16f)
                path.lineTo(16f,13f); path.lineTo(21f,15f); path.lineTo(21f,19f)
                path.cubicTo(20f,25f,1f,14f,3f,5f); path.close()
            }
            "location" -> {
                path.moveTo(12f,22f); path.cubicTo(9f,18f,4f,13f,4f,9f)
                path.cubicTo(4f,-1f,20f,-1f,20f,9f)
                path.cubicTo(20f,13f,15f,18f,12f,22f); path.close(); circle(12f,9f,3f)
            }
            "red_packet" -> {
                box(4f,2.5f,20f,21.5f)
                path.moveTo(4f,7f); path.quadTo(12f,15f,20f,7f)
                circle(12f,13f,2.3f); line(10f,18f,14f,18f)
            }
            "gift" -> {
                box(3f,8f,21f,12f,1f); box(5f,12f,19f,21f,1f); line(12f,8f,12f,21f)
                path.moveTo(12f,8f); path.cubicTo(0f,8f,7f,-3f,12f,8f)
                path.cubicTo(17f,-3f,24f,8f,12f,8f)
            }
            "transfer" -> {
                line(3f,7f,20f,7f,16f,3f); line(20f,7f,16f,11f)
                line(21f,17f,4f,17f,8f,21f); line(4f,17f,8f,13f)
            }
            "voice_input" -> {
                box(9f,2f,15f,15f,3f)
                path.moveTo(5f,11f); path.cubicTo(5f,22f,19f,22f,19f,11f)
                line(12f,19f,12f,22f); line(8f,22f,16f,22f)
            }
            "favorite" -> {
                line(12f,2f,15f,8.5f,22f,9.5f,17f,14.5f,18f,22f,12f,18.5f,6f,22f,7f,14.5f,2f,9.5f,9f,8.5f,12f,2f)
            }
            "solitaire" -> {
                box(3f,3f,11f,9f); box(13f,15f,21f,21f)
                line(7f,9f,7f,18f,13f,18f); line(11f,6f,17f,6f,17f,15f)
            }
            "contact_card" -> {
                box(2f,4f,22f,20f); circle(8f,10f,2.5f)
                path.moveTo(4f,17f); path.cubicTo(4f,12f,12f,12f,12f,17f)
                line(15f,9f,19f,9f); line(15f,13f,19f,13f)
            }
            "file" -> {
                line(5f,2f,14f,2f,20f,8f,20f,22f,5f,22f,5f,2f)
                line(14f,2f,14f,8f,20f,8f); line(9f,13f,16f,13f); line(9f,17f,16f,17f)
            }
            "music" -> {
                line(10f,17f,10f,5f,20f,3f,20f,15f)
                line(10f,9f,20f,7f)
                path.addOval(3f,16f,10f,21f,Path.Direction.CW)
                path.addOval(13f,14f,20f,19f,Path.Direction.CW)
            }
            else -> error("Unknown toolbar glyph: $id")
        }
    }
    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val scale = minOf(bounds.width(), bounds.height()) / 24f
        val save = canvas.save()
        canvas.translate(bounds.exactCenterX() - 12f * scale, bounds.exactCenterY() - 12f * scale)
        canvas.scale(scale, scale)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { if (paint.alpha != alpha) { paint.alpha = alpha; invalidateSelf() } }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Drawable opacity") override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = 24
    override fun getIntrinsicHeight() = 24
}
