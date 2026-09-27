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
    private val body = LinearLayout(context)
    private val meta = TextView(context)
    private var memberCount = 0
    private var speakerCount = 0
    private var sampleCount = 0
    private var reportTalker = ""

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(0), dp(0), dp(0), dp(0))
        setBackgroundColor(Color.rgb(226, 240, 250))
        setPadding(dp(10), dp(12), dp(10), dp(12))

        addView(TextView(context).apply {
            text = "群聊分析报告"
            textSize = 20f
            setTextColor(Color.rgb(45, 74, 106))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }, LayoutParams(-1, dp(34)))
        meta.textSize = 8f
        meta.setTextColor(Color.rgb(83, 91, 105))
        meta.gravity = Gravity.CENTER
        addView(meta, LayoutParams(-1, dp(42)))

        body.orientation = VERTICAL
        body.setPadding(dp(12), dp(8), dp(12), dp(8))
        body.background = GradientDrawable().apply {
            setColor(Color.rgb(255, 252, 248))
            setStroke(dp(1), Color.rgb(224, 137, 159))
            cornerRadius = dp(8).toFloat()
        }
        addView(body, LayoutParams(-1, LayoutParams.WRAP_CONTENT))
        addView(TextView(context).apply {
            text = "—— 群聊分析助手"
            textSize = 8f
            setTextColor(Color.rgb(135, 135, 145))
            gravity = Gravity.RIGHT
            setPadding(0, dp(6), dp(4), 0)
        }, LayoutParams(-1, dp(22)))
    }

    fun updateMeta(talker: String, memberCount: Int, speakerCount: Int, sampleCount: Int) {
        reportTalker = talker
        this.memberCount = memberCount
        this.speakerCount = speakerCount
        this.sampleCount = sampleCount
        refreshMeta()
    }

    private fun refreshMeta() {
        meta.text = "$reportTalker\n群员总数：$memberCount    发言人数：$speakerCount    抽样消息：$sampleCount\n生成时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}"
    }

    fun update(talker: String, days: Int, text: String) {
        if (reportTalker.isBlank()) reportTalker = talker
        refreshMeta()
        body.removeAllViews()
        text.replace("\r", "").split("\n").forEach(::addLine)
        requestLayout()
    }

    private fun addLine(raw: String) {
        val line = raw.trim()
        if (line.isBlank()) {
            body.addView(TextView(context), LayoutParams(1, dp(5)))
            return
        }
        val isSection = line.matches(Regex("^[一二三四五六七八九十]+、.*")) || line in listOf("整体氛围", "重点人物与群体画像", "有趣的点", "主题/话题", "总结")
        val isConclusion = line.startsWith("结论：") || line.startsWith("总结：")
        val isQuote = line.startsWith("◆") || line.startsWith("❖") || line.startsWith("◇")
        body.addView(TextView(context).apply {
            text = line
            textSize = when { isSection -> 13f; isConclusion -> 11f; isQuote -> 10f; else -> 10.5f }
            setTextColor(if (isSection || isConclusion || isQuote) Color.rgb(176, 46, 65) else Color.rgb(48, 45, 48))
            typeface = if (isSection || isConclusion) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setLineSpacing(0f, 1.12f)
            gravity = Gravity.LEFT
            setPadding(0, if (isSection) dp(5) else dp(1), 0, if (isSection) dp(2) else dp(1))
        }, LayoutParams(-1, LayoutParams.WRAP_CONTENT))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}