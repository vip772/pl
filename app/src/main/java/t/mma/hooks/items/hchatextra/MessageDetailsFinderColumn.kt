package t.mma.hooks.items.hchatextra

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/** Temporary send-side column; only owns the verified Finder card and injected time label. */
internal class MessageDetailsFinderColumn private constructor(
    private val card: View,
    private val originalParams: LinearLayout.LayoutParams
) : LinearLayout(card.context) {
    init {
        orientation = VERTICAL
    }

    companion object {
        fun wrap(card: View): MessageDetailsFinderColumn? {
            val parent = card.parent as? LinearLayout ?: return null
            if (parent.orientation != HORIZONTAL) return null
            val params = card.layoutParams as? LinearLayout.LayoutParams ?: return null
            // Native p7 updates the card width itself. Keep those dimensions on the card;
            // outer margins/gravity/weight belong to its original horizontal slot.
            val index = parent.indexOfChild(card)
            if (index < 0) return null
            val column = MessageDetailsFinderColumn(card, params)
            parent.removeViewAt(index)
            column.addView(card, LinearLayout.LayoutParams(params.width, params.height))
            parent.addView(column, index, LinearLayout.LayoutParams(params).apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
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
