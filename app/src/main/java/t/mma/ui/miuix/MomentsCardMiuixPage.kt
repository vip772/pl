package t.mma.ui.miuix

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import t.mma.hooks.items.antirecall.DynamicRecallGradient
import t.mma.hooks.items.moments.MomentsCardSettings as Settings
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

@Composable
internal fun MomentsCardMiuixPage(context: Context, provider: FeatureSettingsProvider, onBack: () -> Unit) {
    val prefs = remember(context) { HchatStorage.preferences(context, Settings.PREFS_NAME) }
    var enabled by remember(prefs) { mutableStateOf(prefs.getBoolean(Settings.ENABLE, false)) }
    var border by remember(prefs) { mutableStateOf(prefs.getBoolean(Settings.BORDER, false)) }
    var colors by remember(prefs) { mutableStateOf(prefs.getString(Settings.COLORS, Settings.DEFAULT_COLORS).orEmpty()) }
    val scroll = MiuixScrollBehavior()
    PageScaffold(title = provider.title(), largeTitle = provider.title(), scrollBehavior = scroll, onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)
        ) {
            item { SettingsCard {
                SwitchRow(enabled, "卡片模式", "每条动态显示为独立卡片，卡片间距 2dp") {
                    enabled = it
                    prefs.edit().putBoolean(Settings.ENABLE, it).apply()
                }
                if (enabled) {
                    InsetDivider()
                    SwitchRow(border, "描边颜色", "为每张卡片添加 1dp 三色渐变描边") {
                        border = it
                        prefs.edit().putBoolean(Settings.BORDER, it).apply()
                    }
                    if (border) {
                        InsetDivider()
                        ColorPickerRow(
                            "三色渐变颜色", "分别编辑起始色、中间色、结束色", colors,
                            threeColorGradient = true,
                            onReset = {
                                colors = Settings.DEFAULT_COLORS
                                prefs.edit().putString(Settings.COLORS, colors).apply()
                            }
                        ) { value ->
                            colors = DynamicRecallGradient.parts(value).joinToString(",")
                            prefs.edit().putString(Settings.COLORS, colors).apply()
                        }
                    }
                }
            } }
        }
    }
}
