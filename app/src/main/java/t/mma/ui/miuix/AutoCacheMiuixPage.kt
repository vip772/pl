package t.mma.ui.miuix

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import t.mma.hooks.items.autocache.AutoCacheSettings
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun AutoCacheMiuixPage(context: Context, provider: FeatureSettingsProvider, onBack: () -> Unit) {
    val prefs = remember { HchatStorage.preferences(context, AutoCacheSettings.PREFS_NAME) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(AutoCacheSettings.KEY_ENABLE, false)) }
    val state = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = state,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "功能开关") }
            item {
                SettingsCard {
                    SwitchRow(
                        checked = enabled,
                        title = "自动缓存",
                        summary = "接收任何私聊、群聊的图片、视频与文件文档时自动下载保存",
                        onCheckedChange = {
                            enabled = it
                            prefs.edit().putBoolean(AutoCacheSettings.KEY_ENABLE, it).apply()
                        }
                    )
                }
            }
            item {
                Text(
                    text = "开启后处理新收到的消息，需要微信进程运行并保持联网；会使用网络流量和存储空间。\n\n文件保存到微信外部媒体目录下 Hchat/AutoCache，按 Image、Video、File 分类，可用系统文件管理器查看。缓存独立保存，不修改聊天消息的下载状态；原消息过期后请从缓存目录打开。关闭后停止接收新任务，已提交给微信的下载可能继续完成。",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
        }
    }
}
