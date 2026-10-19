package t.mma.hooks.items.homeextension

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/** Validated rows: every card once, at most two per row. */
internal object HomeCardLayout {
    val ids = listOf("calendar", "contacts", "photo", "weather")
    fun rows(value: String?, fallback: List<String> = ids): List<List<String>> {
        val seen = HashSet<String>()
        val result = mutableListOf<List<String>>()
        value.orEmpty().split(';').forEach { row ->
            row.split(',').filter { it in ids && seen.add(it) }.chunked(2).forEach { result.add(it) }
        }
        fallback.filter { it in ids && seen.add(it) }.forEach { result.add(listOf(it)) }
        return result
    }
    fun encode(rows: List<List<String>>) = rows.joinToString(";") { it.joinToString(",") }
    fun move(rows: List<List<String>>, id: String, target: Int): List<List<String>> {
        val next = rows.map { it.filter { item -> item != id }.toMutableList() }.toMutableList()
        val row = target.coerceIn(0, next.size)
        if (row == next.size) next.add(mutableListOf(id))
        else if (next[row].size < 2) next[row].add(id)
        else next.add(row + 1, mutableListOf(id))
        return next.filter { it.isNotEmpty() }
    }
}

/** Measure naturally first, then stretch only the shorter card. No layout callbacks/loops. */
internal class HomeCardRow(context: Context) : LinearLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val target = measuredHeight - paddingTop - paddingBottom
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            val params = child.layoutParams as LayoutParams
            child.measure(MeasureSpec.makeMeasureSpec(child.measuredWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((target - params.topMargin - params.bottomMargin).coerceAtLeast(0), MeasureSpec.EXACTLY))
        }
    }
}