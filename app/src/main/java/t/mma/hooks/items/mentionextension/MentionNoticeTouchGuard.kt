package t.mma.hooks.items.mentionextension

import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.FeatureResourceScope
import t.mma.hooks.core.HookRegistry
import t.mma.utils.HLog
import t.mma.utils.KavaReflector
import java.lang.reflect.Method

/** Stop the host system-row listener before it presses its wrapped TextView. */
internal object MentionNoticeTouchGuard {
    private const val TARGET = "com.tencent.mm.pluginsdk.ui.span.y#onTouch(android.view.View,android.view.MotionEvent)"
    fun install(context: FeatureContext, resources: FeatureResourceScope, methods: MutableSet<Method>): Boolean {
        return try {
            installChecked(context, resources, methods).also { installed ->
                if (!installed) report("定位或安装", IllegalStateException("原生触摸入口不可用"))
            }
        } catch (error: Throwable) {
            report("安装", error)
            false
        }
    }
    private fun report(stage: String, error: Throwable) {
        val version = runCatching { WeChatApis.version()?.current() }.getOrNull()
        HLog.e("[Hchat:艾特提醒] 按压拦截失败 stage=$stage target=$TARGET 微信=${version?.versionName}/${version?.versionCode}", error)
    }
    private fun installChecked(context: FeatureContext, resources: FeatureResourceScope, methods: MutableSet<Method>): Boolean {
        if (resources.isClosed()) return true
        val loader = context.hostClassLoader()
        val listener = KavaReflector.loadClass("com.tencent.mm.pluginsdk.ui.span.y", loader) ?: return false
        val touch = KavaReflector.findMethodRecursive(listener, "onTouch", View::class.java, MotionEvent::class.java)
            ?: return false
        if (touch in methods) return true
        val neat = KavaReflector.loadClass("com.tencent.neattextview.textview.view.NeatTextView", loader)
            ?: return false
        val wrappedText = KavaReflector.findMethodRecursive(neat, "getWrappedTextView") ?: return false
        var reported = false
        val hook = HookRegistry.get().hook(touch, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (resources.isClosed() || !MentionNoticeSettings.enabled(context.hostContext())) return
                val view = param.args?.getOrNull(0) as? View ?: return
                try {
                    val textView = when {
                        view is TextView -> view
                        neat.isInstance(view) -> KavaReflector.invokeOrThrow(wrappedText, view) as? TextView
                        else -> null
                    } ?: return
                    if (!isNotice(textView.text)) return
                    // Preserve listeners and backgrounds for recycled rows and disabled features.
                    view.isPressed = false
                    textView.isPressed = false
                    param.result = true
                } catch (error: Throwable) {
                    if (!reported) {
                        reported = true
                        report("触摸处理", error)
                    }
                }
            }
        }) ?: return false
        methods.add(touch)
        resources.register { HookRegistry.get().unhook(hook) }
        return true
    }

    private fun isNotice(text: CharSequence?): Boolean = text != null &&
        (text.startsWith(MentionNoticeSettings.MARKER) || text.startsWith(MentionNoticeSettings.PREFIX))
}
