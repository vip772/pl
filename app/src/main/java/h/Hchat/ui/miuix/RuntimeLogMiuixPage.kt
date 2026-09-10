package h.Hchat.ui.miuix

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robv.android.xposed.XC_MethodHook
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.HLog
import h.Hchat.utils.KavaReflector
import h.Hchat.utils.RuntimeLogStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text

private const val RUNTIME_LOG_EXPORT_REQUEST_CODE = 0x4843

@Composable
internal fun RuntimeLogMiuixPage(
    activity: Activity,
    onBack: () -> Unit
) {
    var refreshVersion by remember { mutableIntStateOf(0) }
    val content = remember(refreshVersion) {
        RuntimeLogStore.readRuntimeLog(activity).ifBlank { "暂无运行日志" }
    }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    PageScaffold(
        title = "运行日志",
        largeTitle = "运行日志",
        scrollBehavior = scrollBehavior,
        bottomBar = {
            BottomActionBar(
                primaryText = "返回",
                onPrimaryClick = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp
            )
        ) {
            item {
                SettingsCard {
                    ActionRow("刷新", "重新读取最新功能状态和失效原因") {
                        refreshVersion += 1
                    }
                    InsetDivider()
                    ActionRow("清空日志", "删除当前运行日志，后续状态会继续记录") {
                        RuntimeLogStore.clearRuntimeLog(activity)
                        refreshVersion += 1
                        Toast.makeText(activity, "运行日志已清空", Toast.LENGTH_SHORT).show()
                    }
                    InsetDivider()
                    ActionRow("导出日志", "选择保存目录并导出为 .log 文件") {
                        RuntimeLogExportBridge.launch(activity)
                    }
                }
            }
            item {
                SelectionContainer {
                    Text(
                        text = content,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private object RuntimeLogExportBridge {
    private var installed = false
    private var pendingActivity: Activity? = null

    @Synchronized
    fun launch(activity: Activity) {
        pendingActivity = activity
        if (!installed) installResultHook(activity.javaClass)
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "Hchat-runtime-$timestamp.log")
        }
        runCatching { activity.startActivityForResult(intent, RUNTIME_LOG_EXPORT_REQUEST_CODE) }
            .onFailure {
                pendingActivity = null
                HLog.e("[Hchat:RuntimeLog] 启动日志导出失败", it)
                Toast.makeText(activity, "无法打开文件选择器", Toast.LENGTH_SHORT).show()
            }
    }

    private fun installResultHook(activityClass: Class<*>) {
        runCatching {
            val method = KavaReflector.findMethodRecursive(
                activityClass,
                "onActivityResult",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Intent::class.java
            ) ?: error("未找到 Activity.onActivityResult")
            HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val requestCode = param.args.getOrNull(0) as? Int ?: return
                    if (requestCode != RUNTIME_LOG_EXPORT_REQUEST_CODE) return
                    val resultCode = param.args.getOrNull(1) as? Int ?: return
                    val data = param.args.getOrNull(2) as? Intent
                    val activity = param.thisObject as? Activity ?: pendingActivity
                    pendingActivity = null
                    if (resultCode != Activity.RESULT_OK || activity == null) return
                    val uri = data?.data ?: return
                    export(activity, uri)
                }
            })
            installed = true
        }.onFailure {
            installed = false
            HLog.e("[Hchat:RuntimeLog] 安装导出结果 Hook 失败", it)
        }
    }

    private fun export(activity: Activity, uri: Uri) {
        runCatching {
            val content = RuntimeLogStore.readRuntimeLog(activity)
            val output = activity.contentResolver.openOutputStream(uri, "wt")
                ?: error("无法创建日志文件")
            output.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(content)
            }
        }.onSuccess {
            Toast.makeText(activity, "运行日志已导出", Toast.LENGTH_SHORT).show()
        }.onFailure {
            HLog.e("[Hchat:RuntimeLog] 写入导出日志失败", it)
            Toast.makeText(activity, "导出失败", Toast.LENGTH_SHORT).show()
        }
    }
}
