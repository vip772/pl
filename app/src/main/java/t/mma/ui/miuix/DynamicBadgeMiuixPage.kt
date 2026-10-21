package t.mma.ui.miuix

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import t.mma.hooks.items.dynamicbadge.DynamicBadgeSettings
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun DynamicBadgeMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, DynamicBadgeSettings.PREFS_NAME) }
    var enabled by remember { mutableStateOf(sp.getBoolean(DynamicBadgeSettings.KEY_ENABLE, false)) }
    var motionStyle by remember { mutableStateOf(sp.getInt(DynamicBadgeSettings.KEY_STYLE, 0).coerceIn(0, 2)) }
    var numberEnabled by remember { mutableStateOf(sp.getBoolean(DynamicBadgeSettings.KEY_NUMBER_ENABLE, true)) }
    var dotEnabled by remember { mutableStateOf(sp.getBoolean(DynamicBadgeSettings.KEY_DOT_ENABLE, true)) }
    var numberColor by remember {
        val legacyStart = sp.getInt(DynamicBadgeSettings.KEY_NUMBER_START, Int.MIN_VALUE)
        val legacyEnd = sp.getInt(DynamicBadgeSettings.KEY_NUMBER_END, Int.MIN_VALUE)
        val saved = sp.getString(DynamicBadgeSettings.KEY_NUMBER_COLOR, null)
        mutableStateOf(
            saved ?: if (legacyStart != Int.MIN_VALUE || legacyEnd != Int.MIN_VALUE) {
                legacyColorPair(legacyStart, legacyEnd)
            } else DynamicBadgeSettings.DEFAULT_NUMBER_COLOR
        )
    }
    var dotColor by remember {
        val legacyStart = sp.getInt(DynamicBadgeSettings.KEY_DOT_START, Int.MIN_VALUE)
        val legacyEnd = sp.getInt(DynamicBadgeSettings.KEY_DOT_END, Int.MIN_VALUE)
        val saved = sp.getString(DynamicBadgeSettings.KEY_DOT_COLOR, null)
        mutableStateOf(
            saved ?: if (legacyStart != Int.MIN_VALUE || legacyEnd != Int.MIN_VALUE) {
                legacyColorPair(legacyStart, legacyEnd)
            } else DynamicBadgeSettings.DEFAULT_DOT_COLOR
        )
    }
    var numberSpeed by remember { mutableStateOf(sp.getInt(DynamicBadgeSettings.KEY_NUMBER_SPEED, DynamicBadgeSettings.DEFAULT_NUMBER_SPEED)) }
var dotSpeed by remember { mutableStateOf(sp.getInt(DynamicBadgeSettings.KEY_DOT_SPEED, DynamicBadgeSettings.DEFAULT_DOT_SPEED)) }
    var numberStrokeWidth by remember { mutableStateOf(sp.getInt(DynamicBadgeSettings.KEY_NUMBER_STROKE_WIDTH, DynamicBadgeSettings.DEFAULT_NUMBER_STROKE_WIDTH)) }
    var dotStrokeWidth by remember { mutableStateOf(sp.getInt(DynamicBadgeSettings.KEY_DOT_STROKE_WIDTH, DynamicBadgeSettings.DEFAULT_DOT_STROKE_WIDTH)) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("保存设置", {
            val editor = sp.edit()
                .putBoolean(DynamicBadgeSettings.KEY_ENABLE, enabled)
                .putInt(DynamicBadgeSettings.KEY_STYLE, motionStyle)
                .putBoolean(DynamicBadgeSettings.KEY_NUMBER_ENABLE, numberEnabled)
                .putBoolean(DynamicBadgeSettings.KEY_DOT_ENABLE, dotEnabled)
                .putInt(DynamicBadgeSettings.KEY_NUMBER_SPEED, numberSpeed)
                .putInt(DynamicBadgeSettings.KEY_DOT_SPEED, dotSpeed)
                .putInt(DynamicBadgeSettings.KEY_NUMBER_STROKE_WIDTH, numberStrokeWidth)
                .putInt(DynamicBadgeSettings.KEY_DOT_STROKE_WIDTH, dotStrokeWidth)
                .putString(DynamicBadgeSettings.KEY_NUMBER_COLOR, numberColor)
                .putString(DynamicBadgeSettings.KEY_DOT_COLOR, dotColor)
                .remove(DynamicBadgeSettings.KEY_NUMBER_START)
                .remove(DynamicBadgeSettings.KEY_NUMBER_END)
                .remove(DynamicBadgeSettings.KEY_DOT_START)
                .remove(DynamicBadgeSettings.KEY_DOT_END)
            editor.commit()
            Toast.makeText(context, "动态角标设置已保存", Toast.LENGTH_SHORT).show()
        }, "返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)
        ) {
            item { SwitchRow(enabled, "动态角标", "启用主界面未读角标动态效果", onCheckedChange = { enabled = it }) }
            if (enabled) {
                item { SmallTitle(text = "动效样式") }
                item { SettingsCard {
                    PopupChoiceRow("特效样式", "选择动态显示效果",
                        listOf(PopupChoice("现有效果", "0"), PopupChoice("闪电环绕", "1"), PopupChoice("蝌蚪环绕", "2")),
                        motionStyle.toString(), { value -> motionStyle = value.toInt() })
                } }
                item { SwitchRow(numberEnabled, "数字角标", "数字和填充保持原样，仅绘制旋转渐变描边", onCheckedChange = { numberEnabled = it }) }
                if (numberEnabled) {
                    item {
                        ColorPickerRow(
                            "数字角标渐变颜色",
                            "描边旋转渐变色",
                            numberColor,
                            onReset = { numberColor = DynamicBadgeSettings.DEFAULT_NUMBER_COLOR }
                        ) { numberColor = it.take(19) }
                    }
                    item { speedControl("描边旋转周期", numberSpeed) { numberSpeed = it } }
                    item { strokeWidthControl("数字角标描边宽度", numberStrokeWidth) { numberStrokeWidth = it } }
                }
                item { SwitchRow(dotEnabled, "圆点角标", "保持原位置和尺寸，以渐变透明度周期闪烁", onCheckedChange = { dotEnabled = it }) }
                if (dotEnabled) {
                    item {
                        ColorPickerRow(
                            "圆点角标渐变颜色",
                            "闪烁光环渐变色",
                            dotColor,
                            onReset = { dotColor = DynamicBadgeSettings.DEFAULT_DOT_COLOR }
                        ) { dotColor = it.take(19) }
                    }
                    item { speedControl("闪烁周期", dotSpeed) { dotSpeed = it } }
                    item { strokeWidthControl("圆点角标描边宽度", dotStrokeWidth) { dotStrokeWidth = it } }
                }
            }
        }
    }
}
@Composable
internal fun strokeWidthControl(title: String, value: Int, onValue: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        SmallTitle(text = "$title ${value}dp")
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.toInt().coerceIn(DynamicBadgeSettings.MIN_STROKE_WIDTH, DynamicBadgeSettings.MAX_STROKE_WIDTH)) },
            modifier = Modifier.fillMaxWidth(),
            valueRange = DynamicBadgeSettings.MIN_STROKE_WIDTH.toFloat()..DynamicBadgeSettings.MAX_STROKE_WIDTH.toFloat(),
            steps = DynamicBadgeSettings.MAX_STROKE_WIDTH - DynamicBadgeSettings.MIN_STROKE_WIDTH - 1
        )
    }
}

@Composable
internal fun speedControl(title: String, value: Int, onValue: (Int) -> Unit) {
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

private fun legacyColorPair(start: Int, end: Int): String {
    val first = if (start != Int.MIN_VALUE) start else end
    val second = if (end != Int.MIN_VALUE) end else first
    return "#%06X,#%06X".format(first and 0xFFFFFF, second and 0xFFFFFF)
}
