package t.mma.ui.miuix

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import t.mma.hooks.items.profileid.ProfileRealNameTailSettings
import t.mma.hooks.items.profileid.ProfileIdSettings
import t.mma.hooks.items.profileid.ProfileAddTimeSettings
import t.mma.hooks.items.profileid.ProfileGroupJoinTimeSettings
import t.mma.hooks.items.profileid.ProfileGroupInviterSettings
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle

@Composable
internal fun ProfileIdMiuixPage(
    context: Context,
    provider: FeatureSettingsProvider,
    onBack: () -> Unit
) {
    val idSp = remember(context) { HchatStorage.preferences(context, ProfileIdSettings.PREFS_NAME) }
    val addTimeSp = remember(context) { HchatStorage.preferences(context, ProfileAddTimeSettings.PREFS_NAME) }
    var addTimeColor by remember(addTimeSp) {
        mutableStateOf(addTimeSp.getString(ProfileAddTimeSettings.KEY_COLOR, ProfileAddTimeSettings.DEFAULT_COLOR) ?: ProfileAddTimeSettings.DEFAULT_COLOR)
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = provider.title(),
        largeTitle = provider.title(),
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar("返回", onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 12.dp
            )
        ) {
            item { SmallTitle(text = "资料页") }
            item {
                SettingsCard {
                    SwitchRow(idSp, ProfileIdSettings.KEY_ENABLE, "显示微信 ID", "在资料页头部显示可点击复制的 ID", ProfileIdSettings.DEFAULT_ENABLE)
                    InsetDivider()
                    SwitchRow(addTimeSp, ProfileAddTimeSettings.KEY_ENABLE, "显示添加时间", "格式：yyyy-MM-dd HH:mm:ss", ProfileAddTimeSettings.DEFAULT_ENABLE)
                }
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "添加时间颜色") }
            item { SettingsCard {
                ColorPickerRow("添加时间渐变颜色", "支持单色或左右渐变", addTimeColor, onReset = {
                    addTimeColor = ProfileAddTimeSettings.DEFAULT_COLOR
                    addTimeSp.edit().remove(ProfileAddTimeSettings.KEY_COLOR).apply()
                }) { value ->
                    addTimeColor = ProfileAddTimeSettings.cleanColorSpec(value)
                    addTimeSp.edit().putString(ProfileAddTimeSettings.KEY_COLOR, addTimeColor).apply()
                }
            } }
            item {
                ProfileGradientSetting(
                    context = context,
                    preferencesName = ProfileRealNameTailSettings.PREFS_NAME,
                    enableKey = ProfileRealNameTailSettings.KEY_ENABLE,
                    colorKey = ProfileRealNameTailSettings.KEY_COLOR,
                    defaultEnabled = ProfileRealNameTailSettings.DEFAULT_ENABLE,
                    defaultColor = ProfileRealNameTailSettings.DEFAULT_COLOR,
                    title = "实名尾字",
                    summary = "显示实名最后一个字，取不到时显示未知",
                    cleanColorSpec = ProfileRealNameTailSettings::cleanColorSpec,
                    prefixKey = ProfileRealNameTailSettings.KEY_PREFIX
                )
            }
            item { SmallTitle(modifier = Modifier.padding(top = 10.dp), text = "群成员资料页") }
            item {
                ProfileGradientSetting(
                    context = context,
                    preferencesName = ProfileGroupJoinTimeSettings.PREFS_NAME,
                    enableKey = ProfileGroupJoinTimeSettings.KEY_ENABLE,
                    colorKey = ProfileGroupJoinTimeSettings.KEY_COLOR,
                    defaultEnabled = ProfileGroupJoinTimeSettings.DEFAULT_ENABLE,
                    defaultColor = ProfileGroupJoinTimeSettings.DEFAULT_COLOR,
                    title = "显示进群时间",
                    summary = "格式：yyyy-MM-dd HH:mm:ss",
                    cleanColorSpec = ProfileGroupJoinTimeSettings::cleanColorSpec
                )
            }
            item {
                ProfileGradientSetting(
                    context = context,
                    preferencesName = ProfileGroupInviterSettings.PREFS_NAME,
                    enableKey = ProfileGroupInviterSettings.KEY_ENABLE,
                    colorKey = ProfileGroupInviterSettings.KEY_COLOR,
                    defaultEnabled = ProfileGroupInviterSettings.DEFAULT_ENABLE,
                    defaultColor = ProfileGroupInviterSettings.DEFAULT_COLOR,
                    title = "显示邀请人",
                    summary = "无邀请记录时显示：自然进群",
                    cleanColorSpec = ProfileGroupInviterSettings::cleanColorSpec
                )
            }
        }
    }
}

/** Keeps each switch and its conditional color editor in the same card. */
@Composable
private fun ProfileGradientSetting(
    context: Context,
    preferencesName: String,
    enableKey: String,
    colorKey: String,
    defaultEnabled: Boolean,
    defaultColor: String,
    title: String,
    summary: String,
    cleanColorSpec: (String?) -> String,
    prefixKey: String? = null
) {
    val prefs = remember(context, preferencesName) { HchatStorage.preferences(context, preferencesName) }
    var enabled by remember(prefs, enableKey) { mutableStateOf(prefs.getBoolean(enableKey, defaultEnabled)) }
    var color by remember(prefs, colorKey) {
        mutableStateOf(prefs.getString(colorKey, defaultColor) ?: defaultColor)
    }
    var prefix by remember(prefs, prefixKey) {
        mutableStateOf(prefixKey?.let { prefs.getString(it, "").orEmpty() }.orEmpty())
    }
    SettingsCard {
        SwitchRow(enabled, title, summary) { checked ->
            enabled = checked
            prefs.edit().putBoolean(enableKey, checked).apply()
        }
        if (enabled) {
            if (prefixKey != null) {
                InsetDivider()
                InputRow("自定义前缀文本", "留空使用默认：＊＊", prefix) { value ->
                    prefix = value
                    prefs.edit().putString(prefixKey, value).apply()
                }
            }
            InsetDivider()
            ColorPickerRow(
                title = "三色渐变颜色",
                summary = "分别编辑起始色、中间色、结束色",
                value = color,
                threeColorGradient = true,
                onReset = {
                    color = defaultColor
                    prefs.edit().remove(colorKey).apply()
                }
            ) { value ->
                color = cleanColorSpec(value)
                prefs.edit().putString(colorKey, color).apply()
            }
        }
    }
}
