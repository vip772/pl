package t.mma.ui.miuix

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import t.mma.utils.HLog

/** A document result may pass through several hooked superclass implementations. */
internal object ConfigImportExportBridge {
    private const val TAG = "[Hchat:ConfigBackup]"
    private val hookedClasses = HashSet<Class<*>>()
    private class Pending(activity: Activity, val requestCode: Int, val onImported: (() -> Unit)?) {
        val owner = WeakReference(activity)
    }
    private var pending: Pending? = null

    @Synchronized
    fun launchExport(activity: Activity) {
        if (!begin(activity, CONFIG_EXPORT_REQUEST_CODE, null)) return
        val fileName = "Hchat_config_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".json"
        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, fileName)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }.preferSystemDocumentsUi(activity)
            activity.startActivityForResult(intent, CONFIG_EXPORT_REQUEST_CODE)
        } catch (error: Throwable) {
            pending = null
            HLog.e("$TAG 无法启动配置导出", error)
            Toast.makeText(activity, "当前系统不支持创建配置文件", Toast.LENGTH_SHORT).show()
        }
    }

    @Synchronized
    fun launchImport(activity: Activity, onDone: () -> Unit) {
        if (!begin(activity, CONFIG_IMPORT_REQUEST_CODE, onDone)) return
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.preferSystemDocumentsUi(activity)
            activity.startActivityForResult(intent, CONFIG_IMPORT_REQUEST_CODE)
        } catch (_: Throwable) {
            try {
                val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivityForResult(
                    Intent.createChooser(fallback, "选择 Hchat 配置文件"), CONFIG_IMPORT_REQUEST_CODE
                )
            } catch (error: Throwable) {
                pending = null
                HLog.e("$TAG 无法启动配置导入", error)
                Toast.makeText(activity, "当前系统不支持选择配置文件", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun begin(activity: Activity, requestCode: Int, onDone: (() -> Unit)?): Boolean {
        val previous = pending?.owner?.get()
        if (previous != null && !previous.isFinishing && !previous.isDestroyed) return false
        pending = Pending(activity, requestCode, onDone)
        // Hook each declared override once, including intermediate superclass implementations.
        var clazz: Class<*>? = activity.javaClass
        while (clazz != null && Activity::class.java.isAssignableFrom(clazz)) {
            hookActivityResult(clazz)
            clazz = clazz.superclass
        }
        return true
    }

    @Synchronized
    private fun consume(owner: Activity, requestCode: Int): Pending? {
        val request = pending ?: return null
        if (request.owner.get() !== owner || request.requestCode != requestCode) return null
        // Clear BEFORE I/O, refreshing Compose, or displaying a Toast. Cancellation consumes too.
        pending = null
        return request
    }

    @Synchronized
    private fun hookActivityResult(clazz: Class<*>) {
        if (hookedClasses.contains(clazz)) return
        try {
            val method = clazz.getDeclaredMethod(
                "onActivityResult", Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Intent::class.java
            )
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val owner = param.thisObject as? Activity ?: return
                    val requestCode = param.args.getOrNull(0) as? Int ?: return
                    val request = consume(owner, requestCode) ?: return
                    val resultCode = param.args.getOrNull(1) as? Int ?: return
                    if (resultCode != Activity.RESULT_OK) return
                    val uri = (param.args.getOrNull(2) as? Intent)?.data ?: return
                    handleResult(owner, request, uri)
                }
            })
            hookedClasses.add(clazz)
        } catch (_: NoSuchMethodException) {
            // This class inherits the callback; its declaring superclass is hooked by begin().
        } catch (error: Throwable) {
            HLog.e("$TAG 安装文件结果回调失败: ${clazz.name}", error)
        }
    }

    private fun handleResult(activity: Activity, request: Pending, uri: Uri) {
        try {
            if (request.requestCode == CONFIG_EXPORT_REQUEST_CODE) {
                val count = HchatConfigBackup.exportToUri(activity, uri)
                Toast.makeText(activity, if (count > 0) "已导出 $count 项配置" else "导出失败",
                    Toast.LENGTH_SHORT).show()
            } else {
                val count = HchatConfigBackup.importFromUri(activity, uri)
                if (count > 0) {
                    val refreshed = runCatching { request.onImported?.invoke() }.onFailure {
                        HLog.e("$TAG 配置已导入，但设置页刷新失败", it)
                    }.isSuccess
                    val message = if (refreshed) "已导入 $count 项配置，当前设置页已刷新"
                        else "已导入 $count 项配置，请重新打开设置页"
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(activity, "导入失败或文件无配置", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (error: Throwable) {
            HLog.e("$TAG 处理配置文件失败", error)
            Toast.makeText(activity, "配置文件处理失败", Toast.LENGTH_SHORT).show()
        }
    }
}
