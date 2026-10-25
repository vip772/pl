package t.mma.ui.miuix

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import t.mma.hooks.items.conversationanalysis.ConversationAnalysisSettings
import t.mma.preferences.HchatStorage

@Composable
internal fun ConversationAnalysisMiuixPage(
    context: Context,
    provider: t.mma.ui.FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, ConversationAnalysisSettings.PREFS_NAME) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 12.dp
            )
        ) {
            item { SmallTitle(text = "群组功能") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        ConversationAnalysisSettings.KEY_ENABLE,
                        "群聊分析",
                        "开启后可在群聊消息长按菜单中使用“分析”",
                        ConversationAnalysisSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}