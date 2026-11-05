package t.mma.hooks.items.conversationanalysis

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Inclusive database bounds, computed from one local-clock snapshot. Weeks start Monday. */
internal object ConversationAnalysisPeriod {
    fun range(days: Int, now: Long = System.currentTimeMillis()): LongRange {
        if (days == 0 || days == 365) return 0L..now
        val start = midnight(now)
        val end = start.clone() as Calendar
        when (days) {
            1 -> end.add(Calendar.DAY_OF_YEAR, 1)
            -1 -> start.add(Calendar.DAY_OF_YEAR, -1)
            7, -7 -> {
                val offset = (start.get(Calendar.DAY_OF_WEEK) + 5) % 7
                start.add(Calendar.DAY_OF_YEAR, -offset)
                end.timeInMillis = start.timeInMillis
                if (days == -7) start.add(Calendar.DAY_OF_YEAR, -7)
                else end.add(Calendar.DAY_OF_YEAR, 7)
            }
            30, -30 -> {
                start.set(Calendar.DAY_OF_MONTH, 1)
                end.timeInMillis = start.timeInMillis
                if (days == -30) start.add(Calendar.MONTH, -1)
                else end.add(Calendar.MONTH, 1)
            }
            else -> return recentDays(days, now)
        }
        return start.timeInMillis..minOf(now, end.timeInMillis - 1)
    }

    /** The separate activity slider explicitly means rolling calendar days. */
    fun recentDays(days: Int, now: Long = System.currentTimeMillis()): LongRange {
        val start = midnight(now).apply { add(Calendar.DAY_OF_YEAR, -(days.coerceAtLeast(1) - 1)) }
        return start.timeInMillis..now
    }

    fun label(days: Int, pattern: String = "yyyy-MM-dd", now: Long = System.currentTimeMillis()): String {
        if (days == 0 || days == 365) return "全部历史消息"
        val bounds = range(days, now)
        val format = SimpleDateFormat(pattern, Locale.getDefault())
        return "${format.format(Date(bounds.first))} 至 ${format.format(Date(bounds.last))}"
    }

    private fun midnight(now: Long) = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

/** Page-local selection; no persistent cache or background work. */
internal class ConversationAnalysisPeriodSelection(initial: Int = 1) {
    var days: Int = initial
        private set
    private val observers = mutableListOf<(Int) -> Unit>()
    fun observe(observer: (Int) -> Unit) { observers.add(observer) }
    fun select(next: Int) {
        if (next == days) return
        days = next
        observers.forEach { it(next) }
    }
}
