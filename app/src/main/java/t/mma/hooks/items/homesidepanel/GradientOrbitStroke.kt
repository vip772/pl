package t.mma.hooks.items.homesidepanel

import android.graphics.*

/** Shared perimeter/trailing highlight renderer. The host owns frame scheduling and cached geometry. */
internal object GradientOrbitStroke {
    fun draw(canvas: Canvas, outline: Path, trail: Path, measure: PathMeasure,
             paint: Paint, perimeter: Float, phase: Float, stroke: Float, density: Float) {
        paint.strokeWidth = stroke
        paint.alpha = 220
        canvas.drawPath(outline, paint)
        trail.reset()
        val start = phase * perimeter
        val end = start + perimeter * .38f
        measure.getSegment(start, minOf(end, perimeter), trail, true)
        if (end > perimeter) measure.getSegment(0f, end - perimeter, trail, true)
        paint.strokeWidth = stroke + 3f * density
        paint.alpha = 25
        canvas.drawPath(trail, paint)
        paint.strokeWidth = stroke + 1.5f * density
        paint.alpha = 65
        canvas.drawPath(trail, paint)
        paint.strokeWidth = stroke
        paint.alpha = 255
        canvas.drawPath(trail, paint)
    }
}
