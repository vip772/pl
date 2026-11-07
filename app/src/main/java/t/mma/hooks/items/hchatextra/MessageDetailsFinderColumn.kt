package t.mma.hooks.items.hchatextra

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/** Keeps time text within the native card width without changing its horizontal slot. */
internal class MessageDetailsFinderColumn private constructor(
    private val card: View,
    private val originalParams: LinearLayout.LayoutParams
) : LinearLayout(card.context) {
    init {
        orientation = VERTICAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // p7 owns the pixel width, including WeChat display configuration. The time
        // label must not enlarge this slot and displace the status area or avatar.
        val nativeWidth = card.layoutParams.width.takeIf { it > 0 }
            ?: card.measuredWidth.takeIf { it > 0 }
        val columnSpec = nativeWidth?.let {
            MeasureSpec.makeMeasureSpec(it, MeasureSpec.EXACTLY)
        } ?: widthMeasureSpec
        super.onMeasure(columnSpec, heightMeasureSpec)
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
