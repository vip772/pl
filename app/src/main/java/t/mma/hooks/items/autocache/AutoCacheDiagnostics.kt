package t.mma.hooks.items.autocache

import android.content.Context
import t.mma.preferences.HchatStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 独立保留少量下载阶段，避免被高频界面日志覆盖；不记录正文、密钥或文件内容。 */
object AutoCacheDiagnostics {
    @Synchronized
    fun record(context: Context, message: String) {
        val prefs = HchatStorage.preferences(context, "Hchat_auto_cache_diagnostics")
        val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        val lines = (prefs.getString("recent", "").orEmpty().lineSequence().filter { it.isNotBlank() }.toList() +
            "$time ${message.take(1500)}").takeLast(80)
        prefs.edit().putString("recent", lines.joinToString("\n")).apply()
    }

    fun recent(context: Context): String = HchatStorage.preferences(context, "Hchat_auto_cache_diagnostics")
        .getString("recent", "暂无自动缓存记录").orEmpty()
}