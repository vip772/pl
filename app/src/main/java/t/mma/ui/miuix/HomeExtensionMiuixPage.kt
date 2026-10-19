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
    val recallPrefs = remember { t.mma.preferences.HchatStorage.preferences(context, t.mma.hooks.items.antirecall.AntiRecallSettings.PREFS_NAME) }
    val defaultOrbitColor = remember { recallPrefs.getString(t.mma.hooks.items.antirecall.AntiRecallSettings.KEY_DYNAMIC_COLOR, t.mma.hooks.items.antirecall.DynamicRecallGradient.DEFAULT).orEmpty() }
    var orbitColor by remember { mutableStateOf(prefs.getString(Settings.ORBIT_COLOR, defaultOrbitColor).orEmpty()) }
    var selected by remember { mutableStateOf(Settings.favorites(prefs.getString(Settings.FAVORITE_IDS, ""))) }
    var weather by remember { mutableStateOf(prefs.getBoolean(Settings.WEATHER, false)) }
    var cityQuery by remember { mutableStateOf("") }
    var cityName by remember { mutableStateOf(prefs.getString("weather_city", "").orEmpty()) }
    var weatherSearching by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf(prefs.getBoolean(Settings.PHOTO, false)) }
    var customLayout by remember { mutableStateOf(prefs.getBoolean(Settings.CUSTOM_LAYOUT, false)) }
    var layoutRows by remember { mutableStateOf(t.mma.hooks.items.homeextension.HomeCardLayout.rows(prefs.getString(Settings.LAYOUT_ROWS, null), Settings.order(prefs.getString(Settings.ORDER, null)))) }
    val cardNames = mapOf("calendar" to "日历", "contacts" to "快捷联系人", "photo" to "首页照片", "weather" to "首页天气")
    fun saveRows(rows: List<List<String>>) {
        layoutRows = rows
        prefs.edit().putString(Settings.LAYOUT_ROWS, t.mma.hooks.items.homeextension.HomeCardLayout.encode(rows)).apply()
    }
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
                item { SmallTitle(text = "自定义布局") }
                item { SettingsCard { Column {
                    SwitchRow(customLayout, "自定义布局", "每行一张独占，或两张均分；关闭卡片后同排自动补位") { customLayout = it; prefs.edit().putBoolean(Settings.CUSTOM_LAYOUT, it).apply() }
                    if(customLayout) {
                        ActionRow("选择布局模板", layoutRows.joinToString(" / ") { row -> row.joinToString(" + ") { cardNames[it].orEmpty() } }) {
                            if(activity != null) VoiceForwardMiuixDialog.showChoices(activity, "布局模板", "使用当前区域顺序排列，也可在下方逐卡调整", listOf("全部竖排" to "1 + 1 + 1 + 1", "田字格" to "2 + 2", "上双下单" to "2 + 1 + 1", "上单下双" to "1 + 1 + 2", "中间双排" to "1 + 2 + 1"), onSelected = { index ->
                                val ids = order
                                saveRows(when(index) {
                                    1 -> ids.chunked(2)
                                    2 -> listOf(ids.take(2), listOf(ids[2]), listOf(ids[3]))
                                    3 -> listOf(listOf(ids[0]), listOf(ids[1]), ids.takeLast(2))
                                    4 -> listOf(listOf(ids[0]), ids.subList(1,3), listOf(ids[3]))
                                    else -> ids.map { listOf(it) }
                                })
                            }, onDismiss = {})
                        }
                        layoutRows.flatten().forEach { id ->
                            val position = layoutRows.indexOfFirst { id in it }
                            ActionRow(cardNames[id].orEmpty(), "第${position + 1}行 · 点击移动或合并") {
                                if(activity != null) VoiceForwardMiuixDialog.showChoices(activity, "调整${cardNames[id]}", "可加入未满的一行；目标行已满时在其下方新建一行", layoutRows.mapIndexed { i,row -> "第${i+1}行" to row.joinToString(" + ") { cardNames[it].orEmpty() } } + listOf("新建一行" to "放到末尾"), onSelected = { target ->
                                    saveRows(t.mma.hooks.items.homeextension.HomeCardLayout.move(layoutRows, id, target))
                                }, onDismiss = {})
                            }
                        }
                        ActionRow("交换同排卡片顺序", "点击选择需要左右交换的一行") {
                            if(activity != null) VoiceForwardMiuixDialog.showChoices(activity, "交换同排顺序", "单张行保持不变", layoutRows.mapIndexed { i,row -> "第${i+1}行" to row.joinToString(" + ") { cardNames[it].orEmpty() } }, onSelected = { index -> saveRows(layoutRows.mapIndexed { i,row -> if(i==index) row.reversed() else row }) }, onDismiss = {})
                        }
                    }
                } } }
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
                    if (favorites || recent) SwitchRow(orbit, "头像环绕动效", "顺时针3秒一圈，带渐隐拖尾，颜色和大小可调整") { orbit = it; prefs.edit().putBoolean(Settings.AVATAR_ORBIT, it).apply() }
                    if ((favorites || recent) && orbit) {
                        ColorPickerRow("环绕渐变颜色", "分别编辑起始色、中间色、结束色", orbitColor,
                            threeColorGradient = true, onReset = {
                                orbitColor = t.mma.hooks.items.antirecall.DynamicRecallGradient.DEFAULT
                                prefs.edit().putString(Settings.ORBIT_COLOR, orbitColor).apply()
                            }) { orbitColor = it; prefs.edit().putString(Settings.ORBIT_COLOR, it).apply() }
                        HomeExtensionSlider(context, "圆点直径 dp", Settings.ORBIT_SIZE, Settings.DEFAULT_ORBIT_SIZE, 2f..10f)
                        HomeExtensionSlider(context, "距头像边缘 dp", Settings.ORBIT_GAP, Settings.DEFAULT_ORBIT_GAP, 2f..12f)
                    }
                    if (favorites || recent) HomeExtensionSlider(context, "背景透明度", Settings.RECENT_ALPHA, 75, 0f..100f, true)
                } } }
                item { SmallTitle(text = "首页天气") }
                item { SettingsCard { Column {
                    SwitchRow(weather, "首页天气", "城市天气、温度、体感、湿度和风速；后台按需更新") { weather = it; prefs.edit().putBoolean(Settings.WEATHER, it).apply() }
                    if (weather) {
                        InputRow(title = "搜索城市", summary = cityName.ifBlank { "输入城市名后点击搜索，可用中文或拼音" }, value = cityQuery, onValueChange = { cityQuery = it.take(80) })
                        ActionRow(if(weatherSearching) "正在搜索…" else "搜索并选择城市", "免密钥 · Open-Meteo · 30分钟缓存") {
                            if (!weatherSearching && cityQuery.isNotBlank() && activity != null) {
                                weatherSearching = true
                                t.mma.hooks.items.homeextension.HomeWeather.search(cityQuery.trim()) { cities ->
                                    weatherSearching = false
                                    if (!activity.isFinishing && !activity.isDestroyed) {
                                        if (cities.isNullOrEmpty()) Toast.makeText(context, if(cities == null) "天气服务暂不可用，请稍后重试" else "未找到城市，请尝试拼音或英文名称", Toast.LENGTH_SHORT).show()
                                        else VoiceForwardMiuixDialog.showChoices(activity, "选择天气城市", "按城市查询天气，无需定位权限", cities.map { it.name to "${it.latitude}, ${it.longitude}" }, onSelected = { index ->
                                            val city = cities[index]; cityName = city.name
                                            prefs.edit().putString("weather_city", city.name).putString("weather_lat", city.latitude.toString()).putString("weather_lon", city.longitude.toString()).apply()
                                        }, onDismiss = {})
                                    }
                                }
                            }
                        }
                        HomeExtensionSlider(context, "背景透明度", Settings.WEATHER_ALPHA, 100, 0f..100f, true)
                    }
                } } }
                item { SmallTitle(text = "首页照片") }
                item { SettingsCard { Column {
                    SwitchRow(photo, "首页照片", "支持静态图片、动图和视频") { photo = it; prefs.edit().putBoolean(Settings.PHOTO, it).apply() }
                    if (photo) {
                        ActionRow("添加 / 更换媒体", "静态图片支持长按拖动、双指缩放，松手自动保存") { if (activity != null) SidePanelImagePicker.launch(activity, Settings.PHOTO_SLOT) }
                        ActionRow("移除媒体", "清除首页照片") { SidePanelImages.clear(context, Settings.PHOTO_SLOT) }
                        HomeExtensionSlider(context, "高度 dp", Settings.PHOTO_HEIGHT, 180, 80f..500f)
                        HomeExtensionSlider(context, "宽度 %", Settings.PHOTO_WIDTH, 100, 30f..100f)
                        HomeExtensionSlider(context, "透明度", Settings.PHOTO_ALPHA, 100, 0f..100f, true)
                    }
                } } }
                if(!customLayout) item { SmallTitle(text = "区域上下顺序") }
                if(!customLayout) item { SettingsCard { Column {
                    order.forEachIndexed { index, id ->
                        val label = mapOf("calendar" to "日历", "contacts" to "快捷联系人", "photo" to "首页照片", "weather" to "首页天气")[id].orEmpty()
                        ActionRow("${index + 1}. $label", "调整位置") {
                            if (activity != null) VoiceForwardMiuixDialog.showChoices(
                                activity = activity,
                                title = "调整${label}位置",
                                summary = "常用联系人和最近联系人共用同一个快捷联系人区域",
                                choices = order.indices.map { "第${it + 1}位" to if(it == 0) "列表顶部" else if(it == order.lastIndex) "列表底部" else "中间" },
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