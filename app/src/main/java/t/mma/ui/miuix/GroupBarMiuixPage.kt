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
import t.mma.hooks.items.groupbar.BarGroup
import t.mma.hooks.items.groupbar.GroupBarStore as Store
import t.mma.hooks.items.conversationgroup.ConversationGroup
import t.mma.ui.FeatureSettingsProvider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
internal fun GroupBarMiuixPage(context: Context, provider: FeatureSettingsProvider, onBack: () -> Unit) {
    val prefs = remember { Store.prefs(context) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(Store.ENABLE, false)) }
    var style by remember { mutableStateOf(prefs.getInt(Store.STYLE, 0)) }
    var alpha by remember { mutableStateOf(prefs.getInt(Store.ALPHA, 0).toFloat()) }
    var groups by remember { mutableStateOf(Store.load(context)) }
    var managing by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<BarGroup?>(null) }
    val activity = context as? Activity
    val current = picker
    if (current != null) {
        // Supply only this independent group, preserving selected members outside recent chats.
        ConversationGroupSettingsUi.ConversationGroupConversationPickerPage(context,
            ConversationGroup(current.id, current.name, conversationIds = current.members.toList()),
            listOf(ConversationGroup(current.id, current.name, conversationIds = current.members.toList())),
            onBack = { picker = null }, onConfirm = { ids ->
                groups = groups.map { if (it.id == current.id) it.copy(members = ids.toSet()) else it }
                Store.save(context, groups); picker = null
            })
        return
    }
    val back = { if (managing) managing = false else onBack() }
    val behavior = MiuixScrollBehavior()
    PageScaffold(title = if (managing) "管理分组" else provider.title(),
        largeTitle = if (managing) "管理分组" else provider.title(), scrollBehavior = behavior,
        onBack = back, bottomBar = { BottomActionBar(primaryText = "返回", onPrimaryClick = back) }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection), contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp)) {
            if (!managing) {
                item { SettingsCard { Column {
                    SwitchRow(enabled, "分组横栏", "在首页标题栏下方显示独立分组；开启时自动关闭聊天分组") {
                        enabled = it; prefs.edit().putBoolean(Store.ENABLE, it).apply()
                    }
                    if (enabled) {
                        PopupChoiceRow("顶部聊天分组样式", "选择横栏显示方式",
                            listOf(PopupChoice("标准分组", "0"), PopupChoice("悬浮分组", "1")),
                            style.toString(), { value -> style = value.toInt(); prefs.edit().putInt(Store.STYLE, style).apply() })
                        ActionRow("管理分组", "固定分组开关、自定义成员和分组顺序") { managing = true }
                        ActionRow("透明度调节", "当前背景透明度 ${alpha.roundToInt()}%") {
                            if (activity != null) VoiceForwardMiuixDialog.showNumberInput(
                                activity = activity, title = "背景透明度", initialValue = alpha.roundToInt(),
                                minValue = 0, maxValue = 100, onConfirm = { value ->
                                    alpha = value.toFloat(); prefs.edit().putInt(Store.ALPHA, value).apply()
                                }, onDismiss = {})
                        }
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("背景透明度 ${alpha.roundToInt()}%", color = MiuixTheme.colorScheme.onSurface)
                            Slider(value = alpha, onValueChange = { alpha = it; prefs.edit().putInt(Store.ALPHA, it.roundToInt()).apply() },
                                valueRange = 0f..100f, modifier = Modifier.fillMaxWidth())
                        }
                    }
                } } }
                item { SmallTitle(text = "适配微信8.0.78两种首页列表；与聊天分组互斥，分别保留配置；最多10组，一屏显示6组，可左右滑动。长按首页分组名称可调整左右位置。") }
            } else {
                item { SmallTitle(text = "固定分组") }
                items(groups.size, key = { groups[it].id }) { index ->
                    val group = groups[index]
                    SettingsCard { Column {
                        if (group.fixed) {
                            SwitchRow(group.enabled, group.name, "关闭后不在横栏显示") { value ->
                                groups = groups.map { if (it.id == group.id) it.copy(enabled = value) else it }; Store.save(context, groups)
                            }
                        } else {
                            ActionRow(group.name, "${group.members.size} 个成员 · 点击添加好友或群聊") { picker = group }
                            ActionRow("重命名", group.name) {
                                if (activity != null) VoiceForwardMiuixDialog.showTextInput(activity = activity,
                                    title = "重命名分组", summary = "输入分组名称", initialValue = group.name, placeholder = "分组名称", maxLength = 20, onConfirm = { name ->
                                        if (name.isNotBlank()) {
                                            groups = groups.map { if (it.id == group.id) it.copy(name = name.trim().take(20)) else it }
                                            Store.save(context, groups)
                                        }
                                    }, onDismiss = {})
                            }
                            ActionRow("移除", "移除该分组，聊天记录不受影响") {
                                groups = groups.filterNot { it.id == group.id }; Store.save(context, groups)
                            }
                        }
                        ActionRow("左移", "将分组向左移动一位") {
                            Store.move(context, group.id, -1); groups = Store.load(context)
                        }
                        ActionRow("右移", "将分组向右移动一位") {
                            Store.move(context, group.id, 1); groups = Store.load(context)
                        }
                    } }
                }
                item { SettingsCard { ActionRow("添加分组", "${groups.size}/10 · 在末尾添加") {
                    if (Store.add(context)) groups = Store.load(context)
                    else Toast.makeText(context, "最多10个分组", Toast.LENGTH_SHORT).show()
                } } }
            }
        }
    }
}