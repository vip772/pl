package t.mma.ui.miuix

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import t.mma.hooks.items.hiddenat.HiddenAtSettings
import t.mma.ui.FeatureSettingsProvider

@Composable
internal fun HiddenAtMiuixPage(context: Context, provider: FeatureSettingsProvider, onBack: () -> Unit) {
    val prefs = remember { HiddenAtSettings.preferences(context) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(HiddenAtSettings.ENABLE, false)) }
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(scrollBehavior = scrollBehavior, title = provider.title(), largeTitle = provider.title(), bottomBar = {
        BottomActionBar(primaryText = "返回", onPrimaryClick = onBack)
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection), contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)) {
            item { SettingsCard {
                SwitchRow(enabled, "隐藏艾特", "隐藏群聊消息中的艾特文字，保留艾特名单；设置即时保存") {
                    enabled = it; prefs.edit().putBoolean(HiddenAtSettings.ENABLE, it).apply()
                }
            } }
            item { SettingsCard {
                InfoRow("使用说明", "在群聊中按微信原有方式选择成员并发送文字。仅转换可准确识别的艾特消息，其他消息保留原样。")
                InfoRow("独立运行", "无需启用脚本插件系统；使用时请停用原隐藏艾特脚本，避免重复处理。")
            } }
        }
    }
}