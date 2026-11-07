package t.mma.hooks.items.hchatextra

import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Measures the time independently while preserving the native card size and edge. */
internal class MessageDetailsFinderColumn private constructor(
    private val card: View,
    private val originalParams: LinearLayout.LayoutParams
) : LinearLayout(card.context) {
    private var timeLabel: TextView? = null
    private var timeWidthLimit = 0

    init {
        orientation = VERTICAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // p7 owns the card width. Only the surrounding column may grow for single-line
        // time; both children retain the same incoming/outgoing edge.
        val nativeWidth = card.layoutParams.width.takeIf { it > 0 }
            ?: card.measuredWidth.takeIf { it > 0 }
        val label = timeLabel?.takeIf { it.parent === this }
        var timeWidth = 0
        if (label != null && timeWidthLimit > 0) {
            label.measure(MeasureSpec.makeMeasureSpec(timeWidthLimit, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            timeWidth = label.measuredWidth
        }
        val columnWidth = maxOf(nativeWidth ?: 0, timeWidth)
        val columnSpec = if (columnWidth > 0)
            MeasureSpec.makeMeasureSpec(columnWidth, MeasureSpec.EXACTLY) else widthMeasureSpec
        super.onMeasure(columnSpec, heightMeasureSpec)
    }

    /** The time uses row space without inheriting the avatar offset a second time. */
    fun timeLayoutParams(label: TextView, isSelf: Boolean, topMargin: Int, availableWidth: Int): LayoutParams {
        timeLabel = label
        timeWidthLimit = availableWidth.coerceAtLeast(0)
        label.setSingleLine(true)
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        val edge = if (isSelf) Gravity.END else Gravity.START
        (card.layoutParams as? LayoutParams)?.let {
            if (it.gravity != edge) { it.gravity = edge; card.layoutParams = it }
        }
        label.gravity = edge
        label.textAlignment = if (isSelf) View.TEXT_ALIGNMENT_TEXT_END else View.TEXT_ALIGNMENT_TEXT_START
        label.setPadding(0, label.paddingTop, 0, label.paddingBottom)
        return LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = edge
            this.topMargin = topMargin
        }
    }

    companion object {
        fun wrap(card: View): MessageDetailsFinderColumn? {
            (card.parent as? MessageDetailsFinderColumn)?.let { return it }
            val parent = card.parent as? LinearLayout ?: return null
            val params = card.layoutParams as? LinearLayout.LayoutParams ?: return null
            // Keep native margins/gravity/weight on the original card slot.
            val index = parent.indexOfChild(card)
            if (index < 0) return null
            val column = MessageDetailsFinderColumn(card, params)
            parent.removeViewAt(index)
            column.addView(card, LinearLayout.LayoutParams(params.width, params.height))
            parent.addView(column, index, LinearLayout.LayoutParams(params).apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            })
            return column
        }

        fun restore(parent: ViewGroup?) {
            val column = parent as? MessageDetailsFinderColumn ?: return
            if (column.childCount != 1 || column.getChildAt(0) !== column.card) return
            val host = column.parent as? LinearLayout ?: return
            val index = host.indexOfChild(column)
            if (index < 0) return
            val current = column.card.layoutParams
            column.originalParams.width = current.width
            column.originalParams.height = current.height
            column.removeView(column.card)
            host.removeViewAt(index)
            host.addView(column.card, index, column.originalParams)
        }
    }
}
