package t.mma.hooks.items.homesidepanel

import android.app.Activity
import android.content.Intent
import android.net.Uri
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal object SidePanelImagePicker {
    private data class Pending(
        val activity: WeakReference<Activity>,
        val slot: String,
        val callback: (SidePanelPickResult) -> Unit,
        val processing: AtomicBoolean = AtomicBoolean(false)
    )

    private val nextRequestCode = AtomicInteger(0x6E10)
    private val pending = ConcurrentHashMap<Int, Pending>()
    private val resultHookedClasses = ConcurrentHashMap.newKeySet<Class<*>>()
    private val destroyHookedClasses = ConcurrentHashMap.newKeySet<Class<*>>()

    fun launch(
        activity: Activity,
        slot: String,
        callback: (SidePanelPickResult) -> Unit = {}
    ) {
        hookActivityHierarchy(activity.javaClass)
        val requestCode = allocateRequestCode()
        pending[requestCode] = Pending(WeakReference(activity), slot, callback)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if(SidePanelAppearance.isPhoto(slot)) "*/*" else "image/*"
            if(SidePanelAppearance.isPhoto(slot)) putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("image/*","video/*"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        runCatching { activity.startActivityForResult(intent, requestCode) }
            .onFailure {
                val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = if(SidePanelAppearance.isPhoto(slot)) "*/*" else "image/*"
                    if(SidePanelAppearance.isPhoto(slot)) putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("image/*","video/*"))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    activity.startActivityForResult(
                        Intent.createChooser(fallback, "选择侧边栏图片或视频"),
                        requestCode
                    )
                }.onFailure {
                    pending.remove(requestCode)?.callback?.invoke(SidePanelPickResult.FAILED)
                }
            }
    }

    private fun hookActivityHierarchy(activityClass: Class<*>) {
        var current: Class<*>? = activityClass
        while (current != null && Activity::class.java.isAssignableFrom(current)) {
            hookActivityResult(current)
            hookActivityDestroy(current)
            current = current.superclass
        }
    }

    private fun allocateRequestCode(): Int {
        repeat(0x6EFF - 0x6E10 + 1) {
            val candidate = nextRequestCode.updateAndGet { current ->
                if (current >= 0x6EFF) 0x6E10 else current + 1
            }
            if (!pending.containsKey(candidate)) return candidate
        }
        val reused = pending.keys.minOrNull() ?: 0x6E10
        pending.remove(reused)?.callback?.invoke(SidePanelPickResult.CANCELLED)
        return reused
    }

    @Synchronized
    private fun hookActivityResult(clazz: Class<*>) {
        if (!resultHookedClasses.add(clazz)) return
        runCatching {
            XposedBridge.hookAllMethods(clazz, "onActivityResult", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val requestCode = param.args.getOrNull(0) as? Int ?: return
                    val result = pending[requestCode] ?: return
                    val activity = result.activity.get()
                    if (activity == null) {
                        pending.remove(requestCode, result)
                        return
                    }
                    if (param.thisObject !== activity) return
                    val resultCode = param.args.getOrNull(1) as? Int ?: return
                    val data = param.args.getOrNull(2) as? Intent
                    val uri = data?.data
                    if (resultCode != Activity.RESULT_OK || uri == null) {
                        if (pending.remove(requestCode, result)) {
                            result.callback(SidePanelPickResult.CANCELLED)
                        }
                        return
                    }
                    if (!result.processing.compareAndSet(false, true)) return
                    takeReadPermission(activity, data, uri)
                    val appContext = activity.applicationContext
                    Thread({
                        val success = SidePanelImages.saveFromUri(appContext, result.slot, uri)
                        val owner = result.activity.get()
                        if (owner == null) {
                            pending.remove(requestCode, result)
                            return@Thread
                        }
                        owner.runOnUiThread {
                            if (!pending.remove(requestCode, result) ||
                                owner.isFinishing || owner.isDestroyed
                            ) {
                                return@runOnUiThread
                            }
                            result.callback(
                                if (success) SidePanelPickResult.SAVED
                                else SidePanelPickResult.FAILED
                            )
                        }
                    }, "Hchat-SidePanelImage").start()
                }
            })
        }.onFailure { resultHookedClasses.remove(clazz) }
    }

    @Synchronized
    private fun hookActivityDestroy(clazz: Class<*>) {
        if (!destroyHookedClasses.add(clazz)) return
        runCatching {
            XposedBridge.hookAllMethods(clazz, "onDestroy", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    pending.entries.forEach { entry ->
                        val owner = entry.value.activity.get()
                        if (owner == null || owner === activity) {
                            pending.remove(entry.key, entry.value)
                        }
                    }
                }
            })
        }.onFailure { destroyHookedClasses.remove(clazz) }
    }

    private fun takeReadPermission(activity: Activity, data: Intent, uri: Uri) {
        if (uri.scheme != "content") return
        runCatching {
            val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if ((flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }
}

internal enum class SidePanelPickResult {
    SAVED,
    CANCELLED,
    FAILED
}
