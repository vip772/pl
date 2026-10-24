package t.mma.ui.miuix

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
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
import t.mma.hooks.items.mentionhighlight.MentionHighlightSettings
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun MentionExtensionMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val sp = remember { HchatStorage.preferences(context, MentionHighlightSettings.PREFS_NAME) }
    var colorSpec by remember {
        mutableStateOf(
            sp.getString(MentionHighlightSettings.KEY_COLOR, MentionHighlightSettings.DEFAULT_COLOR)
                ?: MentionHighlightSettings.DEFAULT_COLOR
        )
    }
    val hiddenPrefs = remember { t.mma.hooks.items.hiddenat.HiddenAtSettings.preferences(context) }
    val emojiPrefs = remember { t.mma.hooks.items.emojiat.EmojiAtSettings.preferences(context) }
    var hidden by remember { mutableStateOf(hiddenPrefs.getBoolean(t.mma.hooks.items.hiddenat.HiddenAtSettings.ENABLE, false)) }
    var emoji by remember { mutableStateOf(emojiPrefs.getBoolean(t.mma.hooks.items.emojiat.EmojiAtSettings.ENABLE, false)) }
    var highlight by remember { mutableStateOf(sp.getBoolean(MentionHighlightSettings.KEY_ENABLE, false)) }
    val noticePrefs = remember { HchatStorage.preferences(context, t.mma.hooks.items.mentionextension.MentionNoticeSettings.PREFS) }
    var notice by remember { mutableStateOf(t.mma.hooks.items.mentionextension.MentionNoticeSettings.enabled(context)) }
    val state = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "保存设置",
                onPrimaryClick = {
                    val normalized = MentionHighlightSettings.cleanColorSpec(colorSpec)
                        .ifEmpty { MentionHighlightSettings.DEFAULT_COLOR }
                    sp.edit().putString(MentionHighlightSettings.KEY_COLOR, normalized).apply()
                    colorSpec = normalized
                    Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                },
                secondaryText = "返回",
                onSecondaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = state,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item { SmallTitle(text = "功能开关") }
            item {
                SettingsCard {
                    SwitchRow(hidden, "隐藏艾特", "隐藏群聊消息中的艾特文字，保留艾特名单") {
                        hidden = it
                        hiddenPrefs.edit().putBoolean(t.mma.hooks.items.hiddenat.HiddenAtSettings.ENABLE, it).apply()
                    }
                    SwitchRow(emoji, "表情艾特", "发送群聊表情时选择普通发送或隐藏成员艾特") {
                        emoji = it
                        emojiPrefs.edit().putBoolean(t.mma.hooks.items.emojiat.EmojiAtSettings.ENABLE, it).apply()
                    }
                    SwitchRow(notice, "艾特提醒", "收到群聊中艾特你或所有人的消息时，插入红色本地提醒") {
                        notice = it
                        noticePrefs.edit().putBoolean(t.mma.hooks.items.mentionextension.MentionNoticeSettings.ENABLE, it).apply()
                    }
                    SwitchRow(highlight, "艾特高亮", "高亮艾特对象并支持点击资料页；重新进入聊天后生效") {
                        highlight = it
                        sp.edit().putBoolean(MentionHighlightSettings.KEY_ENABLE, it).apply()
                    }
                }
            }
            if (highlight) {
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "颜色") }
            item {
                SettingsCard {
                    ColorPickerRow(
                        "艾特高亮渐变颜色",
                        "调节渐变起始色和结束色",
                        colorSpec,
                        onReset = { colorSpec = MentionHighlightSettings.DEFAULT_COLOR }
                    ) { colorSpec = it.take(19) }
                }
            }
            }
        }
    }
}