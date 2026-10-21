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
import t.mma.hooks.items.emojiat.EmojiAtSettings
import t.mma.ui.FeatureSettingsProvider

@Composable
internal fun EmojiAtMiuixPage(context: Context, provider: FeatureSettingsProvider, onBack: () -> Unit) {
    val prefs = remember { EmojiAtSettings.preferences(context) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(EmojiAtSettings.ENABLE, false)) }
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(scrollBehavior = scrollBehavior, title = provider.title(), largeTitle = provider.title(), bottomBar = {
        BottomActionBar(primaryText = "返回", onPrimaryClick = onBack)
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection), contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)) {
            item { SettingsCard {
                SwitchRow(enabled, "表情艾特", "群聊表情可选择普通发送或艾特发送；设置即时保存") {
                    enabled = it; prefs.edit().putBoolean(EmojiAtSettings.ENABLE, it).apply()
                }
            } }
            item { SettingsCard {
                InfoRow("使用说明", "发送群聊自定义表情时选择发送方式。艾特发送最多附带499位其他成员的隐藏艾特名单，不额外发送文字；取消弹窗不发送。")
                InfoRow("适配说明", "仅在发送链路结构检查通过后提供选择；实际提醒效果需双端验证。499是本功能的名单上限。")
                InfoRow("独立运行", "无需启用脚本插件系统；使用时请停用原表情艾特脚本，避免重复处理。")
            } }
        }
    }
}