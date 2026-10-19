package t.mma.ui.miuix

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import t.mma.hooks.items.homeextension.HomeExtensionSettings as Settings
import t.mma.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
internal fun HomeExtensionMiuixPage(context: Context, provider: FeatureSettingsProvider, onBack: () -> Unit) {
    val prefs = remember { Settings.preferences(context) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(Settings.ENABLE, false)) }
    var calendar by remember { mutableStateOf(prefs.getBoolean(Settings.CALENDAR, true)) }
    var recent by remember { mutableStateOf(prefs.getBoolean(Settings.RECENT, true)) }
    var calendarAlpha by remember { mutableStateOf(prefs.getInt(Settings.CALENDAR_ALPHA, 75).coerceIn(0, 100).toFloat()) }
    var recentAlpha by remember { mutableStateOf(prefs.getInt(Settings.RECENT_ALPHA, 75).coerceIn(0, 100).toFloat()) }
    var greeting by remember { mutableStateOf(prefs.getString(Settings.GREETING, Settings.DEFAULT_GREETING).orEmpty()) }
    val behavior = MiuixScrollBehavior()
    fun save() {
        prefs.edit().putBoolean(Settings.ENABLE, enabled).putBoolean(Settings.CALENDAR, calendar)
            .putBoolean(Settings.RECENT, recent).putInt(Settings.CALENDAR_ALPHA, calendarAlpha.roundToInt())
            .putInt(Settings.RECENT_ALPHA, recentAlpha.roundToInt()).putString(Settings.GREETING, greeting.take(120)).apply()
        Toast.makeText(context, "首页扩展设置已保存，返回微信首页查看", Toast.LENGTH_SHORT).show()
    }
    PageScaffold(title = provider.title(), largeTitle = provider.title(), scrollBehavior = behavior,
        bottomBar = { BottomActionBar(primaryText = "保存设置", onPrimaryClick = ::save, secondaryText = "返回", onSecondaryClick = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection), contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)) {
            item {
                SettingsCard {
                    SwitchRow(checked = enabled, title = "首页扩展", summary = "在聊天列表顶部显示扩展内容，随列表向上滚动移出屏幕", onCheckedChange = { enabled = it })
                }
            }
            if (enabled) {
                item {
                    SettingsCard {
                        Column {
                            SwitchRow(checked = calendar, title = "首页日历", summary = "本周日期、今日高亮与自定义问候语", onCheckedChange = { calendar = it })
                            if (calendar) {
                                HomeExtensionOpacity(calendarAlpha) { calendarAlpha = it }
                                InputRow(title = "问候语", summary = "最多 120 字，可留空", value = greeting, onValueChange = { greeting = it.take(120) })
                            }
                        }
                    }
                }
                item {
                    SettingsCard {
                        Column {
                            SwitchRow(checked = recent, title = "最近联系", summary = "按最近消息时间显示联系人和群聊，点击头像进入会话", onCheckedChange = { recent = it })
                            if (recent) HomeExtensionOpacity(recentAlpha) { recentAlpha = it }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeExtensionOpacity(value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("背景透明度 ${100 - value.roundToInt()}%", color = MiuixTheme.colorScheme.onSurface)
        Slider(value = 100f - value, onValueChange = { onChange(100f - it.roundToInt().coerceIn(0, 100)) }, valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth())
        Text("0% 为不透明，100% 为完全透明；文字和头像保持清晰", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}