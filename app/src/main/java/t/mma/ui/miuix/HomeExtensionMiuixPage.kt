package t.mma.ui.miuix

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import t.mma.hooks.items.homeextension.HomeExtensionSettings as Settings
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.items.homesidepanel.SidePanelImagePicker
import t.mma.hooks.items.homesidepanel.SidePanelImages
import t.mma.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
internal fun HomeExtensionMiuixPage(context: Context, provider: FeatureSettingsProvider, onBack: () -> Unit) {
    val prefs = remember { Settings.preferences(context) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(Settings.ENABLE, false)) }
    var calendar by remember { mutableStateOf(prefs.getBoolean(Settings.CALENDAR, true)) }
    var favorites by remember { mutableStateOf(prefs.getBoolean(Settings.FAVORITES, false)) }
    var recent by remember { mutableStateOf(!favorites && prefs.getBoolean(Settings.RECENT, true)) }
    var selected by remember { mutableStateOf(Settings.favorites(prefs.getString(Settings.FAVORITE_IDS, ""))) }
    var photo by remember { mutableStateOf(prefs.getBoolean(Settings.PHOTO, false)) }
    var order by remember { mutableStateOf(Settings.order(prefs.getString(Settings.ORDER, null))) }
    var greeting by remember { mutableStateOf(prefs.getString(Settings.GREETING, Settings.DEFAULT_GREETING).orEmpty()) }
    val activity = context as? Activity ?: WeChatApis.currentActivity()?.currentActivity() as? Activity
    val behavior = MiuixScrollBehavior()
    PageScaffold(title = provider.title(), largeTitle = provider.title(), scrollBehavior = behavior,
        bottomBar = { BottomActionBar(primaryText = "返回", onPrimaryClick = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection), contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)) {
            item { SettingsCard { SwitchRow(enabled, "首页扩展", "设置立即保存，返回首页生效") { enabled = it; prefs.edit().putBoolean(Settings.ENABLE, it).apply() } } }
            if (enabled) {
                item { SettingsCard { Column {
                    SwitchRow(calendar, "首页日历", "日期下方显示对应农历") { calendar = it; prefs.edit().putBoolean(Settings.CALENDAR, it).apply() }
                    if (calendar) {
                        HomeExtensionSlider(context, "背景透明度", Settings.CALENDAR_ALPHA, 75, 0f..100f, true)
                        InputRow(title = "问候语", summary = "显示在年月右侧，最多120字", value = greeting, onValueChange = { greeting = it.take(120); prefs.edit().putString(Settings.GREETING, greeting).apply() })
                    }
                } } }
                item { SettingsCard { Column {
                    Text("快捷联系人", modifier = Modifier.padding(20.dp), color = MiuixTheme.colorScheme.onSurface)
                    SwitchRow(favorites, "常用联系人", "自选联系人和群聊，合计最多8个") {
                        favorites = it; if (it) recent = false
                        prefs.edit().putBoolean(Settings.FAVORITES, favorites).putBoolean(Settings.RECENT, recent).apply()
                    }
                    if (favorites) ActionRow("选择常用联系人", "已选择 ${selected.size}/8 个") {
                        if (activity != null) pickHomeExtensionContacts(activity, selected) { ids -> selected = ids; prefs.edit().putString(Settings.FAVORITE_IDS, ids.joinToString(",")).apply() }
                    }
                    SwitchRow(recent, "最近联系人", "最近收发消息的联系人和群聊混合显示，最多8个") {
                        recent = it; if (it) favorites = false
                        prefs.edit().putBoolean(Settings.FAVORITES, favorites).putBoolean(Settings.RECENT, recent).apply()
                    }
                    if (favorites || recent) HomeExtensionSlider(context, "背景透明度", Settings.RECENT_ALPHA, 75, 0f..100f, true)
                } } }
                item { SettingsCard { Column {
                    SwitchRow(photo, "首页照片", "支持静态图片、动图和视频") { photo = it; prefs.edit().putBoolean(Settings.PHOTO, it).apply() }
                    if (photo) {
                        ActionRow("添加 / 更换媒体", "复用侧边栏图片卡片，独立保存首页媒体") { if (activity != null) SidePanelImagePicker.launch(activity, Settings.PHOTO_SLOT) }
                        ActionRow("移除媒体", "清除首页照片") { SidePanelImages.clear(context, Settings.PHOTO_SLOT) }
                        HomeExtensionSlider(context, "高度 dp", Settings.PHOTO_HEIGHT, 180, 80f..500f)
                        HomeExtensionSlider(context, "宽度 %", Settings.PHOTO_WIDTH, 100, 30f..100f)
                        HomeExtensionSlider(context, "透明度", Settings.PHOTO_ALPHA, 100, 0f..100f, true)
                    }
                } } }
                item { SettingsCard { Column {
                    Text("区域上下顺序", modifier = Modifier.padding(20.dp), color = MiuixTheme.colorScheme.onSurface)
                    order.forEachIndexed { index, id ->
                        val label = mapOf("calendar" to "日历", "contacts" to "快捷联系人", "photo" to "首页照片")[id].orEmpty()
                        Row(Modifier.fillMaxWidth()) {
                            Text("${index + 1}. $label", modifier = Modifier.weight(1f).padding(16.dp), color = MiuixTheme.colorScheme.onSurface)
                            Column(Modifier.weight(1f)) {
                                if (index > 0) ActionRow("上移", "") { val next = order.toMutableList(); java.util.Collections.swap(next, index, index - 1); order = next; prefs.edit().putString(Settings.ORDER, next.joinToString(",")).apply() }
                                if (index < order.lastIndex) ActionRow("下移", "") { val next = order.toMutableList(); java.util.Collections.swap(next, index, index + 1); order = next; prefs.edit().putString(Settings.ORDER, next.joinToString(",")).apply() }
                            }
                        }
                    }
                } } }
            }
        }
    }
}

@Composable
private fun HomeExtensionSlider(context: Context, title: String, key: String, default: Int, range: ClosedFloatingPointRange<Float>, inverse: Boolean = false) {
    val prefs = remember { Settings.preferences(context) }
    var value by remember(key) { mutableStateOf(prefs.getInt(key, default).toFloat().coerceIn(range)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("$title ${if (inverse) 100 - value.roundToInt() else value.roundToInt()}${if (inverse) "%" else ""}", color = MiuixTheme.colorScheme.onSurface)
        Slider(value = if (inverse) 100f - value else value, onValueChange = {
            value = (if (inverse) 100f - it else it).roundToInt().toFloat().coerceIn(range)
            prefs.edit().putInt(key, value.toInt()).apply()
        }, valueRange = range, modifier = Modifier.fillMaxWidth())
    }
}

private fun pickHomeExtensionContacts(activity: Activity, selected: List<String>, onSelected: (List<String>) -> Unit) {
    val loading = VoiceForwardMiuixDialog.showLoading(activity = activity, title = "常用联系人", message = "正在读取联系人和群聊…", onDismiss = {})
    Thread({
        val result = runCatching {
            val api = checkNotNull(WeChatApis.contacts())
            (api.getPickerContacts() + api.getPickerGroups()).distinctBy { it.wxId }.map {
                VoiceForwardMiuixDialog.ContactItem(it.wxId, it.displayName(), it.isGroup(), it.avatarUrl, it.avatarBackupUrl)
            }
        }
        activity.runOnUiThread {
            loading.close()
            if (!activity.isFinishing && !activity.isDestroyed) {
                if (result.isFailure) Toast.makeText(activity, "联系人读取失败，请稍后重试", Toast.LENGTH_SHORT).show()
                else VoiceForwardMiuixDialog.showContacts(activity = activity, contacts = result.getOrDefault(emptyList()),
                    title = "选择常用联系人（最多8个）", confirmText = "保存", maxSelection = 8, initialSelectedIds = selected.toSet(), allowEmpty = true,
                    onConfirm = { items -> if (items.size > 8) Toast.makeText(activity, "最多选择8个，请重新选择", Toast.LENGTH_SHORT).show() else onSelected(items.map { it.id }) }, onDismiss = {})
            }
        }
    }, "Hchat-HomeContactPicker").apply { isDaemon = true }.start()
}