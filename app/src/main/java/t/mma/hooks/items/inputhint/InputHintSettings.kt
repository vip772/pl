package t.mma.hooks.items.inputhint

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import t.mma.preferences.HchatStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InputHintStats(
    val totalMessages: Long = 0L,
    val textMessages: Long = 0L,
    val textCharacters: Long = 0L,
    val emojiMessages: Long = 0L,
    val transferMessages: Long = 0L,
    val redPacketMessages: Long = 0L,
    val fileMessages: Long = 0L
)

object InputHintSettings {
    const val PREFS_NAME = "Hchat_input_hint_config"

    const val KEY_ENABLE = "input_hint_enable"
    const val KEY_TEMPLATE = "input_hint_template"
    const val KEY_STATISTICS_ENABLE = "input_hint_statistics_enable"
    const val KEY_STATISTICS_DAY = "input_hint_statistics_day"
    const val KEY_TOTAL_MESSAGES = "input_hint_total_messages"
    const val KEY_TEXT_MESSAGES = "input_hint_text_messages"
    const val KEY_TEXT_CHARACTERS = "input_hint_text_characters"
    const val KEY_EMOJI_MESSAGES = "input_hint_emoji_messages"
    const val KEY_TRANSFER_MESSAGES = "input_hint_transfer_messages"
    const val KEY_RED_PACKET_MESSAGES = "input_hint_red_packet_messages"
    const val KEY_FILE_MESSAGES = "input_hint_file_messages"

    const val DEFAULT_ENABLE = false
    const val DEFAULT_STATISTICS_ENABLE = true
    const val DEFAULT_VARIABLE_COLOR = "#FF3B82F6"

    const val VAR_TOTAL_MESSAGES = "\${totalMsg}"
    const val VAR_TEXT_MESSAGES = "\${textMsg}"
    const val VAR_TEXT_CHARACTERS = "\${textWord}"
    const val VAR_EMOJI_MESSAGES = "\${emojiMsg}"
    const val VAR_TRANSFER_MESSAGES = "\${transferMsg}"
    const val VAR_RED_PACKET_MESSAGES = "\${redBagMsg}"
    const val VAR_FILE_MESSAGES = "\${fileMsg}"

    const val DEFAULT_TEMPLATE = "今日已发\${totalMsg}条"

    fun preferences(context: Context): SharedPreferences =
        HchatStorage.preferences(context, PREFS_NAME)

    fun template(preferences: SharedPreferences): String = normalizeTemplate(
        preferences.getString(KEY_TEMPLATE, DEFAULT_TEMPLATE)
    )

    fun normalizeTemplate(value: String?): String = value.orEmpty().ifBlank { DEFAULT_TEMPLATE }
    private val variableTokens = listOf(VAR_TOTAL_MESSAGES, VAR_TEXT_MESSAGES, VAR_TEXT_CHARACTERS, VAR_EMOJI_MESSAGES, VAR_TRANSFER_MESSAGES, VAR_RED_PACKET_MESSAGES, VAR_FILE_MESSAGES)
    private fun colorKey(token: String, suffix: String) = "input_hint_color_${token.substring(2, token.length - 1)}_$suffix"
    fun variableColors(preferences: SharedPreferences): Map<String, Pair<String, String>> = variableTokens.associateWith { token -> preferences.getString(colorKey(token, "start"), DEFAULT_VARIABLE_COLOR).orEmpty() to preferences.getString(colorKey(token, "end"), DEFAULT_VARIABLE_COLOR).orEmpty() }
    fun variableColorKey(token: String, end: Boolean): String = colorKey(token, if (end) "end" else "start")

    @Suppress("UNUSED_PARAMETER")
    fun readTodayStats(
        preferences: SharedPreferences,
        now: Long = System.currentTimeMillis()
    ): InputHintStats = OutgoingMessageStatsRepository.current()

    fun renderTemplate(template: String, stats: InputHintStats): String = renderTemplateSpanned(template, stats, emptyMap()).toString()

    fun renderTemplateSpanned(template: String, stats: InputHintStats, colors: Map<String, Pair<String, String>>): CharSequence {
        val source = normalizeTemplate(template)
        val values = linkedMapOf(
            VAR_TOTAL_MESSAGES to stats.totalMessages.toString(), VAR_TEXT_MESSAGES to stats.textMessages.toString(),
            VAR_TEXT_CHARACTERS to stats.textCharacters.toString(), VAR_EMOJI_MESSAGES to stats.emojiMessages.toString(),
            VAR_TRANSFER_MESSAGES to stats.transferMessages.toString(), VAR_RED_PACKET_MESSAGES to stats.redPacketMessages.toString(), VAR_FILE_MESSAGES to stats.fileMessages.toString()
        )
        val result = SpannableStringBuilder(); var index = 0
        while (index < source.length) {
            val token = values.keys.firstOrNull { source.startsWith(it, index) }
            if (token == null) { result.append(source[index]); index++; continue }
            val begin = result.length; result.append(values.getValue(token)); val finish = result.length
            val pair = colors[token]; val start = parseColor(pair?.first) ?: Color.BLUE; val end = parseColor(pair?.second) ?: start
            result.setSpan(ForegroundColorSpan(start), begin, finish, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (start != end) result.setSpan(VariableGradientSpan(start, end), begin, finish, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            index += token.length
        }
        return result
    }
    private fun parseColor(value: String?): Int? = runCatching { Color.parseColor(value?.trim().orEmpty()) }.getOrNull()
    private class VariableGradientSpan(private val start: Int, private val end: Int) : CharacterStyle() {
        override fun equals(other: Any?): Boolean =
            other is VariableGradientSpan && start == other.start && end == other.end
        override fun hashCode(): Int = 31 * start + end
        override fun updateDrawState(tp: TextPaint) { tp.shader = android.graphics.LinearGradient(0f, 0f, tp.textSize * 4f, 0f, start, end, android.graphics.Shader.TileMode.CLAMP) }
    }

    fun dayKey(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))
}
