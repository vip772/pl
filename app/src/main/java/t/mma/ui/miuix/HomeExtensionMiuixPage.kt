package t.mma.ui.miuix

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import top.yukonga.miuix.kmp.basic.SmallTitle
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
    var orbit by remember { mutableStateOf(prefs.getBoolean(Settings.AVATAR_ORBIT, true)) }
    var selected by remember { mutableStateOf(Settings.favorites(prefs.getString(Settings.FAVORITE_IDS, ""))) }
    var photo by remember { mutableStateOf(prefs.getBoolean(Settings.PHOTO, false)) }
    var order by remember { mutableStateOf(Settings.order(prefs.getString(Settings.ORDER, null))) }
    var greeting by remember { mutableStateOf(prefs.getString(Settings.GREETING, Settings.DEFAULT_GREETING).orEmpty()) }
    val activity = context as? Activity ?: WeChatApis.currentActivity()?.currentActivity() as? Activity
    val behavior = MiuixScrollBehavior()
    PageScaffold(title = provider.title(), largeTitle = provider.title(), scrollBehavior = behavior,
        bottomBar = { BottomActionBar(primaryText = "返回", onPrimaryClick = onBack) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 84.dp)) {
            item { SmallTitle(text = "功能开关") }
            item { SettingsCard { SwitchRow(enabled, "首页扩展", "设置立即保存，返回首页生效") { enabled = it; prefs.edit().putBoolean(Settings.ENABLE, it).apply() } } }
            if (enabled) {
                item { SmallTitle(text = "首页日历") }
                item { SettingsCard { Column {
                    SwitchRow(calendar, "首页日历", "日期下方显示对应农历") { calendar = it; prefs.edit().putBoolean(Settings.CALENDAR, it).apply() }
                    if (calendar) {
                        HomeExtensionSlider(context, "背景透明度", Settings.CALENDAR_ALPHA, 75, 0f..100f, true)
                        InputRow(title = "问候语", summary = "显示在年月右侧，最多120字", value = greeting, onValueChange = { greeting = it.take(120); prefs.edit().putString(Settings.GREETING, greeting).apply() })
                    }
                } } }
                item { SmallTitle(text = "快捷联系人") }
                item { SettingsCard { Column {
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
                    if (favorites || recent) SwitchRow(orbit, "头像环绕动效", "复用动态撤回三色渐变，顺时针3秒一圈，带渐隐拖尾") { orbit = it; prefs.edit().putBoolean(Settings.AVATAR_ORBIT, it).apply() }
                    if (favorites || recent) HomeExtensionSlider(context, "背景透明度", Settings.RECENT_ALPHA, 75, 0f..100f, true)
                } } }
                item { SmallTitle(text = "首页照片") }
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
                item { SmallTitle(text = "区域上下顺序") }
                item { SettingsCard { Column {
                    order.forEachIndexed { index, id ->
                        val label = mapOf("calendar" to "日历", "contacts" to "快捷联系人", "photo" to "首页照片")[id].orEmpty()
                        ActionRow("${index + 1}. $label", "调整位置") {
                            if (activity != null) VoiceForwardMiuixDialog.showChoices(
                                activity = activity,
                                title = "调整${label}位置",
                                summary = "常用联系人和最近联系人共用同一个快捷联系人区域",
                                choices = listOf("第一位" to "列表顶部", "第二位" to "中间", "第三位" to "列表底部"),
                                onSelected = { target ->
                                    val next = order.toMutableList()
                                    next.remove(id)
                                    next.add(target.coerceIn(0, next.size), id)
                                    order = next
                                    prefs.edit().putString(Settings.ORDER, next.joinToString(",")).apply()
                                },
                                onDismiss = {}
                            )
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
    val active = java.util.concurrent.atomic.AtomicBoolean(true)
    val loading = VoiceForwardMiuixDialog.showLoading(activity = activity, title = "常用联系人", message = "正在读取联系人和群聊…", onDismiss = { active.set(false) })
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    val timeout = Runnable { if (active.compareAndSet(true, false)) { loading.close(); Toast.makeText(activity, "联系人读取超时，请稍后重试", Toast.LENGTH_SHORT).show() } }
    handler.postDelayed(timeout, 15000)
    Thread({
        val result = runCatching {
            val api = checkNotNull(WeChatApis.contacts())
            val items = (api.getPickerContacts() + api.getPickerGroups()).distinctBy { it.wxId }.map {
                VoiceForwardMiuixDialog.ContactItem(it.wxId, it.displayName(), it.isGroup(), it.avatarUrl, it.avatarBackupUrl)
            }
            // Group resolution may query message tables and acquire group runtime locks.
            // Keep it out of the Compose/UI thread entirely.
            val filters = t.mma.hooks.items.conversationgroup.ConversationGroupPickerSupport.filters(activity, items.map { it.id })
            items to filters
        }
        activity.runOnUiThread {
            handler.removeCallbacks(timeout)
            if (!active.compareAndSet(true, false)) return@runOnUiThread
            loading.close()
            if (!activity.isFinishing && !activity.isDestroyed) {
                if (result.isFailure) Toast.makeText(activity, "联系人读取失败，请稍后重试", Toast.LENGTH_SHORT).show()
                else activity.window.decorView.post {
                    if (activity.isFinishing || activity.isDestroyed) return@post
                    VoiceForwardMiuixDialog.showContacts(activity = activity, contacts = result.getOrThrow().first, preparedConversationGroups = result.getOrThrow().second,
                    title = "选择常用联系人（最多8个）", confirmText = "保存", maxSelection = 8, initialSelectedIds = selected.toSet(), allowEmpty = true,
                    onConfirm = { items -> if (items.size > 8) Toast.makeText(activity, "最多选择8个，请重新选择", Toast.LENGTH_SHORT).show() else onSelected(items.map { it.id }) }, onDismiss = {})
                }
            }
        }
    }, "Hchat-HomeContactPicker").apply { isDaemon = true }.start()
}