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
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
        dialog.setTitle("分析报告")
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 16), dp(activity, 18), dp(activity, 14))
            setBackgroundColor(Color.rgb(250, 250, 250))
        }
        val title = TextView(activity).apply {
            text = "群聊分析报告"
            textSize = 24f
            setTextColor(Color.rgb(35, 35, 35))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(activity, 42)))
        val range = TextView(activity).apply {
            text = rangeText(1)
            textSize = 15f
            setTextColor(Color.rgb(38, 150, 92))
        }
        root.addView(range, LinearLayout.LayoutParams(-1, dp(activity, 28)))

        val periodLabel = TextView(activity).apply {
            text = "统计周期：1 天"
            textSize = 14f
            setTextColor(Color.DKGRAY)
        }
        root.addView(periodLabel, LinearLayout.LayoutParams(-1, dp(activity, 28)))
        val period = SeekBar(activity).apply {
            max = 59
            progress = 0
        }
        root.addView(period, LinearLayout.LayoutParams(-1, dp(activity, 38)))

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(activity).apply { addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val close = TextView(activity).apply {
            text = "关闭"
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.rgb(38, 150, 92))
            setOnClickListener { dialog.dismiss() }
        }
        val actions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        fun action(text: String, click: () -> Unit) = Button(activity).apply { this.text = text; setAllCaps(false); setOnClickListener { click() } }
        val detailsButton = action("排行与深度") { loadDetails(activity, dialog, talker, period.progress + 1, logger) }
        val aiButton = action("AI 总结") { showAi(activity, dialog, talker, period.progress + 1, logger) }
        val aiConfigButton = action("AI 配置") { showAiConfig(activity) }
        val screenshotButton = action("截图发送") { captureAndSend(activity, root, talker) }
        actions.addView(detailsButton, LinearLayout.LayoutParams(0, dp(activity, 46), 1f))
        actions.addView(aiButton, LinearLayout.LayoutParams(0, dp(activity, 46), 1f))
        actions.addView(aiConfigButton, LinearLayout.LayoutParams(0, dp(activity, 46), 1f))
        actions.addView(screenshotButton, LinearLayout.LayoutParams(0, dp(activity, 46), 1f))
        root.addView(actions)
        root.addView(close, LinearLayout.LayoutParams(-1, dp(activity, 46)))
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(dp(activity, 360), dp(activity, 620))
        period.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                val days = value + 1
                periodLabel.text = "统计周期：$days 天"
                range.text = rangeText(days)
                load(activity, dialog, content, talker, days, logger)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.94f).toInt(),
                (activity.resources.displayMetrics.heightPixels * 0.86f).toInt()
            )
            load(activity, dialog, content, talker, 1, logger)
        }
        dialog.show()
    }

    private fun load(
        activity: Activity,
        dialog: Dialog,
        content: LinearLayout,
        talker: String,
        days: Int,
        logger: (String, Throwable?) -> Unit
    ) {
        content.removeAllViews()
        content.addView(message(activity, "正在读取群聊消息…", Color.GRAY))
        executor.execute {
            val result = runCatching { ConversationAnalysisRepository().loadSummary(talker, days) }
                .onFailure { logger("群聊分析报告读取失败", it) }
                .getOrElse { error ->
                    ConversationAnalysisSummary(talker, 0, 0, 0, 0, 0, 0, 0, 0, error.message)
                }
            Handler(Looper.getMainLooper()).post {
                if (!dialog.isShowing) return@post
                content.removeAllViews()
                if (result.error != null) {
                    content.addView(message(activity, result.error, Color.rgb(190, 70, 55)))
                    return@post
                }
                val grid = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                addMetric(activity, grid, "人数", "${result.periodSpeakers} / ${result.groupMemberCount}", ConversationAnalysisIconView.Kind.PEOPLE)
                addMetric(activity, grid, "消息", result.periodTotal.toString(), ConversationAnalysisIconView.Kind.MESSAGE)
                addMetric(activity, grid, "历史总数", result.historyTotal.toString(), ConversationAnalysisIconView.Kind.HISTORY)
                addMetric(activity, grid, "文本消息", result.textCount.toString(), ConversationAnalysisIconView.Kind.REPORT)
                content.addView(grid)
                content.addView(message(activity, "问题消息：${result.questionCount}    欢乐表达：${result.laughCount}", Color.DKGRAY))
                content.addView(message(activity, "活跃成员：${result.activeMemberCount}    当前周期：${days} 天", Color.DKGRAY))
            }
        }
    }

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
        dialog = Dialog(activity); dialog.setTitle("AI 双方案与采样配置"); dialog.setContentView(root); dialog.show(); loadProfile(selected)
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
            configDialog.setTitle("AI 接口配置")
            configDialog.setContentView(box)
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
