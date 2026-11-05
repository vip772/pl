package t.mma.hooks.items.automerge

import android.app.Activity
import android.content.SharedPreferences
import android.os.Looper
import android.view.View
import android.widget.CheckBox
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.FeatureResourceScope
import t.mma.hooks.core.HookRegistry
import t.mma.utils.HLog
import t.mma.utils.KavaReflector
import java.lang.reflect.Field
import java.lang.reflect.Method

/** Version-scoped bindings: never reuse obfuscated fields on an unverified host. */
internal class AutoMergeRuntime(
    private val context: FeatureContext,
    private val prefs: SharedPreferences,
    private val scope: FeatureResourceScope
) {
    private var reportedFailure = false

    fun install() {
        val host = context.hostContext()
        val version = host.packageManager.getPackageInfo(host.packageName, 0).versionName
        check(version == SUPPORTED_VERSION) { "自动勾选合并尚未适配微信 $version（已核实8.0.78）" }
        // Resolve both paths before installing either, so a partial binding fails closed.
        val bindings = listOf(resolve(false), resolve(true))
        bindings.forEach { binding ->
            val handle = HookRegistry.get().hook(binding.refresh, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (scope.isClosed() || param.hasThrowable()) return
                    try {
                        val activity = KavaReflector.readField(binding.activity, param.thisObject) as? Activity
                            ?: return
                        applySelection(binding, activity)
                    } catch (error: Throwable) {
                        if (!reportedFailure) {
                            reportedFailure = true
                            HLog.e("[Hchat:AutoMerge] 微信 $version 刷新失败: ${binding.refresh.toGenericString()}", error)
                        }
                    }
                }
            })
            scope.register { HookRegistry.get().unhook(handle) }
        }
    }

    private fun applySelection(binding: Binding, activity: Activity) {
        if (scope.isClosed() || !prefs.getBoolean(AutoMergeSettings.KEY_ENABLE, false)) return
        if (Looper.myLooper() != Looper.getMainLooper() || activity.isFinishing || activity.isDestroyed) return
        val intent = activity.intent ?: return
        if (intent.getIntExtra("query_source_type", -1) != 3 ||
            intent.getStringExtra("GalleryUI_ToUser").isNullOrBlank()) return
        val container = KavaReflector.readField(binding.container, activity) as? View ?: return
        val checkbox = KavaReflector.readField(binding.checkbox, activity) as? CheckBox ?: return
        val selected = KavaReflector.readField(binding.state, activity) as? Boolean ?: return
        if (container.visibility != View.VISIBLE || !container.isEnabled ||
            !checkbox.isEnabled || selected || checkbox.isChecked) return
        val items = if (binding.preview) {
            KavaReflector.readField(binding.media, activity) as? List<*> ?: return
        } else {
            val adapter = KavaReflector.readField(binding.adapter, activity) ?: return
            KavaReflector.readField(binding.media, adapter) as? List<*> ?: return
        }
        val selectedPaths = if (binding.preview) {
            val paths = KavaReflector.readField(binding.selectedPaths, activity) as? List<*> ?: return
            paths.filterIsInstance<String>().toHashSet()
        } else null
        val mediaCount = items.count { item ->
            val type = item?.let { KavaReflector.invokeOrThrow(binding.mediaType, it) }
            (type == 1 || type == 2) &&
                (selectedPaths == null || KavaReflector.readField(binding.mediaPath, item) in selectedPaths)
        }
        if (mediaCount < MIN_MEDIA) return
        // The host listener flips the sending boolean and updates the checkbox together.
        // Do not setChecked alone, reveal a hidden option, or invoke the send button.
        if (!scope.isClosed() && prefs.getBoolean(AutoMergeSettings.KEY_ENABLE, false)) {
            check(container.performClick()) { "合并选项缺少原生点击监听" }
            check(KavaReflector.readField(binding.state, activity) == true && checkbox.isChecked) {
                "合并选项显示与发送状态未同步"
            }
        }
    }

    private fun resolve(preview: Boolean): Binding {
        val page = requiredClass("com.tencent.mm.plugin.gallery.ui." +
            if (preview) "ImagePreviewUI" else "AlbumPreviewUI")
        val task = requiredClass(if (preview) "l93.q3\$\$k" else "l93.c0\$\$f")
        val media = requiredClass("com.tencent.mm.plugin.gallery.model.GalleryItem\$MediaItem")
        val adapter = if (preview) null else requiredField(page, "m")
        val listOwner = adapter?.type ?: page
        return Binding(
            preview,
            KavaReflector.findDeclaredMethod(task, "run") ?: error("缺少 ${task.name}.run()"),
            requiredField(task, "d").also { check(it.type == page) },
            requiredField(page, if (preview) "U1" else "j2").also { check(View::class.java.isAssignableFrom(it.type)) },
            requiredField(page, if (preview) "V1" else "k2").also { check(CheckBox::class.java.isAssignableFrom(it.type)) },
            requiredField(page, if (preview) "T1" else "i2").also { check(it.type == Boolean::class.javaPrimitiveType) },
            adapter,
            requiredField(listOwner, if (preview) "n" else "g").also { check(List::class.java.isAssignableFrom(it.type)) },
            if (preview) requiredField(page, "m").also { check(List::class.java.isAssignableFrom(it.type)) } else null,
            KavaReflector.findMethodRecursive(media, "getType") ?: error("缺少媒体类型方法"),
            requiredField(media, "e")
        )
    }

    private fun requiredClass(name: String): Class<*> =
        KavaReflector.loadClass(name, context.hostClassLoader()) ?: error("缺少微信类 $name")

    private fun requiredField(type: Class<*>, name: String): Field =
        KavaReflector.findDeclaredField(type, name) ?: error("缺少微信字段 ${type.name}.$name")

    private data class Binding(
        val preview: Boolean,
        val refresh: Method,
        val activity: Field,
        val container: Field,
        val checkbox: Field,
        val state: Field,
        val adapter: Field?,
        val media: Field,
        val selectedPaths: Field?,
        val mediaType: Method,
        val mediaPath: Field
    )

    companion object {
        private const val SUPPORTED_VERSION = "8.0.78"
        private const val MIN_MEDIA = 3
    }
}
