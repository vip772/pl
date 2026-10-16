package t.mma.hooks.items.homesidepanel

import java.util.Calendar

/** Monday-first month grid. Empty cells are alignment only, not selectable dates. */
internal data class SidePanelCalendarMonth(
    val year: Int,
    val month: Int,
    val today: Int,
    val cells: List<Int?>
) {
    companion object {
        fun from(now: Calendar): SidePanelCalendarMonth {
            val first = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
            val offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
            val count = ((offset + days + 6) / 7) * 7
            return SidePanelCalendarMonth(
                first.get(Calendar.YEAR), first.get(Calendar.MONTH) + 1,
                now.get(Calendar.DAY_OF_MONTH),
                List(count) { index -> (index - offset + 1).takeIf { it in 1..days } }
            )
        }
    }
}
