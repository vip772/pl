package t.mma.hooks.items.conversationanalysis

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class ConversationAnalysisAiReportView(context: Context) : LinearLayout(context) {
    private val body = LinearLayout(context)
    private var memberCount = 0
    private var speakerCount = 0
    private var sampleCount = 0
    private var reportTalker = ""
    private var reportDays = 1
    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(0), dp(0), dp(0), dp(0))
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(dp(0), dp(0), dp(0), dp(0))

        body.orientation = VERTICAL
        body.setPadding(dp(12), dp(8), dp(12), dp(8))
        addView(body, LayoutParams(-1, LayoutParams.WRAP_CONTENT))
    }

fun updateMeta(talker: String, days: Int, memberCount: Int, speakerCount: Int) {
        reportDays = days
        reportTalker = talker
        this.memberCount = memberCount
        this.speakerCount = speakerCount
    }
    fun setScreenshotMode(enabled: Boolean) = Unit
    fun getReportTalker(): String = reportTalker
    fun getReportDays(): Int = reportDays
    fun updateSampleCount(count: Int) { sampleCount = count }
    fun renderScreenshot(groupName: String, days: Int, directory: File): File {
        val imageWidth = 900
        val margin = (10f * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val contentPadding = 52
        val bodyTop = 316
        val footerHeight = 104
        val bodyWidth = imageWidth - (margin + contentPadding) * 2
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(36, 43, 55); textSize = 31f }
        var layout = screenshotLayout(currentText, paint, bodyWidth)
        while (layout.height + bodyTop + footerHeight > 7000 && paint.textSize > 20f) {
            paint.textSize -= 1f
            layout = screenshotLayout(currentText, paint, bodyWidth)
        }
        val imageHeight = maxOf(720, layout.height + bodyTop + footerHeight)
        check(imageHeight <= 7000) { "总结内容过长，无法生成单张截图" }
        val bitmap = Bitmap.createBitmap(imageWidth, imageHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val draw = Paint(Paint.ANTI_ALIAS_FLAG)
        draw.shader = LinearGradient(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat(), intArrayOf(Color.rgb(230, 239, 255), Color.rgb(224, 251, 247), Color.rgb(250, 247, 239), Color.rgb(255, 235, 241)), floatArrayOf(0f, .34f, .72f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat(), draw)
        draw.shader = null
        val card = RectF(margin.toFloat(), 276f, (imageWidth - margin).toFloat(), (imageHeight - 38).toFloat())
        draw.color = Color.argb(22, 54, 83, 110)
        canvas.drawRoundRect(RectF(card.left + 3, card.top + 8, card.right + 3, card.bottom + 10), 10f, 10f, draw)
        draw.color = Color.WHITE
        canvas.drawRoundRect(card, 10f, 10f, draw)
        draw.style = Paint.Style.STROKE; draw.strokeWidth = 3f; draw.color = Color.argb(110, 220, 45, 60)
        canvas.drawRoundRect(card, 10f, 10f, draw)
        draw.style = Paint.Style.FILL; draw.textAlign = Paint.Align.CENTER; draw.color = Color.rgb(34, 74, 121); draw.typeface = Typeface.DEFAULT_BOLD; draw.textSize = 50f
        canvas.drawText("群聊分析报告", imageWidth / 2f, 76f, draw)
        draw.color = Color.rgb(65, 100, 139); draw.textSize = 25f
        canvas.drawText(fitLine(dateRange(days), draw, imageWidth - 136f), imageWidth / 2f, 119f, draw)
        draw.color = Color.rgb(73, 91, 112); draw.typeface = Typeface.DEFAULT; draw.textSize = 22f
        canvas.drawText(fitLine("分析群聊：$groupName", draw, imageWidth - 136f), imageWidth / 2f, 158f, draw)
        draw.color = Color.rgb(34, 74, 121); draw.typeface = Typeface.DEFAULT_BOLD
        val stats = "群员总数 $memberCount 人   ·   发言人数 $speakerCount 人   ·   抽样消息 $sampleCount 条"
        canvas.drawText(fitLine(stats, draw, imageWidth - 136f), imageWidth / 2f, 198f, draw)
        draw.color = Color.rgb(105, 116, 130); draw.typeface = Typeface.DEFAULT; draw.textSize = 20f
        canvas.drawText("生成时间：" + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()), imageWidth / 2f, 236f, draw)
        canvas.save(); canvas.translate((margin + contentPadding).toFloat(), bodyTop.toFloat()); layout.draw(canvas); canvas.restore()
        draw.color = Color.rgb(121, 132, 146); draw.textSize = 20f; draw.textAlign = Paint.Align.RIGHT
        canvas.drawText("一字一字手搓的报告🖍", (imageWidth - margin - contentPadding).toFloat(), (imageHeight - 68).toFloat(), draw)
        directory.mkdirs()
        val file = File(directory, "summary_${System.currentTimeMillis()}.png")
        try { FileOutputStream(file).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } } finally { bitmap.recycle() }
        check(file.isFile && file.length() > 0) { "截图文件生成失败" }
        return file
    }
    private var currentText = ""
    private fun screenshotLayout(source: String, paint: TextPaint, width: Int): StaticLayout {
        val styled = SpannableStringBuilder(source)
        val lines = source.split("\\n")
        var offset = 0
        val red = Color.rgb(198, 40, 40)
        val numerals = "一二三四五六七八九十"
        lines.forEach { line ->
            val value = line.trim()
            val start = offset + line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val end = offset + line.length
            val numbered = value.length >= 2 && value[0] in numerals && value[1] in "、，"
            if (value.startsWith("群聊总结：") || numbered || value in setOf("重点人物与群像", "整体氛围", "有趣的点") || value.startsWith("结论：") || value.startsWith("💥")) {
                styled.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                styled.setSpan(ForegroundColorSpan(red), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (value.startsWith("内容概览：")) {
                styled.setSpan(StyleSpan(Typeface.BOLD), start, minOf(end, start + "内容概览：".length), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            offset += line.length + 1
        }
        return StaticLayout(styled, paint, width, Layout.Alignment.ALIGN_NORMAL, 1.28f, 0f, false)
    }
    private fun dateRange(days: Int): String {
        if (days == 0 || days >= 60) return "全部历史消息"
        val start = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -maxOf(0, days - 1))
        }
        val format = SimpleDateFormat("yyyy/MM/dd", Locale.CHINA)
        return "${format.format(start.time)} ~ ${format.format(Date())}"
    }
    private fun fitLine(value: String, paint: Paint, maxWidth: Float): String {
        var text = value
        if (paint.measureText(text) <= maxWidth) return text
        while (text.isNotEmpty() && paint.measureText(text + "…") > maxWidth) text = text.dropLast(1)
        return text + "…"
    }
    fun update(talker: String, days: Int, text: String) {
        reportDays = days
        if (reportTalker.isBlank()) reportTalker = talker
        currentText = text
        body.removeAllViews()
        renderStructured(text)
        requestLayout()
    }

    private fun renderStructured(source: String) {
        val text = cleanMemberIdentifiers(source)
        val styled = SpannableStringBuilder(text)
        val lines = text.split("\n")
        var offset = 0
        val red = Color.rgb(198, 40, 40)
        val numerals = "一二三四五六七八九十"
        lines.forEach { line ->
            val value = line.trim()
            val numberedTitle = value.length >= 2 && value[0] in numerals && value[1] in "、，"
            val sectionTitle = numberedTitle || value in setOf("重点人物与群像", "整体氛围", "有趣的点")
            if (value.startsWith("群聊总结：") || sectionTitle || value.startsWith("结论：")) {
                styled.setSpan(StyleSpan(Typeface.BOLD), offset, offset + line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                styled.setSpan(ForegroundColorSpan(red), offset, offset + line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (value.startsWith("💥")) {
                styled.setSpan(StyleSpan(Typeface.BOLD), offset, offset + line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                styled.setSpan(ForegroundColorSpan(red), offset, offset + line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (value.startsWith("内容概览：")) {
                val end = offset + line.indexOf("：") + 1
                styled.setSpan(StyleSpan(Typeface.BOLD), offset, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            offset += line.length + 1
        }
        body.removeAllViews()
        body.addView(TextView(context).apply {
            setText(styled)
            textSize = 10.8f
            setTextColor(Color.rgb(36, 43, 55))
            setLineSpacing(0f, 1.28f)
            gravity = Gravity.LEFT or Gravity.TOP
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }, LayoutParams(-1, LayoutParams.WRAP_CONTENT))
    }

    private fun cleanMemberIdentifiers(text: String): String {
        return text.replace(Regex("wxid_[A-Za-z0-9_-]+"), "群成员")
            .replace(Regex("[A-Za-z0-9._-]+@(chatroom|im\\.chatroom)"), "群聊")
            .replace(Regex("\\b(?:微信号|微信ID|群聊ID)[:：]\\s*[A-Za-z0-9._@-]+"), "群成员")
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}