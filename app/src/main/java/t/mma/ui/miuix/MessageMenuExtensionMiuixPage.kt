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
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar("保存设置", {
                val saved = prefs.edit().putBoolean(MessageMenuExtensionSettings.KEY_ENABLE, enabled).commit()
                if (saved) {
                    HLog.i("[Hchat:消息菜单扩展诊断] stage=settingsSave enabled=$enabled mode=diagnostic requested=6x5")
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
                SwitchRow(enabled, "消息菜单扩展", "目标：每排6个、最多5排，新增项目自动换行。当前为诊断版：开启只记录菜单结构，暂不改变原生3排×5个布局。", onCheckedChange = { enabled = it })
            }
        }
    }
}
