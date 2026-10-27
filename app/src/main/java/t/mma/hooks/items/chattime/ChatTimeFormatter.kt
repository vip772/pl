package t.mma.hooks.items.chattime

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One formatter per runtime, guarded because SimpleDateFormat is mutable. */
internal class ChatTimeFormatter {
    private var key: List<Any>? = null
    private var formatter: SimpleDateFormat? = null
    private val date = Date()
    @Synchronized fun format(timestamp: Long, pattern: String, fallback: String): String {
        val locale = Locale.getDefault()
        val zone = TimeZone.getDefault()
        val nextKey = listOf(pattern, fallback, locale, zone.id)
        if (key != nextKey || formatter?.timeZone?.hasSameRules(zone) != true) {
            formatter = runCatching { SimpleDateFormat(pattern, locale) }
                .getOrElse { SimpleDateFormat(fallback, locale) }.apply { timeZone = zone }
            key = nextKey
        }
        date.time = timestamp
        return requireNotNull(formatter).format(date)
    }
}