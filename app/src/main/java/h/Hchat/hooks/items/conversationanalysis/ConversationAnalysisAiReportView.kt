package h.Hchat.hooks.items.conversationanalysis

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class ConversationAnalysisAiReportView(context: Context) : LinearLayout(context) {
    private val body = TextView(context)
    init {
        orientation = VERTICAL
        setPadding(dp(24), dp(22), dp(24), dp(22))
        setBackgroundColor(Color.rgb(247, 249, 248))
        val title = TextView(context).apply {
            text = "群聊分析 · AI 总结"
            textSize = 26f
            setTextColor(Color.rgb(28, 44, 39))
            typeface = Typeface.DEFAULT_BOLD
        }
        addView(title, LayoutParams(-1, dp(42)))
        addView(TextView(context).apply {
            textSize = 14f
            setTextColor(Color.rgb(38, 150, 92))
            tag = "meta"
        }, LayoutParams(-1, dp(42)))
        body.textSize = 16f
        body.setTextColor(Color.rgb(45, 48, 47))
        body.setLineSpacing(0f, 1.35f)
        body.setPadding(dp(16), dp(16), dp(16), dp(16))
        body.background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(10).toFloat() }
        addView(body, LayoutParams(-1, LayoutParams.WRAP_CONTENT))
    }
    fun update(talker: String, days: Int, text: String) {
        val meta = getChildAt(1) as TextView
        meta.text = "群聊：$talker\n周期：最近 $days 天    生成：${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}"
        body.text = text
        requestLayout()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}