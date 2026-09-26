package h.Hchat.hooks.items.conversationanalysis

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import android.widget.Toast
import android.view.ViewGroup
import h.Hchat.hooks.api.core.WeChatApis
import java.io.File

internal object ConversationAnalysisReportDialog {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Hchat-ConversationAnalysis").apply { isDaemon = true }
    }
    fun show(activity: Activity, talker: String, logger: (String, Throwable?) -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return
        val dialog = Dialog(activity)
        var days = 1
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 22), dp(activity, 22), dp(activity, 22), dp(activity, 18))
            background = rounded(0xfffff8f0.toInt(), dp(activity, 24))
        }
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        val page = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(page)
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label(activity, "分析报告", 28f, 0xff25211f.toInt(), true), LinearLayout.LayoutParams(0, dp(activity, 48), 1f))
        val screenshot = label(activity, "□", 28f, 0xffd86522.toInt(), false).apply {
            gravity = Gravity.CENTER
            setOnClickListener { captureAndSend(activity, page, talker) }
        }
        header.addView(screenshot, LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 48)))
        page.addView(header)
        val range = label(activity, rangeText(days), 16f, 0xffd86522.toInt(), true)
        page.addView(range, marginParams(activity, -1, 34, 0, 14, 0, 0))

        page.addView(section(activity, "核心指标"), marginParams(activity, -1, 34, 0, 8, 0, 0))
        val metrics = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(0xfffffdf9.toInt(), dp(activity, 20))
            setPadding(dp(activity, 5), dp(activity, 15), dp(activity, 5), dp(activity, 15))
        }
        val metricRows = listOf(
            metricCard(activity, "♙", "0", "今日发言人数"),
            metricCard(activity, "◡", "0", "今日消息数"),
            metricCard(activity, "◷", "0", "历史消息总数")
        )
        metricRows.forEach { metrics.addView(it, LinearLayout.LayoutParams(0, dp(activity, 126), 1f)) }
        page.addView(metrics, marginParams(activity, -1, 0, 0, 18, 0, 0))

        val samplePanel = sampleSettings(activity, dialog, talker, days, range, metrics, logger)
        samplePanel.visibility = View.GONE
        val sampleIcon = label(activity, "≡", 27f, 0xfff57c00.toInt(), true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { samplePanel.visibility = if (samplePanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        }
        (header.getChildAt(1) as? ViewGroup)?.let { }
        header.removeViewAt(1)
        header.addView(sampleIcon, LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42)))
        page.addView(samplePanel, 1)
        page.addView(section(activity, "智能洞察"), marginParams(activity, -1, 48, 0, 12, 0, 0))
        val insightContent = buildInsight(activity, dialog, talker, days, logger)
        addCollapsible(activity, page, "✦", "智能总结消息", insightContent, false)
        page.addView(section(activity, "深度图表"), marginParams(activity, -1, 48, 0, 12, 0, 0))
        val deepRoot = buildDeepCharts(activity, dialog, talker, days, metrics, range, logger)
        page.addView(deepRoot)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val close = label(activity, "关闭", 16f, 0xffd86522.toInt(), true).apply { gravity = Gravity.CENTER; setOnClickListener { dialog.dismiss() } }
        root.addView(close, LinearLayout.LayoutParams(-1, dp(activity, 46)))
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener {
            dialog.window?.setLayout(-1, -1)
            dialog.window?.setStatusBarColor(0xfffff8f0.toInt())
            dialog.window?.setNavigationBarColor(0xfffff8f0.toInt())
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
        this.text = text; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun section(activity: Activity, text: String) = label(activity, text, 19f, 0xff302b28.toInt(), true)

    private fun metricCard(activity: Activity, icon: String, value: String, caption: String) = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(activity, 4), 0, dp(activity, 4), 0)
        addView(label(activity, icon, 29f, 0xffd86522.toInt(), false), LinearLayout.LayoutParams(-1, dp(activity, 38)))
        addView(label(activity, value, 25f, 0xff292522.toInt(), true), LinearLayout.LayoutParams(-1, dp(activity, 38)))
        addView(label(activity, caption, 11f, 0xff77716d.toInt(), false), LinearLayout.LayoutParams(-1, dp(activity, 28)))
    }

    private fun actionRow(activity: Activity, icon: String, title: String) = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL; setPadding(dp(activity, 17), 0, dp(activity, 14), 0); background = rounded(0xfffffdf9.toInt(), dp(activity, 20))
        addView(label(activity, icon, 23f, 0xffd86522.toInt(), false), LinearLayout.LayoutParams(dp(activity, 42), -1))
        addView(label(activity, title, 16f, 0xff302b28.toInt(), true), LinearLayout.LayoutParams(0, -1, 1f))
        addView(label(activity, "›", 28f, 0xffaaa29b.toInt(), false), LinearLayout.LayoutParams(dp(activity, 25), -1))
    }

    private fun marginParams(activity: Activity, width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int) = LinearLayout.LayoutParams(width, if (height == 0) -2 else dp(activity, height)).apply { setMargins(dp(activity, left), dp(activity, top), dp(activity, right), dp(activity, bottom)) }


    private fun addCollapsible(activity: Activity, parent: LinearLayout, iconText: String, titleText: String, body: View, open: Boolean) {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(0xfffffdf9.toInt(), dp(activity, 20))
        }
        val header = actionRow(activity, iconText, titleText)
        val arrow = header.getChildAt(2) as TextView
        arrow.text = if (open) "⌃" else "⌄"
        body.visibility = if (open) View.VISIBLE else View.GONE
        header.setOnClickListener {
            val expanded = body.visibility != View.VISIBLE
            body.visibility = if (expanded) View.VISIBLE else View.GONE
            arrow.text = if (expanded) "⌃" else "⌄"
        }
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

    private fun addSettingSlider(activity: Activity, parent: LinearLayout, title: String, unit: String, min: Int, max: Int, initial: Int) {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val labels = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        labels.addView(label(activity, title, 16f, 0xff292522.toInt(), true), LinearLayout.LayoutParams(0, dp(activity, 34), 1f))
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
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 16)) }
        content.addView(divider(activity), marginParams(activity, -1, 1, 0, 4, 0, 12))
        val top = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(label(activity, "选择总结时段", 16f, 0xff292522.toInt(), true), LinearLayout.LayoutParams(0, dp(activity, 40), 1f))
        val config = label(activity, "⚙", 22f, 0xfff57c00.toInt(), true).apply { gravity = Gravity.CENTER; setOnClickListener { showAiConfig(activity) } }
        top.addView(config, LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 40)))
        content.addView(top)
        val tabs = LinearLayout(activity).apply { gravity = Gravity.CENTER; background = rounded(0xfff6ece2.toInt(), dp(activity, 14)); setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4)) }
        listOf("今日" to 1, "昨日" to 2, "本周" to 7, "本月" to 30, "全部" to 60).forEachIndexed { index, pair ->
            val tab = label(activity, pair.first, 14f, if (index == 0) 0xfff57c00.toInt() else 0xff4e4b54.toInt(), index == 0).apply { gravity = Gravity.CENTER; setOnClickListener { tabs.children().forEach { it.background = null }; background = rounded(0xfffffdf9.toInt(), dp(activity, 12)); showAi(activity, dialog, talker, pair.second, logger) } }
            tabs.addView(tab, LinearLayout.LayoutParams(0, dp(activity, 34), 1f))
        }
        content.addView(tabs, marginParams(activity, -1, 42, 0, 10, 0, 10))
        val focus = EditText(activity).apply { hint = "（可选）让 AI 重点关注什么？例如：大家聊了吃的吗"; gravity = Gravity.CENTER; setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 8)); background = rounded(0x00ffffff, dp(activity, 14)) }
        content.addView(focus, LinearLayout.LayoutParams(-1, dp(activity, 58)))
        val buttons = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val generate = actionButton(activity, "✦  开始生成", 0xfff57c00.toInt()) { showAi(activity, dialog, talker, days, logger) }
        val screenshot = actionButton(activity, "生成截图", 0xffe65c00.toInt()) { Toast.makeText(activity, "请先生成 AI 报告", Toast.LENGTH_SHORT).show() }
        buttons.addView(generate, LinearLayout.LayoutParams(0, dp(activity, 46), 1f)); buttons.addView(Space(activity), LinearLayout.LayoutParams(dp(activity, 10), 1)); buttons.addView(screenshot, LinearLayout.LayoutParams(0, dp(activity, 46), 1f))
        content.addView(buttons, marginParams(activity, -1, 46, 0, 12, 0, 12))
        content.addView(label(activity, "点击上方按钮，让分析实时生成报告", 15f, 0xff7e7984.toInt(), false).apply { gravity = Gravity.CENTER; setPadding(dp(activity, 12), dp(activity, 18), dp(activity, 12), dp(activity, 18)); background = rounded(0xfffffdf9.toInt(), dp(activity, 18)) })
        return content
    }

    private fun buildDeepCharts(activity: Activity, dialog: Dialog, talker: String, days: Int, metrics: LinearLayout, range: TextView, logger: (String, Throwable?) -> Unit): LinearLayout {
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; background = rounded(0xfffffdf9.toInt(), dp(activity, 20)); setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 10)) }
        val activityBody = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8)) }
        activityBody.addView(actionRow(activity, "◷", "检测周期    最近 $days 天"))
        val seek = SeekBar(activity).apply { max = 59; progress = days - 1; progressTintList = android.content.res.ColorStateList.valueOf(0xfff57c00.toInt()) }
        activityBody.addView(seek, LinearLayout.LayoutParams(-1, dp(activity, 48)))
        val stats = LinearLayout(activity).apply { background = rounded(0xfffff4e6.toInt(), dp(activity, 18)); setPadding(dp(activity, 8), dp(activity, 12), dp(activity, 8), dp(activity, 12)) }
        stats.addView(metricSmall(activity, "0", "活跃发言人数"), LinearLayout.LayoutParams(0, dp(activity, 62), 1f)); stats.addView(metricSmall(activity, "0", "当前群聊总人数"), LinearLayout.LayoutParams(0, dp(activity, 62), 1f)); activityBody.addView(stats)
        activityBody.addView(label(activity, "查看/清理未发言成员（0人）", 15f, 0xffb82d25.toInt(), true).apply { gravity = Gravity.CENTER; setPadding(0, dp(activity, 12), 0, dp(activity, 12)); background = rounded(0xfff1e2df.toInt(), dp(activity, 15)) }, marginParams(activity, -1, 48, 0, 10, 0, 0))
        addCollapsible(activity, root, "♟", "群聊活跃检测", activityBody, false)
        val names = listOf("活跃发言排行" to "▥", "高频语义特征" to "✦", "聊天作息图鉴" to "◕", "情绪指纹探测" to "☺", "废话程度鉴定" to "π", "多维属性探测" to "◎", "全天活跃频次" to "⌁", "内容载体偏好" to "◌")
        val bodies = LinkedHashMap<String, LinearLayout>()
        names.forEach { pair ->
            val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 12)); background = rounded(0xfffff4e6.toInt(), dp(activity, 16)) }
            body.addView(label(activity, "正在读取群聊数据…", 14f, 0xff4e4b54.toInt(), false).apply { gravity = Gravity.CENTER; setPadding(0, dp(activity, 18), 0, dp(activity, 18)) })
            bodies[pair.first] = body
            addCollapsible(activity, root, pair.second, pair.first, body, false)
        }
        executor.execute {
            val details = runCatching { ConversationAnalysisDetailsRepository().load(talker, days) }.getOrElse {
                ConversationAnalysisDetails(emptyList(), emptyList(), ConversationDeepStats(IntArray(24), emptyMap(), 0, 0, 0, 0), it.message)
            }
            Handler(Looper.getMainLooper()).post {
                renderDeepData(activity, bodies, details)
                val active = details.ranking.size
                updateMetric(metrics, 0, active.toString())
            }
        }
        return root
    }

    private fun renderDeepData(activity: Activity, bodies: Map<String, LinearLayout>, details: ConversationAnalysisDetails) {
        if (details.error != null) {
            bodies.values.forEach { box -> box.removeAllViews(); box.addView(label(activity, details.error, 14f, 0xffb82d25.toInt(), false).apply { gravity = Gravity.CENTER }) }
            return
        }
        renderBars(activity, bodies["活跃发言排行"], details.ranking.take(10).map { it.name to it.count }, 0xfff57c00.toInt())
        renderBars(activity, bodies["高频语义特征"], listOf("平均文本长度" to details.deep.avgLength, "问题消息" to details.deep.questionCount, "欢乐表达" to details.deep.laughCount), 0xfff57c00.toInt())
        renderBars(activity, bodies["情绪指纹探测"], listOf("快乐浓度" to details.deep.laughCount, "疑惑指数" to details.deep.questionCount, "最长文本" to details.deep.longest), 0xfff57c00.toInt())
        renderBars(activity, bodies["废话程度鉴定"], listOf("平均文本长度" to details.deep.avgLength, "最长文本" to details.deep.longest), 0xff2196f3.toInt())
        renderBars(activity, bodies["多维属性探测"], listOf("活跃度" to details.ranking.size, "互动率" to details.deep.questionCount, "表达欲" to details.deep.avgLength, "含金量" to details.deep.longest), 0xff9c27b0.toInt())
        renderBars(activity, bodies["内容载体偏好"], details.deep.types.entries.map { "类型 ${it.key}" to it.value }, 0xfff57c00.toInt())
        val routine = bodies["聊天作息图鉴"]
        routine?.let {
            it.removeAllViews()
            val groups = listOf("早起鸟儿" to details.deep.hourly.slice(5..8).sum(), "带薪摸鱼" to details.deep.hourly.slice(9..17).sum(), "夜生活" to details.deep.hourly.slice(18..22).sum(), "熬夜修仙" to (details.deep.hourly[23] + details.deep.hourly.slice(0..4).sum()))
            groups.forEach { pair -> it.addView(label(activity, "${pair.first}    ${pair.second} 条", 16f, 0xffe65c00.toInt(), true).apply { setPadding(0, dp(activity, 9), 0, dp(activity, 9)) }) }
        }
        renderHourly(activity, bodies["全天活跃频次"], details.deep.hourly)
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
            row.addView(SeekBar(activity).apply { isEnabled = false; this.max = max; progress = pair.second; progressTintList = android.content.res.ColorStateList.valueOf(color); progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xffffe0bd.toInt()) }, LinearLayout.LayoutParams(-1, dp(activity, 24)))
            box.addView(row)
        }
    }

    private fun renderHourly(activity: Activity, box: LinearLayout?, values: IntArray) {
        if (box == null) return
        box.removeAllViews()
        box.addView(HourlyBarsView(activity, values), LinearLayout.LayoutParams(-1, dp(activity, 150)))
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
    private fun metricSmall(activity: Activity, value: String, title: String) = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; addView(label(activity, value, 23f, 0xff292522.toInt(), true)); addView(label(activity, title, 11f, 0xff4e4b54.toInt(), false)) }
    private fun divider(activity: Activity) = View(activity).apply { setBackgroundColor(0xffeee2d6.toInt()) }
    private fun LinearLayout.children(): List<View> = (0 until childCount).map { getChildAt(it) }

    private fun showAiConfig(activity: Activity) {
        val ai = ConversationAnalysisAi(activity)
        val store = ConversationAnalysisAiConfigStore(activity)
        var selected = store.activeProfileId()
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 18), dp(activity, 16), dp(activity, 18), dp(activity, 16)) }
        val profileBar = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val fields = arrayOf(EditText(activity), EditText(activity), EditText(activity), EditText(activity), EditText(activity))
        fields[0].hint = "方案名称"; fields[1].hint = "API 地址"; fields[2].hint = "API 路径"; fields[3].hint = "API Key"; fields[4].hint = "模型名称"
        fields.forEach { root.addView(it, LinearLayout.LayoutParams(-1, dp(activity, 46))) }
        val capacity = EditText(activity).apply { hint = "上下文容量：128k / 256k / 512k / 1m / 2m" }
        val limit = EditText(activity).apply { hint = "消息上限，0 为自动"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        root.addView(capacity, LinearLayout.LayoutParams(-1, dp(activity, 46)))
        root.addView(limit, LinearLayout.LayoutParams(-1, dp(activity, 46)))
        fun loadProfile(id: Int) { selected = id; val p = ai.profiles()[id - 1]; fields[0].setText(p.name); fields[1].setText(p.address); fields[2].setText(p.path); fields[3].setText(p.key); fields[4].setText(p.model) }
        (1..2).forEach { id -> profileBar.addView(Button(activity).apply { text = "方案$id"; setAllCaps(false); setOnClickListener { loadProfile(id) } }, LinearLayout.LayoutParams(0, dp(activity, 46), 1f)) }
        root.addView(profileBar, 0)
        lateinit var dialog: Dialog
        val save = Button(activity).apply { text = "保存并启用"; setOnClickListener { ai.saveProfile(ConversationAiProfile(selected, fields[0].text.toString(), fields[1].text.toString(), fields[2].text.toString(), fields[3].text.toString(), fields[4].text.toString())); ai.selectProfile(selected); ConversationAnalysisAiConfigStore(activity).setSampling(capacity.text.toString(), limit.text.toString().toIntOrNull() ?: 0); dialog.dismiss() } }
        root.addView(save)
        dialog = Dialog(activity)
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener { dialog.window?.setLayout(-1, -1) }
        dialog.show()
        loadProfile(selected)
    }

    private fun showAi(activity: Activity, reportDialog: Dialog, talker: String, days: Int, logger: (String, Throwable?) -> Unit) {
        val ai = ConversationAnalysisAi(activity)
        if (!ai.configured()) {
            val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(activity, 20), dp(activity, 16), dp(activity, 20), dp(activity, 16)) }
            val address = EditText(activity).apply { hint = "API 地址" }
            val path = EditText(activity).apply { hint = "API 路径"; setText("/chat/completions") }
            val key = EditText(activity).apply { hint = "API Key" }
            val model = EditText(activity).apply { hint = "模型名称" }
            listOf(address, path, key, model).forEach { box.addView(it, LinearLayout.LayoutParams(-1, dp(activity, 48))) }
            lateinit var configDialog: Dialog
            val save = Button(activity).apply { text = "保存并生成"; setOnClickListener { ai.save(address.text.toString(), path.text.toString(), key.text.toString(), model.text.toString()); configDialog.dismiss(); showAi(activity, reportDialog, talker, days, logger) } }
            box.addView(save)
            configDialog = Dialog(activity)
            configDialog.setContentView(box)
            configDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            configDialog.setOnShowListener { configDialog.window?.setLayout(-1, -1) }
            configDialog.show()
            return
        }
        val reportView = ConversationAnalysisAiReportView(activity)
        reportView.update(talker, days, "正在生成…")
        val resultDialog = Dialog(activity)
        resultDialog.setTitle("AI 群聊总结 · 方案${ai.activeProfileId()}")
        val wrapper = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        wrapper.addView(ScrollView(activity).apply { addView(reportView) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val send = Button(activity).apply { text = "发送 AI 报告截图"; setAllCaps(false); setOnClickListener { captureAndSend(activity, reportView, talker) } }
        wrapper.addView(send, LinearLayout.LayoutParams(-1, dp(activity, 48)))
        resultDialog.setContentView(wrapper)
        resultDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        resultDialog.setOnShowListener { resultDialog.window?.setLayout(-1, -1) }
        resultDialog.setOnDismissListener { ai.cancel() }
        resultDialog.show()
        ai.generateStreaming(talker, days, { partial -> reportView.update(talker, days, partial) }, { final -> reportView.update(talker, days, final) })
    }

    private fun captureAndSend(activity: Activity, root: View, talker: String) {
        ConversationAnalysisScreenshot.capture(root, File(activity.cacheDir, "conversation_analysis")) { file ->
            if (file == null || WeChatApis.media()?.sendImage(talker, file.absolutePath) != true) Toast.makeText(activity, "截图发送失败", Toast.LENGTH_SHORT).show()
            else Toast.makeText(activity, "截图已发送", Toast.LENGTH_SHORT).show()
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
    private fun dp(activity: Activity, value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
