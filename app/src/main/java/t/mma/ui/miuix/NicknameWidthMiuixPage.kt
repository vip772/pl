package t.mma.ui.miuix

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import t.mma.hooks.items.nicknamewidth.NicknameWidthSettings
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

@Composable
internal fun NicknameWidthMiuixPage(context: Context, provider: FeatureSettingsProvider, onBack: () -> Unit) {
    val prefs = remember { HchatStorage.preferences(context, NicknameWidthSettings.PREFS) }
    val behavior = MiuixScrollBehavior()
    PageScaffold(title = provider.title(), largeTitle = provider.title(), scrollBehavior = behavior,
        bottomBar = { BottomActionBar(primaryText = "返回", onPrimaryClick = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)) {
            item {
                SettingsCard {
                    SwitchRow(prefs, NicknameWidthSettings.ENABLE, NicknameWidthSettings.TITLE,
                        "让群聊消息昵称使用可用宽度，保留原生单行显示", false)
                }
            }
            item { SmallTitle(text = "开启后在消息下次绑定时生效，可重新进入聊天查看。关闭后恢复宽度限制。当前适配微信 8.0.78。") }
        }
    }
}