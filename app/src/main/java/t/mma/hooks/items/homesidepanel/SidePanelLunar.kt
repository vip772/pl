package t.mma.hooks.items.homesidepanel

import android.icu.util.ChineseCalendar
import android.icu.util.Calendar
import android.icu.util.TimeZone
import java.util.Date

/** ICU handles leap months and timezone boundaries without a finite year lookup table. */
internal object SidePanelLunar {
    fun today(now: Date = Date()): String {
        val calendar = ChineseCalendar(TimeZone.getDefault()).apply { time = now }
        val months = arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
        val numbers = arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val dayText = when (day) {
            10 -> "初十"
            20 -> "二十"
            30 -> "三十"
            else -> arrayOf("初", "十", "廿")[((day - 1) / 10)] + numbers[(day - 1) % 10]
        }
        val leap = if (calendar.get(ChineseCalendar.IS_LEAP_MONTH) == 1) "闰" else ""
        return "农历 $leap${months[calendar.get(Calendar.MONTH)]}月$dayText"
    }
}
