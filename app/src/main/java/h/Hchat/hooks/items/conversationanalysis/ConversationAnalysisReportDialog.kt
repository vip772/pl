package h.Hchat.hooks.items.conversationanalysis

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Space
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.LinkedHashMap
import java.util.concurrent.Executors
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.Toast
import android.view.ViewGroup
import h.Hchat.hooks.api.core.WeChatApis
import java.io.File

internal object ConversationAnalysisReportDialog {
    private var generatedReportView: ConversationAnalysisAiReportView? = null
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Hchat-ConversationAnalysis").apply { isDaemon = true }
    }
    fun show(activity: Activity, talker: String, logger: (String, Throwable?) -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        val dialog = Dialog(activity, android.R.style.Theme_Material_Light_NoActionBar)
        var days = 1
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 22), dp(activity, 18), dp(activity, 40))
            setBackgroundColor(pageColor(activity))
        }
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val page = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(page)
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val titles = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label(activity, "分析报告", 26f, textColor(activity), true), LinearLayout.LayoutParams(-1, dp(activity, 43)))
        val range = label(activity, rangeText(days), 18f, 0xfff57c00.toInt(), true)
        titles.addView(range, LinearLayout.LayoutParams(-1, dp(activity, 34)))
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        val topSampling = label(activity, "▤", 27f, 0xffff7a00.toInt(), true).apply { gravity = Gravity.CENTER; setOnClickListener { showTopSamplingDialog(activity) } }
        val topTools = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        topTools.addView(topSampling, LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)))
        header.addView(topTools, LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 48)))
        page.addView(header)
        page.addView(section(activity, "核心指标"), marginParams(activity, -1, 48, 0, 20, 0, 0))
        val metrics = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(cardColor(activity), dp(activity, 28))
            setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 16))
        }
        val metricRows = listOf(
            metricCard(activity, ConversationAnalysisIconView.Kind.PEOPLE, "0", "今日发言人数"),
            metricCard(activity, ConversationAnalysisIconView.Kind.MESSAGE, "0", "今日消息数"),
            metricCard(activity, ConversationAnalysisIconView.Kind.HISTORY, "0", "历史消息总数")
        )
        metricRows.forEach { metrics.addView(it, LinearLayout.LayoutParams(0, dp(activity, 112), 1f)) }
        page.addView(metrics, marginParams(activity, -1, 0, 0, 18, 0, 0))

        page.addView(section(activity, "智能洞察"), marginParams(activity, -1, 48, 0, 12, 0, 0))
        val insightContent = buildInsight(activity, dialog, talker, days, logger)
        addCollapsible(activity, page, "✦", "智能总结消息", insightContent, false, true)
        page.addView(section(activity, "深度图表"), marginParams(activity, -1, 48, 0, 12, 0, 0))
        val deepRoot = buildDeepCharts(activity, dialog, talker, days, metrics, range, logger)
        page.addView(deepRoot)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener {
            dialog.window?.setLayout(-1, -1)
            dialog.window?.setStatusBarColor(pageColor(activity))
            dialog.window?.setNavigationBarColor(pageColor(activity))
            load(activity, dialog, metrics, talker, days, logger)
        }
        dialog.show()
    }

    private fun load(activity: Activity, dialog: Dialog, metrics: LinearLayout, talker: String, days: Int, logger: (String, Throwable?) -> Unit) {
        executor.execute {
            val result = runCatching { ConversationAnalysisRepository().loadSummary(talker, days) }.onFailure { logger("群聊分析报告读取失败", it) }.getOrElse { error -> ConversationAnalysisSummary(talker, 0, 0, 0, 0, 0, 0, 0, 0, error.message) }
            Handler(Looper.getMainLooper()).post {
                if (!dialog.isShowing || result.error != null) return@post
                updateMetric(metrics, 0, result.periodSpeakers.toString())
                updateMetric(metrics, 1, result.periodTotal.toString())
                updateMetric(metrics, 2, result.historyTotal.toString())
            }
        }
    }

    private fun updateMetric(parent: LinearLayout, index: Int, value: String) {
        val card = parent.getChildAt(index) as? LinearLayout ?: return
        (card.getChildAt(1) as? TextView)?.text = value
    }

    private fun label(activity: Activity, text: String, size: Float, color: Int, bold: Boolean) = TextView(activity).apply {
        this.text = text; textSize = (size - 5f).coerceAtLeast(10f); setTextColor(if (isDark(activity) && (color == 0xff1e1c21.toInt() || color == 0xff292522.toInt() || color == 0xff302b28.toInt() || color == 0xff4e4b54.toInt() || color == 0xff6f6862.toInt() || color == 0xff77716d.toInt() || color == 0xff5d5148.toInt())) textColor(activity) else color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun section(activity: Activity, text: String) = label(activity, text, 20f, textColor(activity), true)

    private fun metricCard(activity: Activity, icon: ConversationAnalysisIconView.Kind, value: String, caption: String) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(activity, 4), 0, dp(activity, 4), 0)
        addView(ConversationAnalysisIconView(activity, icon).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(activity, 44)))
        addView(label(activity, value, 32f, textColor(activity), true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(activity, 38)))
        addView(label(activity, caption, 16f, if (isDark(activity)) 0xffc0bac5.toInt() else 0xff77716d.toInt(), false).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, dp(activity, 28)))
    }

    private fun actionRow(activity: Activity, icon: String, title: String) = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(activity, 17), 0, dp(activity, 14), 0); background = rounded(0xfffffdf9.toInt(), dp(activity, 20))
        addView(label(activity, icon, 23f, 0xffd86522.toInt(), false), LinearLayout.LayoutParams(dp(activity, 42), -1))
        addView(label(activity, title, 16f, textColor(activity), true), LinearLayout.LayoutParams(0, -1, 1f))
        addView(label(activity, "›", 28f, 0xffaaa29b.toInt(), false), LinearLayout.LayoutParams(dp(activity, 25), -1))
    }

    private fun marginParams(activity: Activity, width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int) = LinearLayout.LayoutParams(width, if (height == 0) -2 else dp(activity, height)).apply { setMargins(dp(activity, left), dp(activity, top), dp(activity, right), dp(activity, bottom)) }


    private fun addCollapsible(activity: Activity, parent: LinearLayout, iconText: String, titleText: String, body: View, open: Boolean, insight: Boolean = false) {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = if (insight) strokeRounded(pageColor(activity), 0xffe69150.toInt(), dp(activity, 28), dp(activity, 2)) else rounded(cardColor(activity), dp(activity, 28))
            setPadding(if (insight) dp(activity, 16) else 0, if (insight) dp(activity, 12) else 0, if (insight) dp(activity, 16) else 0, if (insight) dp(activity, 14) else 0)
        }
        var arrowRef: TextView? = null
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; setOnClickListener {
            val expanded = body.visibility != View.VISIBLE
            body.visibility = if (expanded) View.VISIBLE else View.GONE
            arrowRef?.text = if (expanded) "⌃" else "⌄"
        } }
        val icon = label(activity, iconText, 18f, 0xfff57c00.toInt(), true).apply { gravity = Gravity.CENTER; background = rounded(0xffffe0bd.toInt(), dp(activity, 16)) }
        header.addView(icon, LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)))
        val title = label(activity, titleText, 20f, textColor(activity), true).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(activity, 12), 0, 0, 0) }
        header.addView(title, LinearLayout.LayoutParams(0, dp(activity, 48), 1f))
        val arrow = label(activity, if (open) "⌃" else "⌄", 25f, 0xffbdbdbd.toInt(), true).apply { gravity = Gravity.CENTER; arrowRef = this }
        header.addView(arrow, LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 48)))
        body.visibility = if (open) View.VISIBLE else View.GONE
        card.addView(header)
        card.addView(body)
        parent.addView(card, marginParams(activity, -1, 0, 0, 8, 0, 0))
    }

    private fun sampleSettings(activity: Activity, dialog: Dialog, talker: String, days: Int, range: TextView, metrics: LinearLayout, logger: (String, Throwable?) -> Unit): LinearLayout {
        val holder = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(activity, 10), 0, dp(activity, 12)) }
        holder.addView(section(activity, "采样设置"), marginParams(activity, -1, 40, 0, 0, 0, 8))
        val card = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 14)); background = rounded(0xfffffdf9.toInt(), dp(activity, 20)) }
        addSettingSlider(activity, card, "分析深度", "条", 100, 50000, 500)
        addSettingSlider(activity, card, "词云提取数", "个", 10, 80, 40)
        addSettingSlider(activity, card, "最小词长", "字", 2, 10, 2)
        holder.addView(card)
        return holder
    }

    private fun showTopSamplingDialog(activity: Activity) {
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 14)); background = rounded(pageColor(activity), dp(activity, 22)) }
        root.addView(label(activity, "采样设置", 21f, textColor(activity), true), marginParams(activity, -1, 46, 0, 0, 0, 10))
        val card = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 10)); background = rounded(panelColor(activity), dp(activity, 18)) }
        addSettingSlider(activity, card, "分析深度", "条", 100, 50000, 500)
        addSettingSlider(activity, card, "词云提取数", "个", 10, 80, 40)
        addSettingSlider(activity, card, "最小词长", "字", 2, 10, 2)
        root.addView(card)
        lateinit var dialog: Dialog
        val actions = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(label(activity, "取消", 16f, 0xffffa13a.toInt(), true).apply { gravity = Gravity.CENTER; setOnClickListener { dialog.dismiss() } }, LinearLayout.LayoutParams(0, dp(activity, 50), 1f))
        actions.addView(actionButton(activity, "保存", 0xffffa13a.toInt()) { dialog.dismiss() }, LinearLayout.LayoutParams(0, dp(activity, 50), 1f))
        root.addView(actions, marginParams(activity, -1, 50, 0, 12, 0, 0))
        dialog = Dialog(activity); dialog.setContentView(root); dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); dialog.window?.setWindowAnimations(0); dialog.setOnShowListener { dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .90f).toInt(), (activity.resources.displayMetrics.heightPixels * .72f).toInt()) }; dialog.show()
    }

    private fun showSamplingDialog(activity: Activity) {
        val store = ConversationAnalysisAiConfigStore(activity)
        var window = store.contextWindow()
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 28), dp(activity, 24), dp(activity, 28), dp(activity, 18)); background = rounded(pageColor(activity), dp(activity, 26)) }
        root.addView(label(activity, "模型容量与采样", 24f, textColor(activity), true), marginParams(activity, -1, 54, 0, 0, 0, 16))
        root.addView(label(activity, "模型上下文容量", 17f, textColor(activity), true), marginParams(activity, -1, 34, 0, 0, 0, 8))
        val capacityBar = LinearLayout(activity).apply { background = rounded(panelColor(activity), dp(activity, 18)); setPadding(dp(activity, 5), dp(activity, 5), dp(activity, 5), dp(activity, 5)) }
        val capacities = listOf("128k", "256k", "512k", "1m", "2m")
        capacities.forEach { value ->
            val item = label(activity, value.uppercase(Locale.ROOT), 16f, if (value == window) 0xffffa13a.toInt() else if (isDark(activity)) 0xffd0cbd2.toInt() else 0xff5c5652.toInt(), value == window).apply { gravity = Gravity.CENTER; setOnClickListener { window = value; capacityBar.children().forEach { (it as TextView).setTextColor(if ((it as TextView).text.toString().lowercase(Locale.ROOT) == value) 0xffffa13a.toInt() else if (isDark(activity)) 0xffd0cbd2.toInt() else 0xff5c5652.toInt()) } } }
            capacityBar.addView(item, LinearLayout.LayoutParams(0, dp(activity, 68), 1f))
        }
        root.addView(capacityBar, marginParams(activity, -1, 78, 0, 0, 0, 26))
        val limitTitle = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        limitTitle.addView(label(activity, "提取消息数量上限", 17f, textColor(activity), true), LinearLayout.LayoutParams(0, dp(activity, 34), 1f))
        val limitValue = label(activity, if (store.sampleLimit() == 0) "自动" else store.sampleLimit().toString(), 17f, 0xffffa13a.toInt(), true).apply { gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL }
        limitTitle.addView(limitValue, LinearLayout.LayoutParams(dp(activity, 90), dp(activity, 34)))
        root.addView(limitTitle)
        val seek = SeekBar(activity).apply { max = 50000; progress = store.sampleLimit(); progressTintList = android.content.res.ColorStateList.valueOf(0xffffa13a.toInt()); thumbTintList = android.content.res.ColorStateList.valueOf(0xffffa13a.toInt()); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(b: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) limitValue.text = if (p == 0) "自动" else p.toString() }; override fun onStartTrackingTouch(b: SeekBar?) = Unit; override fun onStopTrackingTouch(b: SeekBar?) = Unit }) }
        root.addView(seek, LinearLayout.LayoutParams(-1, dp(activity, 54)))
        root.addView(label(activity, "注：设为 0 时根据容量自动计算（3000 条）。当指定时段的消息总数超出上限时，会自动进行全局均匀抽样，保障整体逻辑连贯。", 14f, if (isDark(activity)) 0xffaaa4ae.toInt() else 0xff77716d.toInt(), false).apply { setLineSpacing(4f, 1f) }, marginParams(activity, -1, 0, 0, 12, 0, 18))
        lateinit var dialog: Dialog
        val actions = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(label(activity, "取消", 16f, 0xffffa13a.toInt(), true).apply { gravity = Gravity.CENTER; setOnClickListener { dialog.dismiss() } }, LinearLayout.LayoutParams(0, dp(activity, 54), 1f))
        val save = actionButton(activity, "保存", 0xffffa13a.toInt()) { store.setSampling(window, seek.progress); dialog.dismiss() }
        actions.addView(save, LinearLayout.LayoutParams(0, dp(activity, 54), 1f))
        root.addView(actions)
        dialog = Dialog(activity); dialog.setContentView(root); dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); dialog.window?.setWindowAnimations(0); dialog.setOnShowListener { dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .92f).toInt(), (activity.resources.displayMetrics.heightPixels * .78f).toInt()) }; dialog.show()
    }

    private fun addSettingSlider(activity: Activity, parent: LinearLayout, title: String, unit: String, min: Int, max: Int, initial: Int) {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val labels = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        labels.addView(label(activity, title, 16f, textColor(activity), true), LinearLayout.LayoutParams(0, dp(activity, 34), 1f))
        val value = label(activity, "$initial $unit", 16f, 0xfff57c00.toInt(), true).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT }
        labels.addView(value, LinearLayout.LayoutParams(dp(activity, 110), dp(activity, 34)))
        row.addView(labels)
        val seek = SeekBar(activity).apply {
            this.max = max - min; progress = initial - min
            progressTintList = android.content.res.ColorStateList.valueOf(0xfff57c00.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xfff57c00.toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) value.text = "${min + p} $unit" }
                override fun onStartTrackingTouch(b: SeekBar?) = Unit
                override fun onStopTrackingTouch(b: SeekBar?) = Unit
            })
        }
        row.addView(seek, LinearLayout.LayoutParams(-1, dp(activity, 48)))
        parent.addView(row, marginParams(activity, -1, 92, 0, 0, 0, 5))
    }

    private fun buildInsight(activity: Activity, dialog: Dialog, talker: String, days: Int, logger: (String, Throwable?) -> Unit): LinearLayout {
        var selectedDays = days
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 16)) }
        content.addView(divider(activity), marginParams(activity, -1, 1, 0, 4, 0, 12))
        val top = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(label(activity, "选择总结时段", 16f, textColor(activity), true), LinearLayout.LayoutParams(0, dp(activity, 40), 1f))
        val sampling = label(activity, "▤", 22f, 0xffff7a00.toInt(), true).apply { gravity = Gravity.CENTER; setOnClickListener { showSamplingDialog(activity) } }
        val api = label(activity, "⚙", 22f, 0xffff7a00.toInt(), true).apply { gravity = Gravity.CENTER; setOnClickListener { showAiConfig(activity) } }
        top.addView(sampling, LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 40)))
        top.addView(api, LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 40)))
        content.addView(top)
        val tabs = LinearLayout(activity).apply { gravity = Gravity.CENTER; background = rounded(panelColor(activity), dp(activity, 14)); setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4)) }
        val tabScroll = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
        val tabWidth = ((activity.resources.displayMetrics.widthPixels / activity.resources.displayMetrics.density - 32f) / 6f).toInt().coerceAtLeast(52)
        listOf("今日" to 1, "昨日" to -1, "本周" to 7, "上周" to -7, "本月" to 30, "上月" to -30, "全部" to 365).forEachIndexed { index, pair ->
            val tab = label(activity, pair.first, 14f, if (index == 0) 0xfff57c00.toInt() else 0xff4e4b54.toInt(), index == 0).apply {
                gravity = Gravity.CENTER
                setOnClickListener {
                    selectedDays = pair.second
                    tabs.children().forEach { child ->
                        child.background = null
                        (child as? TextView)?.apply { setTextColor(0xff4e4b54.toInt()); setTypeface(typeface, android.graphics.Typeface.NORMAL) }
                    }
                    background = rounded(0xfffffdf9.toInt(), dp(activity, 12))
                    setTextColor(0xfff57c00.toInt())
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
            }
            tabs.addView(tab, LinearLayout.LayoutParams(dp(activity, tabWidth), dp(activity, 34)))
        }
        content.addView(tabScroll, marginParams(activity, -1, 42, 0, 10, 0, 10))
        val focus = EditText(activity).apply { hint = "（可选）让 AI 重点关注什么？例如：大家聊了吃的吗"; gravity = Gravity.CENTER; setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 8)); background = rounded(0x00ffffff, dp(activity, 14)) }
        content.addView(focus, LinearLayout.LayoutParams(-1, dp(activity, 58)))
        val buttons = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val reportHost = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val screenshot = actionButton(activity, "生成截图", 0xffd8d8d8.toInt()) { showGeneratedReportPicker(activity, dialog, talker) }.apply {
            isEnabled = false
            setTextColor(0xff999999.toInt())
        }
        val generate = actionButton(activity, "✦  开始生成", 0xfff57c00.toInt()) { showAi(activity, dialog, talker, selectedDays, logger, reportHost, screenshot) }
        buttons.addView(generate, LinearLayout.LayoutParams(0, dp(activity, 46), 1f)); buttons.addView(Space(activity), LinearLayout.LayoutParams(dp(activity, 10), 1)); buttons.addView(screenshot, LinearLayout.LayoutParams(0, dp(activity, 46), 1f))
        content.addView(buttons, marginParams(activity, -1, 46, 0, 12, 0, 12))
        reportHost.addView(label(activity, "点击上方按钮，让分析实时生成报告", 15f, 0xff7e7984.toInt(), false).apply { gravity = Gravity.CENTER; setPadding(dp(activity, 12), dp(activity, 18), dp(activity, 12), dp(activity, 18)); background = rounded(0xfffffdf9.toInt(), dp(activity, 18)) })
        content.addView(reportHost, marginParams(activity, -1, 0, 0, 0, 0, 0))
        return content
    }

    private fun buildDeepCharts(activity: Activity, dialog: Dialog, talker: String, days: Int, metrics: LinearLayout, range: TextView, logger: (String, Throwable?) -> Unit): LinearLayout {
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; background = rounded(cardColor(activity), dp(activity, 28)); setPadding(dp(activity, 16), dp(activity, 12), dp(activity, 16), dp(activity, 14)) }
        val activityBody = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val cycle = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 16), dp(activity, 18), dp(activity, 16), dp(activity, 18)); background = rounded(panelColor(activity), dp(activity, 22)) }
        val activityTitle = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        activityTitle.addView(label(activity, "检测周期", 18f, 0xff292522.toInt(), true), LinearLayout.LayoutParams(0, dp(activity, 34), 1f))
        val activityPeriod = label(activity, "最近 $days 天", 18f, 0xfff57c00.toInt(), true).apply { gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL }
        activityTitle.addView(activityPeriod, LinearLayout.LayoutParams(dp(activity, 104), dp(activity, 34)))
        val activitySeek = SeekBar(activity).apply { max = 59; progress = days - 1; progressTintList = android.content.res.ColorStateList.valueOf(0xfff57c00.toInt()); thumbTintList = android.content.res.ColorStateList.valueOf(0xfff57c00.toInt()) }
        cycle.addView(activityTitle)
        cycle.addView(activitySeek, LinearLayout.LayoutParams(-1, dp(activity, 54)))
        activityBody.addView(cycle)
        val activeValue = label(activity, "0", 23f, 0xff292522.toInt(), true)
        val memberValue = label(activity, "0", 23f, 0xff292522.toInt(), true)
        val stats = LinearLayout(activity).apply { background = rounded(panelColor(activity), dp(activity, 18)); setPadding(dp(activity, 8), dp(activity, 12), dp(activity, 8), dp(activity, 12)) }
        stats.addView(metricSmallView(activity, activeValue, "活跃发言人数"), LinearLayout.LayoutParams(0, dp(activity, 62), 1f))
        stats.addView(metricSmallView(activity, memberValue, "当前群聊总人数"), LinearLayout.LayoutParams(0, dp(activity, 62), 1f))
        activityBody.addView(stats, marginParams(activity, -1, 0, 0, 12, 0, 0))
        val inactive = label(activity, "查看/清理未发言成员（0人）", 15f, 0xffb82d25.toInt(), true).apply { gravity = Gravity.CENTER; setPadding(dp(activity, 12), 0, dp(activity, 12), 0); background = rounded(0xfff1e2df.toInt(), dp(activity, 18)) }
        activityBody.addView(inactive, LinearLayout.LayoutParams(-1, dp(activity, 54)))
        addCollapsible(activity, root, "♟", "群聊活跃检测", activityBody, false)
        fun reloadActivity(nextDays: Int) {
            executor.execute {
                val result = runCatching { ConversationAnalysisDetailsRepository().load(talker, nextDays) }.getOrElse { ConversationAnalysisDetails(emptyList(), emptyList(), ConversationDeepStats(IntArray(24), emptyMap(), 0, 0, 0, 0), it.message) }
                Handler(Looper.getMainLooper()).post {
                    activeValue.text = result.ranking.count { it.count > 0 }.toString()
                    memberValue.text = result.lowActivity.size.toString()
                    inactive.text = "查看/清理未发言成员（${result.lowActivity.count { it.count == 0 }}人）"
                    inactive.setOnClickListener { showLowActivityMembers(activity, result.lowActivity) }
                }
            }
        }
        activitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: SeekBar?, value: Int, fromUser: Boolean) { if (fromUser) { activityPeriod.text = "最近 ${value + 1} 天" } }
            override fun onStartTrackingTouch(b: SeekBar?) = Unit
            override fun onStopTrackingTouch(b: SeekBar?) { reloadActivity((b?.progress ?: 0) + 1) }
        })
        reloadActivity(days)
        val names = listOf("活跃发言排行" to "▥", "高频语义特征" to "✦", "聊天作息图鉴" to "◕", "情绪指纹探测" to "☺", "废话程度鉴定" to "π", "多维属性探测" to "◎", "全天活跃频次" to "⌁", "内容载体偏好" to "◌")
        val bodies = LinkedHashMap<String, LinearLayout>()
        var rankingPeriod = days
        var reloadRanking: ((Int) -> Unit)? = null
        names.forEach { pair ->
            val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 12)); background = rounded(panelColor(activity), dp(activity, 16)) }
            body.addView(label(activity, "正在读取群聊数据…", 14f, 0xff4e4b54.toInt(), false).apply { gravity = Gravity.CENTER; setPadding(0, dp(activity, 18), 0, dp(activity, 18)) })
            bodies[pair.first] = body
            addCollapsible(activity, root, pair.second, pair.first, body, false)
        }
        reloadRanking = { periodDays ->
            rankingPeriod = periodDays
            executor.execute {
                val refreshed = runCatching { ConversationAnalysisDetailsRepository().load(talker, periodDays) }.getOrElse {
                    ConversationAnalysisDetails(emptyList(), emptyList(), ConversationDeepStats(IntArray(24), emptyMap(), 0, 0, 0, 0), it.message)
                }
                Handler(Looper.getMainLooper()).post { renderRanking(activity, bodies["活跃发言排行"], refreshed.ranking, reloadRanking, rankingPeriod) }
            }
        }
        executor.execute {
            val details = runCatching { ConversationAnalysisDetailsRepository().load(talker, days) }.getOrElse {
                ConversationAnalysisDetails(emptyList(), emptyList(), ConversationDeepStats(IntArray(24), emptyMap(), 0, 0, 0, 0), it.message)
            }
            Handler(Looper.getMainLooper()).post {
                renderDeepData(activity, bodies, details, reloadRanking)
                val active = details.ranking.size
                updateMetric(metrics, 0, active.toString())
            }
        }
        return root
    }

    private fun renderDeepData(activity: Activity, bodies: Map<String, LinearLayout>, details: ConversationAnalysisDetails, reloadRanking: ((Int) -> Unit)? = null) {
        if (details.error != null) {
            bodies.values.forEach { box -> box.removeAllViews(); box.addView(label(activity, details.error, 14f, 0xffb82d25.toInt(), false).apply { gravity = Gravity.CENTER }) }
            return
        }
        renderRanking(activity, bodies["活跃发言排行"], details.ranking, reloadRanking)
        renderBars(activity, bodies["高频语义特征"], listOf("平均文本长度" to details.deep.avgLength, "问题消息" to details.deep.questionCount, "欢乐表达" to details.deep.laughCount), 0xfff57c00.toInt())
        renderBars(activity, bodies["情绪指纹探测"], listOf("快乐浓度" to details.deep.laughCount, "疑惑指数" to details.deep.questionCount, "表达强度" to details.deep.longest), 0xffe65c00.toInt())
        renderBars(activity, bodies["废话程度鉴定"], listOf("平均文本长度" to details.deep.avgLength, "最长文本" to details.deep.longest), 0xff2196f3.toInt())
        renderBars(activity, bodies["多维属性探测"], listOf("活跃度" to details.ranking.size, "互动率" to details.deep.questionCount, "表达欲" to details.deep.avgLength, "含金量" to details.deep.longest), 0xff9c27b0.toInt())
        renderBars(activity, bodies["内容载体偏好"], details.deep.types.entries.map { typeName(it.key) to it.value }, 0xfff57c00.toInt())
        renderRoutine(activity, bodies["聊天作息图鉴"], details.deep.hourly)
        renderHourly(activity, bodies["全天活跃频次"], details.deep.hourly)
    }

    private fun renderRanking(activity: Activity, box: LinearLayout?, rows: List<ConversationRankingRow>, reload: ((Int) -> Unit)? = null, selectedPeriod: Int = 1) {
        if (box == null) return
        box.removeAllViews()
        val tabs = LinearLayout(activity).apply { gravity = Gravity.CENTER; background = rounded(0xffffead2.toInt(), dp(activity, 14)); setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4)) }
        val tabScroll = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
        val tabWidth = ((activity.resources.displayMetrics.widthPixels / activity.resources.displayMetrics.density - 32f) / 6f).toInt().coerceAtLeast(52)
        val periods = listOf(1, -1, 7, -7, 30, -30, 365)
        listOf("今日", "昨日", "本周", "上周", "本月", "上月", "全部").forEachIndexed { index, text ->
            val selected = periods[index] == selectedPeriod
            val tab = label(activity, text, 13f, if (selected) 0xfff57c00.toInt() else 0xff6f6862.toInt(), selected).apply {
                gravity = Gravity.CENTER
                setOnClickListener {
                    val periodDays = periods[index]
                    reload?.invoke(periodDays)
                    tabs.children().forEach { it.background = null; (it as? TextView)?.setTextColor(0xff6f6862.toInt()) }
                    background = rounded(0xfffffdf9.toInt(), dp(activity, 10)); setTextColor(0xfff57c00.toInt())
                }
            }
            tabs.addView(tab, LinearLayout.LayoutParams(dp(activity, tabWidth), dp(activity, 32)))
        }
        box.addView(tabScroll, marginParams(activity, -1, 40, 0, 0, 0, 8))
        if (rows.isEmpty()) { box.addView(label(activity, "所选周期暂无数据", 14f, 0xff4e4b54.toInt(), false)); return }
        val max = rows.maxOf { it.count }.coerceAtLeast(1)
        rows.take(10).forEachIndexed { index, row ->
            val item = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(activity, 7), 0, dp(activity, 7)) }
            val titleRow = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            titleRow.addView(label(activity, (index + 1).toString(), 16f, if (index < 3) 0xfff57c00.toInt() else 0xff77716d.toInt(), true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(activity, 30), dp(activity, 38)))
            titleRow.addView(label(activity, row.name.take(12), 14f, textColor(activity), true).apply { gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(0, dp(activity, 38), 1f))
            titleRow.addView(label(activity, row.count.toString() + " 条", 13f, 0xfff57c00.toInt(), true).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT }, LinearLayout.LayoutParams(dp(activity, 54), dp(activity, 38)))
            item.addView(titleRow)
            val track = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = rounded(if (isDark(activity)) 0xff4a3424.toInt() else 0xffffe0bd.toInt(), dp(activity, 8)) }
            val fill = View(activity).apply { background = rounded(0xffffa13a.toInt(), dp(activity, 8)) }
            val ratio = (row.count.toFloat() / max).coerceIn(.02f, 1f)
            track.addView(fill, LinearLayout.LayoutParams(0, dp(activity, 9), ratio))
            track.addView(Space(activity), LinearLayout.LayoutParams(0, dp(activity, 9), 1f - ratio))
            item.addView(track, LinearLayout.LayoutParams(-1, dp(activity, 9)).apply { setMargins(dp(activity, 30), dp(activity, 4), dp(activity, 54), 0) })
            box.addView(item)
        }
    }

    private fun renderRoutine(activity: Activity, box: LinearLayout?, hourly: IntArray) {
        if (box == null) return
        box.removeAllViews()
        val groups = listOf(
            Triple("早起鸟儿", "05:00 - 08:59", hourly.slice(5..8).sum()),
            Triple("带薪摸鱼", "09:00 - 17:59", hourly.slice(9..17).sum()),
            Triple("夜生活", "18:00 - 22:59", hourly.slice(18..22).sum()),
            Triple("熬夜修仙", "23:00 - 04:59", hourly[23] + hourly.slice(0..4).sum())
        )
        val grid = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        groups.chunked(2).forEach { pair ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { index, item ->
                val colors = listOf(0xffffe7c2, 0xffffd6c5, 0xffd9e9ff, 0xffe4d8ff).map { it.toInt() }
                val card = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = rounded(colors[groups.indexOf(item) % colors.size], dp(activity, 18)); setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8)) }
                card.addView(label(activity, "◷", 25f, 0xffe65c00.toInt(), true), LinearLayout.LayoutParams(-1, dp(activity, 30)))
                card.addView(label(activity, item.first, 13f, textColor(activity), true), LinearLayout.LayoutParams(-1, dp(activity, 25)))
                card.addView(label(activity, item.third.toString() + " 条", 27f, textColor(activity), true), LinearLayout.LayoutParams(-1, dp(activity, 30)))
                row.addView(card, LinearLayout.LayoutParams(0, dp(activity, 154), 1f).apply { setMargins(if (index == 0) 0 else dp(activity, 6), 0, if (index == 1) 0 else dp(activity, 6), dp(activity, 8)) })
            }
            grid.addView(row)
        }
        box.addView(grid)
    }

    private fun typeName(type: Int): String = when (type) {
        1 -> "文本消息"; 3 -> "图片消息"; 34 -> "语音消息"; 43 -> "视频消息"; 47 -> "表情消息"; 49 -> "链接/文件"; else -> "其他消息"
    }

    private fun renderBars(activity: Activity, box: LinearLayout?, values: List<Pair<String, Int>>, color: Int) {
        if (box == null) return
        box.removeAllViews()
        if (values.isEmpty()) { box.addView(label(activity, "所选周期暂无数据", 14f, 0xff4e4b54.toInt(), false)); return }
        val max = values.maxOf { it.second }.coerceAtLeast(1)
        values.forEach { pair ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(activity, 5), 0, dp(activity, 5)) }
            val top = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            top.addView(label(activity, pair.first, 13f, 0xff302b28.toInt(), true), LinearLayout.LayoutParams(0, dp(activity, 24), 1f))
            top.addView(label(activity, pair.second.toString(), 13f, color, true).apply { gravity = Gravity.RIGHT }, LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 24)))
            row.addView(top)
            val track = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; background = rounded(if (isDark(activity)) 0xff4a3424.toInt() else 0xffffe0bd.toInt(), dp(activity, 8)) }
            val ratio = (pair.second.toFloat() / max).coerceIn(.02f, 1f)
            track.addView(View(activity).apply { background = rounded(color, dp(activity, 8)) }, LinearLayout.LayoutParams(0, dp(activity, 9), ratio))
            track.addView(Space(activity), LinearLayout.LayoutParams(0, dp(activity, 9), 1f - ratio))
            row.addView(track, LinearLayout.LayoutParams(-1, dp(activity, 9)))
            box.addView(row)
        }
    }

    private fun renderHourly(activity: Activity, box: LinearLayout?, values: IntArray) {
        if (box == null) return
        box.removeAllViews()
        box.addView(HourlyBarsView(activity, values), LinearLayout.LayoutParams(-1, dp(activity, 130)))
    }

    private class HourlyBarsView(context: android.content.Context, private val values: IntArray) : View(context) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: android.graphics.Canvas) {
            val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
            val slot = width.toFloat() / 24f
            paint.strokeWidth = (slot * .38f).coerceAtLeast(2f); paint.strokeCap = android.graphics.Paint.Cap.ROUND
            for (i in 0 until 24) {
                val x = slot * (i + .5f); val bottom = height - 25f; val top = bottom - (height - 45f) * values.getOrElse(i) { 0 } / max
                paint.color = 0xffffe0bd.toInt(); canvas.drawLine(x, 10f, x, bottom, paint)
                paint.color = 0xfff57c00.toInt(); canvas.drawLine(x, bottom, x, top, paint)
                if (i % 6 == 0) { paint.color = 0xff4e4b54.toInt(); paint.textSize = 11f; paint.textAlign = android.graphics.Paint.Align.CENTER; canvas.drawText(i.toString(), x, height - 5f, paint) }
            }
        }
    }

    private fun actionButton(activity: Activity, text: String, color: Int, click: () -> Unit) = TextView(activity).apply { this.text = text; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD); background = rounded(color, dp(activity, 16)); setOnClickListener { click() } }
    private fun metricSmallView(activity: Activity, value: TextView, title: String) = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; addView(value, LinearLayout.LayoutParams(-1, dp(activity, 34))); addView(label(activity, title, 11f, 0xff4e4b54.toInt(), false)) }
    private fun metricSmall(activity: Activity, value: String, title: String) = metricSmallView(activity, label(activity, value, 23f, 0xff292522.toInt(), true), title)
    private fun activityTitleText(row: LinearLayout, text: String) { (row.getChildAt(1) as? TextView)?.text = text }

    private fun showLowActivityMembers(activity: Activity, members: List<ConversationLowActivityRow>) {
        val dialog = Dialog(activity)
        val lowMembers = members.filter { it.count == 0 }
        val selected = linkedSetOf<String>()
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 12)); background = rounded(pageColor(activity), dp(activity, 22)) }
        val titleRow = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(label(activity, "低活跃成员（${lowMembers.size}人）", 19f, textColor(activity), true), LinearLayout.LayoutParams(-1, dp(activity, 42)))
        box.addView(titleRow)
        box.addView(label(activity, "按所选检测周期的发言数从少到多排列，其中未发言 ${lowMembers.size} 人。勾选后可批量移出群聊。", 13f, if (isDark(activity)) 0xffd0cbd2.toInt() else 0xff77716d.toInt(), false), marginParams(activity, -1, 0, 0, 4, 0, 8))
        val search = EditText(activity).apply { hint = "搜索昵称或微信号"; textSize = 15f; setTextColor(textColor(activity)); setHintTextColor(if (isDark(activity)) 0xffaaa4ae.toInt() else 0xff77716d.toInt()); setSingleLine(true); setPadding(dp(activity, 14), 0, dp(activity, 14), 0); background = strokeRounded(cardColor(activity), if (isDark(activity)) 0xff77737d.toInt() else 0xff8b8b93.toInt(), dp(activity, 18), dp(activity, 1)) }
        box.addView(search, LinearLayout.LayoutParams(-1, dp(activity, 54)))
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        lateinit var removeButton: TextView
        fun redraw(filter: String = "") {
            body.removeAllViews()
            lowMembers.filter { filter.isBlank() || it.name.contains(filter, true) || it.wxId.contains(filter, true) }.forEach { member ->
                val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(activity, 0), dp(activity, 5), 0, dp(activity, 5)); setOnClickListener { if (!selected.add(member.wxId)) selected.remove(member.wxId); val mark = getChildAt(1) as TextView; mark.text = if (selected.contains(member.wxId)) "✓" else "□"; removeButton.text = "移出群聊（${selected.size}）"; removeButton.background = rounded(if (selected.isEmpty()) 0xff686868.toInt() else 0xffff8a00.toInt(), dp(activity, 16)) } }
                val name = label(activity, "${member.name}\n${member.wxId}  ·  未发言", 15f, 0xffd34242.toInt(), false)
                row.addView(name, LinearLayout.LayoutParams(0, dp(activity, 58), 1f))
                row.addView(label(activity, "□", 28f, if (isDark(activity)) 0xffc7c2c8.toInt() else 0xff77716d.toInt(), false).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 58)))
                body.addView(row)
                body.addView(divider(activity), LinearLayout.LayoutParams(-1, dp(activity, 1)))
            }
        }
        search.addTextChangedListener(object : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { redraw(s?.toString().orEmpty()) }; override fun afterTextChanged(s: android.text.Editable?) = Unit })
        redraw()
        box.addView(ScrollView(activity).apply { isFillViewport = true; addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(label(activity, "取消", 16f, 0xffffa13a.toInt(), true).apply { gravity = Gravity.CENTER; setOnClickListener { dialog.dismiss() } }, LinearLayout.LayoutParams(0, dp(activity, 52), 1f))
        removeButton = actionButton(activity, "移出群聊（0）", 0xff686868.toInt()) { dialog.dismiss() }
        actions.addView(removeButton, LinearLayout.LayoutParams(0, dp(activity, 52), 1f))
        box.addView(actions)
        dialog.setContentView(box)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener { dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .92f).toInt(), (activity.resources.displayMetrics.heightPixels * .86f).toInt()) }
        dialog.show()
    }
    private fun divider(activity: Activity) = View(activity).apply { setBackgroundColor(0xffeee2d6.toInt()) }
    private fun LinearLayout.children(): List<View> = (0 until childCount).map { getChildAt(it) }

    private fun showModelPicker(activity: Activity, models: List<String>, onSelected: (String) -> Unit) {
        val dialog = Dialog(activity)
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 14)); background = rounded(pageColor(activity), dp(activity, 22)) }
        root.addView(label(activity, "选择模型", 21f, textColor(activity), true), marginParams(activity, -1, 46, 0, 0, 0, 10))
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        models.forEach { model ->
            list.addView(label(activity, model, 15f, textColor(activity), false).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(activity, 12), 0, dp(activity, 12), 0); setOnClickListener { onSelected(model); dialog.dismiss() } }, LinearLayout.LayoutParams(-1, dp(activity, 48)))
            list.addView(divider(activity), LinearLayout.LayoutParams(-1, dp(activity, 1)))
        }
        root.addView(ScrollView(activity).apply { isFillViewport = true; addView(list) }, LinearLayout.LayoutParams(-1, dp(activity, 240)))
        root.addView(label(activity, "取消", 16f, 0xffffa13a.toInt(), true).apply { gravity = Gravity.CENTER; setOnClickListener { dialog.dismiss() } }, LinearLayout.LayoutParams(-1, dp(activity, 50)))
        dialog.setContentView(root); dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); dialog.window?.setWindowAnimations(0); dialog.setOnShowListener { dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .86f).toInt(), (activity.resources.displayMetrics.heightPixels * .72f).toInt()) }; dialog.show()
    }

    private fun showAiConfig(activity: Activity) {
        val ai = ConversationAnalysisAi(activity)
        val store = ConversationAnalysisAiConfigStore(activity)
        var selected = store.activeProfileId()
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 26), dp(activity, 22), dp(activity, 26), dp(activity, 18)); background = rounded(pageColor(activity), dp(activity, 26)) }
        val titleRow = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(label(activity, "API 接口配置", 24f, textColor(activity), true), LinearLayout.LayoutParams(0, dp(activity, 52), 1f))
        titleRow.addView(label(activity, "▾", 22f, 0xffffa13a.toInt(), true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 52)))
        root.addView(titleRow)
        val profileBar = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; background = rounded(panelColor(activity), dp(activity, 14)); setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4)) }
        val fields = arrayOf(EditText(activity), EditText(activity), EditText(activity), EditText(activity), EditText(activity))
        val fieldNames = listOf("方案名称", "API 地址", "API 路径", "API Key（Bearer Token）", "模型名称")
        fields.forEachIndexed { index, field ->
            root.addView(label(activity, fieldNames[index], 16f, if (isDark(activity)) 0xffd0cbd2.toInt() else 0xff5c5652.toInt(), true), marginParams(activity, -1, 30, 0, 10, 0, 2))
            field.hint = fieldNames[index]
            field.setTextColor(textColor(activity)); field.setHintTextColor(if (isDark(activity)) 0xffaaa4ae.toInt() else 0xff8b8178.toInt()); field.setPadding(dp(activity, 14), 0, dp(activity, 14), 0); field.background = strokeRounded(cardColor(activity), if (isDark(activity)) 0xff77737d.toInt() else 0xffead8c5.toInt(), dp(activity, 14), dp(activity, 1)); root.addView(field, marginParams(activity, -1, 58, 0, 0, 0, 2))
        }
// API configuration does not edit sampling values; sampling has its own dialog.

        fun loadProfile(id: Int) { selected = id; val p = ai.profiles()[id - 1]; fields[0].setText(p.name); fields[1].setText(p.address); fields[2].setText(p.path); fields[3].setText(p.key); fields[4].setText(p.model) }
        (1..2).forEach { id -> profileBar.addView(label(activity, "方案$id", 15f, if (id == selected) 0xffffa13a.toInt() else textColor(activity), true).apply { gravity = Gravity.CENTER; setOnClickListener { loadProfile(id) } }, LinearLayout.LayoutParams(0, dp(activity, 38), 1f)) }
        root.addView(profileBar, 1)
        lateinit var dialog: Dialog
        lateinit var testButton: TextView
        val utilityButtons = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        testButton = actionButton(activity, "测试连接", 0x00ffffff) {
            testButton.isEnabled = false
            testButton.text = "测试中…"
            ai.testConnection(fields[1].text.toString(), fields[2].text.toString(), fields[3].text.toString()) { ok, _ ->
                testButton.text = if (ok) "连接成功" else "连接失败"
                testButton.setTextColor(if (ok) 0xff4caf50.toInt() else 0xffd94b4b.toInt())
                Handler(Looper.getMainLooper()).postDelayed({
                    if (testButton.isAttachedToWindow) {
                        testButton.text = "测试连接"
                        testButton.setTextColor(0xffffa13a.toInt())
                        testButton.isEnabled = true
                    }
                }, 1500L)
            }
        }.apply {
            background = strokeRounded(0x00ffffff, 0xffffa13a.toInt(), dp(activity, 18), dp(activity, 2))
            setTextColor(0xffffa13a.toInt())
        }
        utilityButtons.addView(testButton, LinearLayout.LayoutParams(0, dp(activity, 48), 1f))
        utilityButtons.addView(actionButton(activity, "获取模型", 0x00ffffff) { ai.fetchModels(fields[1].text.toString(), fields[3].text.toString()) { models, error -> if (models.isNotEmpty()) showModelPicker(activity, models) { fields[4].setText(it) } else Toast.makeText(activity, "获取模型失败：${error ?: "返回为空"}", Toast.LENGTH_SHORT).show() } }.apply { background = strokeRounded(0x00ffffff, 0xffffa13a.toInt(), dp(activity, 18), dp(activity, 2)); setTextColor(0xffffa13a.toInt()) }, LinearLayout.LayoutParams(0, dp(activity, 48), 1f))
        root.addView(utilityButtons, marginParams(activity, -1, 48, 0, 14, 0, 0))
        val actions = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(activity, 8), 0, 0) }
        val cancel = label(activity, "取消", 16f, 0xffffa13a.toInt(), true).apply { gravity = Gravity.CENTER; background = strokeRounded(0x00ffffff, 0xffffa13a.toInt(), dp(activity, 14), dp(activity, 1)); setOnClickListener { dialog.dismiss() } }
        val save = actionButton(activity, "保存", 0xffffa13a.toInt()) { ai.saveProfile(ConversationAiProfile(selected, fields[0].text.toString(), fields[1].text.toString(), fields[2].text.toString(), fields[3].text.toString(), fields[4].text.toString())); ai.selectProfile(selected); dialog.dismiss() }
        actions.addView(cancel, LinearLayout.LayoutParams(0, dp(activity, 50), 1f).apply { setMargins(0, 0, dp(activity, 6), 0) })
        actions.addView(save, LinearLayout.LayoutParams(0, dp(activity, 50), 1f).apply { setMargins(dp(activity, 6), 0, 0, 0) })
        root.addView(actions)
        dialog = Dialog(activity)
        dialog.setContentView(ScrollView(activity).apply { isFillViewport = true; addView(root) })
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setWindowAnimations(0)
        dialog.setOnShowListener { dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .92f).toInt(), (activity.resources.displayMetrics.heightPixels * .88f).toInt()) }
        dialog.show()
        loadProfile(selected)
    }

    private fun showAi(activity: Activity, reportDialog: Dialog, talker: String, days: Int, logger: (String, Throwable?) -> Unit, reportHost: LinearLayout, screenshot: TextView) {
        val ai = ConversationAnalysisAi(activity)
        if (!ai.configured()) {
            val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 20), dp(activity, 18), dp(activity, 20), dp(activity, 18)); background = rounded(pageColor(activity), dp(activity, 22)) }
            val address = EditText(activity).apply { hint = "API 地址" }
            val path = EditText(activity).apply { hint = "API 路径"; setText("/chat/completions") }
            val key = EditText(activity).apply { hint = "API Key" }
            val model = EditText(activity).apply { hint = "模型名称" }
            listOf(address, path, key, model).forEach { it.setTextColor(0xff292522.toInt()); it.setHintTextColor(0xff8b8178.toInt()); it.background = strokeRounded(0xfffffdf9.toInt(), 0xffead8c5.toInt(), dp(activity, 14), dp(activity, 1)); it.setPadding(dp(activity, 14), 0, dp(activity, 14), 0); box.addView(it, marginParams(activity, -1, 48, 0, 4, 0, 0)) }
            lateinit var configDialog: Dialog
            val save = actionButton(activity, "保存并生成", 0xfff57c00.toInt()) { ai.save(address.text.toString(), path.text.toString(), key.text.toString(), model.text.toString()); configDialog.dismiss(); showAi(activity, reportDialog, talker, days, logger, reportHost, screenshot) }
            box.addView(save, marginParams(activity, -1, 46, 0, 10, 0, 0))
            configDialog = Dialog(activity)
            configDialog.setContentView(box)
            configDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            configDialog.setOnShowListener { configDialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .86f).toInt(), (activity.resources.displayMetrics.heightPixels * .55f).toInt()) }
            configDialog.show()
            return
        }
        reportHost.removeAllViews()
        screenshot.isEnabled = false
        screenshot.setTextColor(0xff999999.toInt())
        screenshot.background = rounded(0xffd8d8d8.toInt(), dp(activity, 16))
        val reportView = ConversationAnalysisAiReportView(activity)
        reportView.update(talker, days, "正在生成…")
        generatedReportView = reportView
        reportHost.addView(reportView, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        reportHost.requestLayout()
        ai.generateStreaming(talker, days, { partial ->
            reportView.update(talker, days, partial)
            reportHost.requestLayout()
        }, { final ->
            reportView.update(talker, days, final)
            val success = !final.startsWith("AI 总结失败：") && !final.startsWith("所选时段暂无") && !final.startsWith("请先配置")
            screenshot.isEnabled = success
            screenshot.setTextColor(if (success) Color.WHITE else 0xff999999.toInt())
            screenshot.background = rounded(if (success) 0xffe65c00.toInt() else 0xffd8d8d8.toInt(), dp(activity, 16))
            reportHost.requestLayout()
        })
    }

    private fun showGeneratedReportPicker(activity: Activity, reportDialog: Dialog, currentTalker: String) {
        val report = generatedReportView ?: run {
            Toast.makeText(activity, "请先点击开始生成", Toast.LENGTH_SHORT).show()
            return
        }
        val talkers = WeChatApis.conversations()?.getRecentConversationUsernames(10000).orEmpty()
            .filter { it != currentTalker && WeChatApis.contacts()?.isGroup(it) == true }
            .distinct()
        if (talkers.isEmpty()) {
            Toast.makeText(activity, "没有可发送的群聊", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = linkedSetOf<String>()
        lateinit var picker: Dialog
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 14))
            background = rounded(pageColor(activity), dp(activity, 24))
        }
        root.addView(label(activity, "选择群聊", 22f, textColor(activity), true), marginParams(activity, -1, 44, 0, 0, 0, 4))
        root.addView(label(activity, "选择要发送群聊分析截图的群聊", 14f, if (isDark(activity)) 0xffaaa4ae.toInt() else 0xff77716d.toInt(), false), marginParams(activity, -1, 32, 0, 0, 0, 8))
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        talkers.forEach { talker ->
            val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(activity, 4), 0, 0, 0) }
            val name = WeChatApis.conversations()?.getConversationTitle(talker).orEmpty().ifBlank { talker }
            row.addView(label(activity, name, 16f, textColor(activity), false), LinearLayout.LayoutParams(0, dp(activity, 52), 1f))
            val check = label(activity, "□", 26f, if (isDark(activity)) 0xffd0cbd2.toInt() else 0xff6f6862.toInt(), false).apply { gravity = Gravity.CENTER }
            row.addView(check, LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 52)))
            row.setOnClickListener {
                if (selected.add(talker)) { check.text = "✓"; check.setTextColor(0xffffa13a.toInt()) }
                else { selected.remove(talker); check.text = "□"; check.setTextColor(if (isDark(activity)) 0xffd0cbd2.toInt() else 0xff6f6862.toInt()) }
            }
            list.addView(row)
            list.addView(divider(activity))
        }
        root.addView(ScrollView(activity).apply { isFillViewport = true; addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(activity, 10), 0, 0) }
        val cancel = label(activity, "取消", 16f, 0xffffa13a.toInt(), true).apply { gravity = Gravity.CENTER; background = strokeRounded(0x00ffffff, 0xffffa13a.toInt(), dp(activity, 14), dp(activity, 1)); setOnClickListener { picker.dismiss() } }
        val send = actionButton(activity, "发送", 0xffffa13a.toInt()) {
            if (selected.isEmpty()) {
                Toast.makeText(activity, "请选择群聊", Toast.LENGTH_SHORT).show()
            } else {
                picker.dismiss()
                captureAndSend(activity, report, selected.toList())
            }
        }
        actions.addView(cancel, LinearLayout.LayoutParams(0, dp(activity, 50), 1f).apply { setMargins(0, 0, dp(activity, 6), 0) })
        actions.addView(send, LinearLayout.LayoutParams(0, dp(activity, 50), 1f).apply { setMargins(dp(activity, 6), 0, 0, 0) })
        root.addView(actions)
        picker = Dialog(activity)
        picker.setContentView(root)
        picker.window?.setBackgroundDrawableResource(android.R.color.transparent)
        picker.window?.setWindowAnimations(0)
        picker.setOnShowListener { picker.window?.setLayout((activity.resources.displayMetrics.widthPixels * .92f).toInt(), (activity.resources.displayMetrics.heightPixels * .84f).toInt()) }
        picker.show()
    }

    private fun captureAndSend(activity: Activity, root: View, talkers: List<String>) {
        ConversationAnalysisScreenshot.capture(root, File(activity.cacheDir, "conversation_analysis")) { file ->
            val ok = file != null && talkers.all { WeChatApis.media()?.sendImage(it, file.absolutePath) == true }
            Toast.makeText(activity, if (ok) "截图已发送" else "部分群聊发送失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDetails(
        activity: Activity,
        reportDialog: Dialog,
        talker: String,
        days: Int,
        logger: (String, Throwable?) -> Unit
    ) {
        reportDialog.dismiss()
        val loading = Dialog(activity)
        loading.setTitle("排行与深度分析")
        loading.setContentView(message(activity, "正在分析排行、作息和内容特征…", Color.DKGRAY))
        loading.show()
        executor.execute {
            val details = runCatching { ConversationAnalysisDetailsRepository().load(talker, days) }
                .onFailure { logger("群聊分析深度统计失败", it) }
                .getOrElse { ConversationAnalysisDetails(emptyList(), emptyList(), ConversationDeepStats(IntArray(24), emptyMap(), 0, 0, 0, 0), it.message) }
            Handler(Looper.getMainLooper()).post {
                loading.dismiss()
                if (!activity.isFinishing && !activity.isDestroyed) {
                    ConversationAnalysisDetailsDialog.show(activity, "排行与深度分析", details)
                }
            }
        }
    }

    private fun addMetric(activity: Activity, parent: LinearLayout, label: String, value: String, kind: ConversationAnalysisIconView.Kind) {
        val row = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8))
            background = rounded(Color.WHITE, dp(activity, 8))
        }
        row.addView(ConversationAnalysisIconView(activity, kind), LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)))
        val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(message(activity, label, Color.DKGRAY))
        texts.addView(message(activity, value, Color.rgb(35, 35, 35)).apply { textSize = 20f })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val params = LinearLayout.LayoutParams(-1, dp(activity, 72)).apply { setMargins(0, 0, 0, dp(activity, 8)) }
        parent.addView(row, params)
    }

    private fun message(activity: Activity, text: String, color: Int) = TextView(activity).apply {
        this.text = text
        textSize = 15f
        setTextColor(color)
        setPadding(dp(activity, 4), dp(activity, 7), dp(activity, 4), dp(activity, 7))
    }

    private fun rangeText(days: Int): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val end = Date()
        val start = Date(System.currentTimeMillis() - (days - 1L) * 86_400_000L)
        return "${format.format(start)} 至 ${format.format(end)}"
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
    private fun strokeRounded(color: Int, strokeColor: Int, radius: Int, strokeWidth: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat(); setStroke(strokeWidth, strokeColor) }
    private fun isDark(activity: Activity): Boolean = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private fun pageColor(activity: Activity): Int = if (isDark(activity)) 0xff111111.toInt() else 0xfffff8f0.toInt()
    private fun cardColor(activity: Activity): Int = if (isDark(activity)) 0xff1f1f1f.toInt() else 0xfffffdf9.toInt()
    private fun panelColor(activity: Activity): Int = if (isDark(activity)) 0xff292522.toInt() else 0xfffff4e6.toInt()
    private fun textColor(activity: Activity): Int = if (isDark(activity)) Color.WHITE else 0xff292522.toInt()
    private fun dp(activity: Activity, value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
