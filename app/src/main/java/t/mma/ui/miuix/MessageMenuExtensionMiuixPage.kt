package t.mma.ui.miuix

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import t.mma.hooks.items.messagemenuextension.MessageMenuExtensionSettings
import t.mma.ui.FeatureSettingsProvider
import t.mma.utils.HLog
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

@Composable
internal fun MessageMenuExtensionMiuixPage(context: Context, provider: FeatureSettingsProvider, onBack: () -> Unit) {
    val prefs = remember { MessageMenuExtensionSettings.preferences(context) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(MessageMenuExtensionSettings.KEY_ENABLE, false)) }
    var columns by remember { mutableStateOf(MessageMenuExtensionSettings.columns(prefs)) }
    var maxRows by remember { mutableStateOf(MessageMenuExtensionSettings.maxRows(prefs)) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar("保存设置", {
                val saved = prefs.edit().putInt(MessageMenuExtensionSettings.KEY_COLUMNS, columns)
                    .putInt(MessageMenuExtensionSettings.KEY_MAX_ROWS, maxRows)
                    .putBoolean(MessageMenuExtensionSettings.KEY_ENABLE, enabled).commit()
                if (saved) {
                    HLog.i("[Hchat:消息菜单扩展诊断] stage=settingsSave enabled=$enabled mode=grid requested=${columns}x$maxRows")
                } else {
                    HLog.e("[Hchat:消息菜单扩展诊断] stage=settingsSave failed")
                }
                Toast.makeText(context, if (saved) "设置已保存" else "保存失败，请重试", Toast.LENGTH_SHORT).show()
            }, "返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)
        ) {
            item {
                SwitchRow(enabled, "消息菜单扩展", "自定义菜单列数和可见排数，默认5列、3排；超出可见排数可上下滚动。关闭后使用微信原生布局。保存后下次打开菜单生效。", onCheckedChange = { enabled = it })
            }
            if (enabled) {
                item {
                    PopupChoiceRow("横向图标数量", "每排图标按钮数量，默认5个",
                        (5..8).map { PopupChoice("${it}个", it.toString()) },
                        columns.toString(), { columns = it.toInt() })
                }
                item {
                    PopupChoiceRow("竖向最多排数", "默认3排，超出后滚动查看",
                        (3..5).map { PopupChoice("${it}排", it.toString()) },
                        maxRows.toString(), { maxRows = it.toInt() })
                }
            }
        }
    }
}
