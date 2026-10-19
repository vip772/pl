package t.mma.ui.miuix

import androidx.compose.foundation.layout.Arrangement
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
    var cacheType by remember {
        mutableStateOf(AutoCacheSettings.normalizeType(prefs.getString(AutoCacheSettings.KEY_CACHE_TYPE, AutoCacheSettings.TYPE_ALL)))
    }
    val cacheTypes = remember {
        listOf(
            PopupChoice("图片视频", AutoCacheSettings.TYPE_MEDIA),
            PopupChoice("图片视频PDF", AutoCacheSettings.TYPE_MEDIA_PDF),
            PopupChoice("所有文件", AutoCacheSettings.TYPE_ALL)
        )
    }
    var selectedChats by remember { mutableStateOf(prefs.getStringSet(AutoCacheSettings.KEY_CHATS, emptySet()).orEmpty().toSet()) }
    var picker by remember { mutableStateOf<MessageBlockContactPickerRequest?>(null) }
    val state = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    SettingsRouteTransition(
        targetState = picker,
        label = "AutoCacheChatsTransition",
        depthOf = { if (it == null) 0 else 1 }
    ) { request ->
        if (request != null) {
            MessageBlockContactPickerPage(
                context = context,
                request = request,
                onBack = { picker = null },
                onConfirm = { selected ->
                    selectedChats = selected.map { it.contact.id }.toSet()
                    prefs.edit().putStringSet(AutoCacheSettings.KEY_CHATS, selectedChats).apply()
                    picker = null
                }
            )
        } else {
            PageScaffold(
                title = provider.title(),
                largeTitle = provider.title(),
                scrollBehavior = scrollBehavior,
                bottomBar = { BottomActionBar("返回", onBack) }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                    state = state,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                summary = "按所选类型自动下载私聊、群聊新收到的媒体和文件",
                                onCheckedChange = {
                                    enabled = it
                                    prefs.edit().putBoolean(AutoCacheSettings.KEY_ENABLE, it).apply()
                                }
                            )
                        }
                    }
                    if (enabled) {
                        item { SmallTitle(text = "缓存范围") }
                        item {
                            SettingsCard {
                                ActionRow(
                                    title = "适用聊天",
                                    summary = if (selectedChats.isEmpty()) "全部聊天（未选择）" else "仅缓存已选择的 ${selectedChats.size} 个聊天"
                                ) {
                                    picker = MessageBlockContactPickerRequest(
                                        title = "适用聊天",
                                        existingValue = formatIds(selectedChats),
                                        onValue = {},
                                        allowOfficialAccounts = false
                                    )
                                }
                                PopupChoiceRow(
                                    title = "缓存类型",
                                    summary = "选择私聊、群聊新收到消息的自动缓存范围",
                                    options = cacheTypes,
                                    currentValue = cacheType,
                                    onValueChanged = {
                                        cacheType = it
                                        prefs.edit().putString(AutoCacheSettings.KEY_CACHE_TYPE, it).apply()
                                    }
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            text = "适用聊天未选择时适用于全部聊天；选择后仅缓存选中的联系人和群聊，群消息按所属群判断。修改选择后尚未提交的任务按最新名单过滤。\n\n开启后按所选类型监听私聊、群聊新收到的消息，自动触发微信内部下载，无需点击消息。图片视频：仅图片和视频；图片视频PDF：增加 PDF 文件；所有文件：图片、视频及任意类型的文件文档。切换类型不删除已有缓存，尚未提交的排队任务按最新选择过滤。文件仍在上传时等待上传完成后下载；由微信保存到原生缓存并维护下载状态，之后直接从聊天消息打开。\n\n微信在前台或后台均可触发，需微信进程存活并联网，会消耗网络流量和存储空间。失败或元数据未就绪时持续补查，最长一小时；关闭后停止排队任务，已提交的原生下载可能继续完成。不能恢复开启前已过期的内容。",
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
