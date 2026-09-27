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
    private val reportTitle = TextView(context)
    private val reportFooter = TextView(context)
    private val meta = TextView(context)
    private var memberCount = 0
    private var speakerCount = 0
    private var sampleCount = 0
    private var reportTalker = ""

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(0), dp(0), dp(0), dp(0))
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(dp(0), dp(0), dp(0), dp(0))

        reportTitle.apply {
            text = "群聊分析报告"
            textSize = 20f
            setTextColor(Color.rgb(45, 74, 106))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        addView(reportTitle, LayoutParams(-1, dp(34)))
        meta.textSize = 8f
        meta.setTextColor(Color.rgb(83, 91, 105))
        meta.gravity = Gravity.CENTER
        addView(meta, LayoutParams(-1, dp(42)))

        body.orientation = VERTICAL
        body.setPadding(dp(12), dp(8), dp(12), dp(8))
        body.background = null
        addView(body, LayoutParams(-1, LayoutParams.WRAP_CONTENT))
        reportFooter.apply {
            text = "—— 群聊分析助手"
            textSize = 8f
            setTextColor(Color.rgb(135, 135, 145))
            gravity = Gravity.RIGHT
            setPadding(0, dp(6), dp(4), 0)
        }
        addView(reportFooter, LayoutParams(-1, dp(22)))
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

    fun setScreenshotMode(enabled: Boolean) {
        setBackgroundColor(if (enabled) Color.rgb(226, 240, 250) else Color.TRANSPARENT)
        setPadding(if (enabled) dp(10) else dp(0), if (enabled) dp(12) else dp(0), if (enabled) dp(10) else dp(0), if (enabled) dp(12) else dp(0))
        reportTitle.visibility = if (enabled) VISIBLE else GONE
        meta.visibility = if (enabled) VISIBLE else GONE
        reportFooter.visibility = if (enabled) VISIBLE else GONE
        body.background = if (enabled) GradientDrawable().apply {
            setColor(Color.rgb(255, 252, 248))
            setStroke(dp(1), Color.rgb(224, 137, 159))
            cornerRadius = dp(8).toFloat()
        } else null
        requestLayout()
    }

    fun update(talker: String, days: Int, text: String) {
        if (reportTalker.isBlank()) reportTalker = talker
        refreshMeta()
        body.removeAllViews()
        renderStructured(text)
        requestLayout()
    }

    private fun renderStructured(source: String) {
        // Some models omit line breaks between a heading, its paragraph and the next heading.
        // Insert boundaries before known report markers so the visual hierarchy remains stable.
        val normalized = source.replace("\r", "")
            .replace(Regex("\\s+(?=(?:一|二|三|四|五|六|七|八|九|十)、)"), "\n")
            .replace(Regex("\\s+(?=结论：)"), "\n")
            .replace(Regex("\\s+(?=(?:重点人物与群像|整体氛围|有趣的点|内容概览|群聊总结)[：:]?)"), "\n")
            .replace(Regex("\\s+(?=\\d+[、.]\\s*)"), "\n")
        normalized.split("\n").forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) {
                addGap(6)
            } else {
                renderBlock(line)
            }
        }
    }

    private fun renderBlock(line: String) {
        val sectionMatch = Regex("^([一二三四五六七八九十]+、[^：:。]+)(?:[：:]?)(.*)$").matchEntire(line)
        if (sectionMatch != null) {
            addSection(sectionMatch.groupValues[1].trim())
            val remainder = sectionMatch.groupValues[2].trim()
            if (remainder.isNotBlank()) renderParagraph(remainder)
            return
        }
        val namedSection = listOf("群聊总结", "内容概览", "重点人物与群像", "整体氛围", "有趣的点", "主题章节", "总结")
            .firstOrNull { line == it || line.startsWith("$it：") || line.startsWith("$it:") }
        if (namedSection != null) {
            addSection(namedSection)
            val remainder = line.removePrefix(namedSection).removePrefix("：").removePrefix(":").trim()
            if (remainder.isNotBlank()) renderParagraph(remainder)
            return
        }
        if (line.startsWith("结论：") || line.startsWith("结论:")) {
            addConclusion(line)
        } else if (line.startsWith("💥") || line.startsWith("◆") || line.startsWith("❖") || line.startsWith("◇") || line.startsWith("关键词：") || line.startsWith("关键词:")) {
            addQuote(line)
        } else if (line.matches(Regex("^\\d+[、.]\\s*[^：:]+(?:：|:).*$"))) {
            val split = line.indexOfAny(charArrayOf('：', ':'))
            addSection(line.substring(0, split).trim())
            renderParagraph(line.substring(split + 1).trim())
        } else {
            renderParagraph(line)
        }
    }

    private fun addSection(text: String) {
        addText(text, 13f, Color.rgb(176, 46, 65), Typeface.DEFAULT_BOLD, dp(10), dp(3), 1.15f)
    }

    private fun addConclusion(text: String) {
        addText(text, 11.5f, Color.rgb(176, 46, 65), Typeface.DEFAULT_BOLD, dp(4), dp(5), 1.2f)
    }

    private fun addQuote(text: String) {
        addText(text, 10.5f, Color.rgb(176, 46, 65), Typeface.DEFAULT_BOLD, dp(4), dp(3), 1.2f)
    }

    private fun renderParagraph(text: String) {
        addText(text, 10.8f, Color.rgb(48, 45, 48), Typeface.DEFAULT, dp(2), dp(3), 1.22f)
    }

    private fun addGap(height: Int) {
        body.addView(TextView(context), LayoutParams(1, dp(height)))
    }

    private fun cleanMemberIdentifiers(text: String): String {
        return text.replace(Regex("wxid_[A-Za-z0-9_-]+"), "群成员")
            .replace(Regex("[A-Za-z0-9._-]+@(chatroom|im\\.chatroom)"), "群聊")
            .replace(Regex("\\b(?:微信号|微信ID|群聊ID)[:：]\\s*[A-Za-z0-9._@-]+"), "群成员")
    }

    private fun addText(text: String, size: Float, color: Int, face: Typeface, top: Int, bottom: Int, spacing: Float) {
        body.addView(TextView(context).apply {
            this.text = cleanMemberIdentifiers(text)
            textSize = size
            setTextColor(color)
            typeface = face
            setLineSpacing(0f, spacing)
            gravity = Gravity.LEFT
            setPadding(0, top, 0, bottom)
        }, LayoutParams(-1, LayoutParams.WRAP_CONTENT))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}