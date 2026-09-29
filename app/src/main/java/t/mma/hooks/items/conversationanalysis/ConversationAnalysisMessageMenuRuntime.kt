package t.mma.hooks.items.conversationanalysis

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

import android.graphics.drawable.Drawable
import android.view.MenuItem
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.message.SingleMessageMenuLocator
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.utils.KavaReflector
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

internal class ConversationAnalysisMessageMenuRuntime(
    private val context: FeatureContext,
    private val logger: (String, Throwable?) -> Unit
) {
    private data class Binding(val nativeMessage: Any, val talker: String)

    private val hookedMethods = Collections.newSetFromMap(ConcurrentHashMap<Method, Boolean>())
    private val byItem = Collections.synchronizedMap(WeakHashMap<MenuItem, Binding>())
    private val byGroup = ConcurrentHashMap<Int, Binding>()
    private val messageIdMethods = ConcurrentHashMap<Class<*>, Method>()

    @Synchronized
    fun install(): Boolean {
        val createCount = SingleMessageMenuLocator.menuCreateMethods(context, logger).count { method ->
            hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    addAnalysisMenu(param)
                }
            })
        }
        val clickCount = SingleMessageMenuLocator.menuClickMethods(context, logger).count { method ->
            hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    handleAnalysisClick(param)
                }
            })
        }
        if (createCount == 0) logger("群聊分析消息菜单创建 Hook 未安装", null)
        if (clickCount == 0) logger("群聊分析消息菜单点击 Hook 未安装", null)
        return createCount > 0 && clickCount > 0
    }

    fun destroy() {
        clearBindings()
    }

    private fun hook(method: Method, callback: XC_MethodHook): Boolean {
        if (Modifier.isAbstract(method.modifiers) || method.declaringClass.isInterface) return false
        if (!hookedMethods.add(method)) return true
        return runCatching {
            HookRegistry.get().hook(KavaReflector.accessible(method) ?: method, callback)
            true
        }.getOrElse {
            hookedMethods.remove(method)
            logger("群聊分析消息菜单 Hook 安装失败: ${method.toGenericString()}", it)
            false
        }
    }

    private fun addAnalysisMenu(param: XC_MethodHook.MethodHookParam) {
        clearBindings()
        if (!ConversationAnalysisSettings.isEnabled(context.hostContext())) return
        val view = param.args?.getOrNull(1) as? View ?: return
        val native = resolveNativeMessage(view.tag) ?: return
        val talker = resolveTalker(native) ?: return
        if (!isGroup(talker)) return
        val menu = param.args?.getOrNull(0) ?: return
        val item = addMenuItem(menu, view) ?: return
        val binding = Binding(native, talker)
        byItem[item] = binding
        byGroup[item.groupId] = binding
    }

    private fun handleAnalysisClick(param: XC_MethodHook.MethodHookParam) {
        if (!ConversationAnalysisSettings.isEnabled(context.hostContext())) return
        val item = param.args?.firstNotNullOfOrNull { it as? MenuItem } ?: return
        if (item.itemId != MENU_ITEM_ID) return
        val binding = byItem.remove(item) ?: byGroup.remove(item.groupId)
        clearBindings()
        if (binding == null || !isGroup(binding.talker)) return
        param.result = null
        val activity = WeChatApis.currentActivity()?.currentActivity() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed) {
                ConversationAnalysisReportDialog.show(activity, binding.talker, logger)
            }
        }
    }

    private fun addMenuItem(menu: Any, view: View): MenuItem? {
        (KavaReflector.invokeMethod(menu, "findItem", MENU_ITEM_ID) as? MenuItem)?.let { existing ->
            existing.icon = AnalysisMenuDrawable(view.resources.displayMetrics.density)
            return existing
        }
        val groupId = readMenuGroupId(menu)
        val title = MENU_TITLE
        val added = KavaReflector.invokeMethod(menu, "add", groupId, MENU_ITEM_ID, 0, title)
            ?: KavaReflector.invokeMethod(menu, "add", groupId, MENU_ITEM_ID, 0, title as CharSequence)
        val item = (added as? MenuItem)
            ?: (KavaReflector.invokeMethod(menu, "findItem", MENU_ITEM_ID) as? MenuItem)
        item?.icon = AnalysisMenuDrawable(view.resources.displayMetrics.density)
        return item
    }

    private fun readMenuGroupId(menu: Any): Int {
        val size = (KavaReflector.invokeMethod(menu, "size") as? Number)?.toInt() ?: return 0
        for (index in 0 until size) {
            val item = KavaReflector.invokeMethod(menu, "getItem", index) as? MenuItem ?: continue
            return item.groupId
        }
        return 0
    }

    private fun resolveNativeMessage(source: Any?): Any? {
        if (source == null) return null
        if (isNativeMessage(source) && messageId(source) > 0L) return source
        var current: Class<*>? = source.javaClass
        while (current != null && current != Any::class.java) {
            for (field in KavaReflector.declaredFields(current)) {
                if (KavaReflector.isStatic(field) || !isNativeMessageClass(field.type)) continue
                val value = KavaReflector.readField(field, source) ?: continue
                if (messageId(value) > 0L) return value
            }
            current = current.superclass
        }
        current = source.javaClass
        while (current != null && current != Any::class.java) {
            for (method in KavaReflector.declaredMethods(current)) {
                if (KavaReflector.isStatic(method) || method.parameterTypes.isNotEmpty() || !isNativeMessageClass(method.returnType)) continue
                val value = KavaReflector.invoke(method, source) ?: continue
                if (messageId(value) > 0L) return value
            }
            current = current.superclass
        }
        return null
    }

    private fun resolveTalker(native: Any): String? {
        val direct = readString(native, "getTalker", "field_talker", "talker")
        if (direct.isNotBlank()) return direct
        val id = messageId(native)
        if (id <= 0L) return null
        return runCatching { WeChatApis.messageStore()?.getMessageById(id)?.talker }.getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun isGroup(talker: String): Boolean =
        talker.endsWith("@chatroom") || talker.endsWith("@im.chatroom") ||
            (WeChatApis.contacts()?.isGroup(talker) == true)

    private fun isNativeMessage(value: Any): Boolean =
        isNativeMessageClass(value.javaClass) && messageId(value) > 0L

    private fun isNativeMessageClass(clazz: Class<*>): Boolean =
        clazz.name.startsWith("com.tencent.mm.storage.")

    private fun messageId(message: Any): Long {
        messageIdMethods[message.javaClass]?.let { method ->
            (KavaReflector.invoke(method, message) as? Number)?.toLong()?.let { return it }
        }
        val method = KavaReflector.declaredMethods(message.javaClass).firstOrNull {
            !KavaReflector.isStatic(it) && it.parameterTypes.isEmpty() &&
                it.name in setOf("getMsgId", "getMsgID", "getId") &&
                (it.returnType == Long::class.javaPrimitiveType || it.returnType == Long::class.java)
        }
        if (method != null) {
            messageIdMethods[message.javaClass] = method
            (KavaReflector.invoke(method, message) as? Number)?.toLong()?.let { return it }
        }
        return (KavaReflector.invokeMethod(message, "getMsgId") as? Number)?.toLong()
            ?: (KavaReflector.readField(message, "field_msgId") as? Number)?.toLong()
            ?: (KavaReflector.readField(message, "msgId") as? Number)?.toLong()
            ?: 0L
    }

    private fun readString(source: Any, getter: String, field: String, fallback: String): String =
        (KavaReflector.invokeMethod(source, getter) as? String).orEmpty().ifBlank {
            (KavaReflector.readField(source, field) as? String).orEmpty()
        }.ifBlank {
            (KavaReflector.readField(source, fallback) as? String).orEmpty()
        }

    private fun clearBindings() {
        byItem.clear()
        byGroup.clear()
    }

    private companion object {
        const val MENU_ITEM_ID = 0x4843414E
        const val MENU_TITLE = "分析"
    }
}

private class AnalysisMenuDrawable(private val density: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.BLACK
    }

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val unit = density
        val halfWidth = 13f * unit
        val halfHeight = 16f * unit
        val radius = 5f * unit
        canvas.drawRoundRect(
            RectF(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight),
            radius,
            radius,
            paint
        )
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(cx - 8f * unit, cy - 8f * unit, cx + 8f * unit, cy - 5f * unit), 1.5f * unit, 1.5f * unit, paint)
        canvas.drawRoundRect(RectF(cx - 8f * unit, cy - 1.5f * unit, cx + 10f * unit, cy + 1.5f * unit), 1.5f * unit, 1.5f * unit, paint)
        canvas.drawRoundRect(RectF(cx - 8f * unit, cy + 5f * unit, cx + 4f * unit, cy + 8f * unit), 1.5f * unit, 1.5f * unit, paint)
        paint.style = Paint.Style.STROKE
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}