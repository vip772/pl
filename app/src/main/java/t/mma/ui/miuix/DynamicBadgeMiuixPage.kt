package t.mma.ui.miuix

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import t.mma.hooks.items.dynamicbadge.DynamicBadgeSettings
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DynamicBadgeMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, DynamicBadgeSettings.PREFS_NAME) }
    var enabled by remember { mutableStateOf(sp.getBoolean(DynamicBadgeSettings.KEY_ENABLE, false)) }
    var numberEnabled by remember { mutableStateOf(sp.getBoolean(DynamicBadgeSettings.KEY_NUMBER_ENABLE, true)) }
    var dotEnabled by remember { mutableStateOf(sp.getBoolean(DynamicBadgeSettings.KEY_DOT_ENABLE, true)) }
    var numberStart by remember { mutableStateOf(colorString(sp.getInt(DynamicBadgeSettings.KEY_NUMBER_START, DynamicBadgeSettings.DEFAULT_NUMBER_START))) }
    var numberEnd by remember { mutableStateOf(colorString(sp.getInt(DynamicBadgeSettings.KEY_NUMBER_END, DynamicBadgeSettings.DEFAULT_NUMBER_END))) }
    var dotStart by remember { mutableStateOf(colorString(sp.getInt(DynamicBadgeSettings.KEY_DOT_START, DynamicBadgeSettings.DEFAULT_DOT_START))) }
    var dotEnd by remember { mutableStateOf(colorString(sp.getInt(DynamicBadgeSettings.KEY_DOT_END, DynamicBadgeSettings.DEFAULT_DOT_END))) }
    var numberSpeed by remember { mutableStateOf(sp.getInt(DynamicBadgeSettings.KEY_NUMBER_SPEED, DynamicBadgeSettings.DEFAULT_NUMBER_SPEED)) }
    var dotSpeed by remember { mutableStateOf(sp.getInt(DynamicBadgeSettings.KEY_DOT_SPEED, DynamicBadgeSettings.DEFAULT_DOT_SPEED)) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("保存设置", {
            val editor = sp.edit()
                .putBoolean(DynamicBadgeSettings.KEY_ENABLE, enabled)
                .putBoolean(DynamicBadgeSettings.KEY_NUMBER_ENABLE, numberEnabled)
                .putBoolean(DynamicBadgeSettings.KEY_DOT_ENABLE, dotEnabled)
                .putInt(DynamicBadgeSettings.KEY_NUMBER_SPEED, numberSpeed)
                .putInt(DynamicBadgeSettings.KEY_DOT_SPEED, dotSpeed)
            parseColor(numberStart)?.let { editor.putInt(DynamicBadgeSettings.KEY_NUMBER_START, it) }
            parseColor(numberEnd)?.let { editor.putInt(DynamicBadgeSettings.KEY_NUMBER_END, it) }
            parseColor(dotStart)?.let { editor.putInt(DynamicBadgeSettings.KEY_DOT_START, it) }
            parseColor(dotEnd)?.let { editor.putInt(DynamicBadgeSettings.KEY_DOT_END, it) }
            editor.apply()
        }, "返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)
        ) {
            item { SwitchRow(enabled, "动态角标", "启用会话列表未读角标动画", onCheckedChange = { enabled = it }) }
            if (enabled) {
                item { SwitchRow(numberEnabled, "数字角标", "数字和填充保持原样，仅绘制旋转渐变描边", onCheckedChange = { numberEnabled = it }) }
                if (numberEnabled) {
                    item { colorInput("数字渐变颜色 A", numberStart) { numberStart = it } }
                    item { colorInput("数字渐变颜色 B", numberEnd) { numberEnd = it } }
                    item { speedControl("描边旋转周期", numberSpeed) { numberSpeed = it } }
                }
                item { SwitchRow(dotEnabled, "圆点角标", "保持原位置和尺寸，以渐变透明度周期闪烁", onCheckedChange = { dotEnabled = it }) }
                if (dotEnabled) {
                    item { colorInput("圆点渐变颜色 A", dotStart) { dotStart = it } }
                    item { colorInput("圆点渐变颜色 B", dotEnd) { dotEnd = it } }
                    item { speedControl("闪烁周期", dotSpeed) { dotSpeed = it } }
                }
            }
        }
    }
}

@Composable
private fun colorInput(title: String, value: String, onValue: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(title, color = MiuixTheme.colorScheme.onSurface, fontSize = 14.sp)
            Text(
                text = "#",
                modifier = Modifier.padding(start = 10.dp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            BasicTextField(
                value = value,
                onValueChange = { input ->
                    onValue(input.filter { it.isDigit() || it in "abcdefABCDEF" }.take(8))
                },
                singleLine = true,
                textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 14.sp),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                modifier = Modifier.weight(1f)
                    .padding(start = 6.dp)
                    .background(MiuixTheme.colorScheme.secondaryVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text("RRGGBB 或 AARRGGBB", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        innerTextField()
                    }
                }
            )
        }
        Text(
            text = "支持 6 位 RGB 或 8 位 ARGB 十六进制色值",
            modifier = Modifier.padding(top = 5.dp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun speedControl(title: String, value: Int, onValue: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        SmallTitle(text = "$title ${value}ms")
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.toInt().coerceIn(DynamicBadgeSettings.MIN_SPEED, DynamicBadgeSettings.MAX_SPEED)) },
            modifier = Modifier.fillMaxWidth(),
            valueRange = DynamicBadgeSettings.MIN_SPEED.toFloat()..DynamicBadgeSettings.MAX_SPEED.toFloat(),
            steps = 15
        )
    }
}

private fun colorString(color: Int): String = color.toUInt().toString(16).uppercase().takeLast(8)

private fun parseColor(value: String): Int? {
    val normalized = value.removePrefix("#")
    if (normalized.length != 6 && normalized.length != 8) return null
    val parsed = normalized.toLongOrNull(16)?.toInt() ?: return null
    return if (normalized.length == 6) (0xFF000000L or parsed.toLong()).toInt() else parsed
}
