package t.mma.hooks.items.chattoolbar

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/** Small, bounded conversion at panel snapshot time; never called by draw/scroll. */
internal object ChatToolbarIcon {
    private const val EDGE = 96

    fun normalize(resources: Resources, icon: Drawable): Drawable {
        val oldBounds = Rect(icon.bounds)
        val bitmap = Bitmap.createBitmap(EDGE, EDGE, Bitmap.Config.ARGB_8888)
        try {
            val width = icon.intrinsicWidth.coerceAtLeast(1)
            val height = icon.intrinsicHeight.coerceAtLeast(1)
            val scale = EDGE.toFloat() / maxOf(width, height)
            val w = (width * scale).toInt().coerceIn(1, EDGE)
            val h = (height * scale).toInt().coerceIn(1, EDGE)
            icon.setBounds((EDGE - w) / 2, (EDGE - h) / 2, (EDGE + w) / 2, (EDGE + h) / 2)
            icon.draw(Canvas(bitmap))
            val pixels = IntArray(EDGE * EDGE)
            bitmap.getPixels(pixels, 0, EDGE, 0, 0, EDGE, EDGE)
            val bounds = visibleBounds(pixels, EDGE, EDGE) ?: return icon
            val cropped = Bitmap.createBitmap(bitmap, bounds[0], bounds[1], bounds[2], bounds[3])
            return BitmapDrawable(resources, cropped)
        } finally {
            icon.bounds = oldBounds
            // Bitmap.createBitmap may return the input; keep it owned by the drawable.
        }
    }

    internal fun visibleBounds(pixels: IntArray, width: Int, height: Int): IntArray? {
        var left = width; var top = height; var right = -1; var bottom = -1
        for (y in 0 until height) for (x in 0 until width) {
            if ((pixels[y * width + x] ushr 24) <= 8) continue
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
        return if (right < left) null else intArrayOf(left, top, right - left + 1, bottom - top + 1)
    }
}
