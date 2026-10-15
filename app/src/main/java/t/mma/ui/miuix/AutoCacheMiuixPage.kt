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
                    text = "开启后监听所有私聊、群聊新收到的图片、视频及任意扩展名的文件文档，自动触发微信内部下载，无需点击消息。文件仍在上传时等待上传完成后下载；由微信保存到原生缓存并维护下载状态，之后直接从聊天消息打开。\n\n微信在前台或后台均可触发，需微信进程存活并联网，会消耗网络流量和存储空间。失败或元数据未就绪时持续补查，最长一小时；关闭后停止排队任务，已提交的原生下载可能继续完成。不能恢复开启前已过期的内容。",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
        }
    }
}
