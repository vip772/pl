package h.Hchat.ui.miuix

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import h.Hchat.BuildConfig
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.items.securemessage.SecureMessageSettings
import h.Hchat.hooks.items.chattime.ChatTimeStyleSettings
import h.Hchat.hooks.items.swipequote.SwipeQuoteSettings
import h.Hchat.hooks.items.transparentavatar.UploadTransparentAvatarSettings
import h.Hchat.preferences.HchatStorage
import h.Hchat.ui.FeatureSettingsProvider
import h.Hchat.update.HchatUpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun SecureMessageMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val isAnti = provider.featureId() == SecureMessageSettings.ANTI_ID
    val prefsName = if (isAnti) SecureMessageSettings.ANTI_PREFS else SecureMessageSettings.SEND_PREFS
    val sp = remember { HchatStorage.preferences(context, prefsName) }
    val title = if (isAnti) "反安全消息" else "安全消息"
    val summary = if (isAnti) {
        "恢复安全消息的普通长按菜单和操作"
    } else {
        "给文字、链接、卡片、图片、视频和表情包添加安全标记"
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = title,
        largeTitle = title,
        scrollBehavior = scrollBehavior,
        bottomBar = { BottomActionBar("返回", onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "消息安全") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        SecureMessageSettings.KEY_ENABLE,
                        title,
                        summary,
                        if (isAnti) false else SecureMessageSettings.DEFAULT_ENABLE
                    )
                    InsetDivider()
                    InfoRow("适配范围", "微信 8.0.49–8.0.77；媒体消息按发送链路处理")
                }
            }
        }
    }
}

@Composable
internal fun UploadTransparentAvatarMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { UploadTransparentAvatarSettings.preferences(context) }
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
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "个人头像") }
            item {
                SettingsCard {
                    SwitchRow(
                        sp,
                        UploadTransparentAvatarSettings.KEY_ENABLE,
                        "上传透明头像",
                        "上传头像时使用 PNG 格式保留透明背景",
                        UploadTransparentAvatarSettings.DEFAULT_ENABLE
                    )
                }
            }
        }
    }
}
