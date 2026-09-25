package h.Hchat.hooks.items.mentionhighlight

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import h.Hchat.hooks.api.core.WeChatApis
import h.Hchat.hooks.api.model.WeChatMessage
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.HLog
import h.Hchat.utils.KavaReflector
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

class MentionHighlightFeature : BaseFeature() {
    override fun featureId() = ID
    override fun name() = "艾特高亮"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(MentionHighlightSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        DexInstallScheduler.schedule(ID, name()) {
            MentionHighlightRuntime(context).install()
        }
    }

    companion object {
        const val ID = "mention_highlight"
    }
}

private class MentionHighlightRuntime(private val context: FeatureContext) {
    private val messages = Collections.synchronizedMap(WeakHashMap<View, WeChatMessage>())
    private var installed = false
    private val reentry = ThreadLocal<Boolean>()

    @Synchronized
    fun install(): Boolean {
        if (installed) return true
        val bind = locateBindMethod() ?: run {
            HLog.e(TAG + " 聊天消息绑定方法定位失败")
            return false
        }
        HookRegistry.get().hook(bind, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                captureBinding(param)
            }
        })
        val textMethods = linkedSetOf<Method>()
        val bufferType = TextView.BufferType::class.java
        listOf(
            TextView::class.java,
            KavaReflector.loadClass("com.tencent.neattextview.textview.view.NeatTextView", context.hostClassLoader()),
            KavaReflector.loadClass("com.tencent.mm.ui.widget.MMNeat7extView", context.hostClassLoader())
        ).forEach { clazz ->
            clazz ?: return@forEach
            KavaReflector.findMethodRecursive(clazz, "setText", CharSequence::class.java, bufferType)
                ?.let { method ->
                    if (textMethods.add(method)) HookRegistry.get().hook(method, textHook)
                }
        }
        installed = textMethods.isNotEmpty()
        HLog.i(TAG + " 安装完成 bind=true textMethods=${textMethods.size}")
        return installed
    }

    private val textHook = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            if (reentry.get() == true) return
            val view = param.thisObject as? View ?: return
            val prefs = MentionHighlightSettings.preferences(context.hostContext())
            if (!prefs.getBoolean(MentionHighlightSettings.KEY_ENABLE, false)) return
            val message = findMessage(view) ?: return
            val input = param.args?.firstOrNull() as? CharSequence ?: return
            val result = buildMentionText(input, message, prefs.getString(
                MentionHighlightSettings.KEY_COLOR,
                MentionHighlightSettings.DEFAULT_COLOR
            ) ?: MentionHighlightSettings.DEFAULT_COLOR)
            HLog.d(TAG + " setText前置 enabled=true class=${view.javaClass.name} spanned=${input is Spanned} mentions=${result.mentionCount} applied=${result.appliedCount}")
            if (result.appliedCount == 0) return
            param.args?.set(0, result.text)
            if (view is TextView && view.movementMethod == null) {
                view.movementMethod = LinkMovementMethod.getInstance()
            }
        }
    }

    private fun captureBinding(param: XC_MethodHook.MethodHookParam) {
        val holder = param.args?.firstOrNull() ?: return
        val position = param.args?.getOrNull(1) as? Int ?: return
        val root = findRoot(holder) ?: return
        val item = adapterItem(param.thisObject ?: return, position) ?: return
        val native = resolveNativeMessage(item) ?: return
        val message = messageFromNative(native) ?: return
        messages[root] = message
        HLog.d(TAG + " bind msgId=${message.msgId} talker=${message.talker} atUsers=${message.getAtUserList().size}")
    }

    private fun buildMentionText(text: CharSequence, message: WeChatMessage, spec: String): BuildResult {
        val ids = message.getEffectiveAtUserList()
        if (ids.isEmpty()) return BuildResult(text, 0, 0)
        val colors = MentionHighlightSettings.parseColors(spec)
        val builder = SpannableStringBuilder(text)
        val nativeSpans = if (text is Spanned) {
            text.getSpans(0, text.length, ClickableSpan::class.java)
                .filter { span ->
                    val name = span.javaClass.name
                    name == "lx2" || name.endsWith(".lx2")
                }
                .mapNotNull { span ->
                    val start = text.getSpanStart(span)
                    val end = text.getSpanEnd(span)
                    if (start >= 0 && end > start) start until end else null
                }
                .distinct()
                .sortedBy { it.first }
        } else emptyList()
        val ranges = if (nativeSpans.isNotEmpty()) nativeSpans else if (text is Spanned) {
            text.getSpans(0, text.length, ClickableSpan::class.java)
                .mapNotNull { span ->
                    val start = text.getSpanStart(span)
                    val end = text.getSpanEnd(span)
                    if (start >= 0 && end > start && text[start] == '@') start until end else null
                }
                .distinct()
                .sortedBy { it.first }
        } else emptyList()
        val rendered = text.toString()
        val fallbackRanges: List<IntRange?> = if (ranges.isEmpty()) {
            val result = ArrayList<IntRange?>()
            var searchFrom = 0
            ids.forEach { user ->
                if (isAtAll(user)) {
                    result += null
                } else {
                    val displayName = runCatching {
                        WeChatApis.contacts()?.getGroupMemberDisplayName(message.talker, user).orEmpty()
                    }.getOrDefault("")
                    val atName = if (displayName.isNotBlank()) "@$displayName" else ""
                    val at = if (atName.isNotEmpty()) rendered.indexOf(atName, searchFrom) else -1
                    if (at >= 0) {
                        result += at until (at + atName.length)
                        searchFrom = at + atName.length
                    } else {
                        val marker = rendered.indexOf('@', searchFrom)
                        val separator = if (marker >= 0) rendered.indexOf(AT_SEPARATOR, marker + 1) else -1
                        if (marker >= 0 && separator >= 0) {
                            result += marker until (separator + 1)
                            searchFrom = separator + 1
                        } else {
                            result += null
                        }
                    }
                }
            }
            result
        } else emptyList()
        var cursor = 0
        var index = 0
        var mentionCount = 0
        var appliedCount = 0
        while (index < ids.size) {
            val range = ranges.getOrNull(index) ?: fallbackRanges.getOrNull(index) ?: run {
                val at = rendered.indexOf('@', cursor)
                if (at < 0) break
                val separator = rendered.indexOf(AT_SEPARATOR, at + 1)
                if (separator < 0) break
                at until (separator + 1)
            }
            val start = range.first
            val end = range.last + 1
            val user = ids[index]
            mentionCount++
            if (!isAtAll(user) && end <= builder.length) {
                builder.setSpan(ProfileSpan { view -> openProfile(view, message.talker, user) }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(MentionColorSpan(colors.first, colors.second), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                appliedCount++
            }
            cursor = end
            index++
        }
        return BuildResult(if (appliedCount > 0) builder else text, mentionCount, appliedCount)
    }

    private fun openProfile(view: View, room: String, user: String) {
        val intent = Intent().setComponent(ComponentName(view.context.packageName, "com.tencent.mm.plugin.profile.ui.ContactInfoUI"))
            .putExtra("Contact_User", user)
            .putExtra("Contact_ChatRoomId", room)
            .putExtra("room_name", room)
            .putExtra("Contact_Scene", 14)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (view.context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { view.context.startActivity(intent) }
            .onFailure { HLog.e(TAG + " 资料页打开失败 user=$user room=$room", it) }
    }

    private fun targetTextView(view: View): TextView? {
        if (view is TextView) return view
        return (KavaReflector.invokeMethod(view, "getWrappedTextView") as? TextView)
            ?: findTextViewChild(view)
    }

    private fun findTextViewChild(view: View): TextView? {
        if (view is TextView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findTextViewChild(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun renderedText(view: View, textView: TextView): CharSequence? {
        return (KavaReflector.invokeMethod(view, "a") as? CharSequence)
            ?: (KavaReflector.readField(view, "x") as? CharSequence)
            ?: textView.text
    }

    private fun findMessage(view: View): WeChatMessage? {
        var current: View? = view
        while (current != null) {
            messages[current]?.let { return it }
            current = current.parent as? View
        }
        return null
    }

    private fun findRoot(value: Any): View? {
        if (value is View) return value
        return KavaReflector.readField(value, "itemView") as? View
            ?: KavaReflector.declaredFields(value.javaClass).firstNotNullOfOrNull { field ->
                KavaReflector.readField(field, value) as? View
            }
    }

    private fun adapterItem(adapter: Any, position: Int): Any? {
        var current: Class<*>? = adapter.javaClass
        while (current != null && current != Any::class.java) {
            KavaReflector.declaredMethods(current).firstOrNull {
                it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Int::class.java &&
                    it.name in setOf("J0", "getItem", "get")
            }?.let { KavaReflector.invoke(it, adapter, position)?.let { item -> return item } }
            KavaReflector.declaredFields(current).firstOrNull {
                it.name in setOf("K", "items", "data", "list")
            }?.let { field ->
                val list = KavaReflector.readField(field, adapter)
                if (list is List<*> && position in list.indices) return list[position]
                KavaReflector.invoke(KavaReflector.findMethod(list?.javaClass, "get", Int::class.java), list, position)
                    ?.let { item -> return item }
            }
            current = current.superclass
        }
        return null
    }

    private fun resolveNativeMessage(source: Any): Any? {
        return resolveNativeMessage(source, Collections.newSetFromMap(WeakHashMap()), 0)
    }

    private fun resolveNativeMessage(source: Any?, visited: MutableSet<Any>, depth: Int): Any? {
        if (source == null || depth > 4 || !visited.add(source)) return null
        if (isLikelyMessage(source) && numberValue(source, "getMsgId", "getMsgID", "field_msgId", "msgId", "msgID") > 0) return source
        if (source is View || source is ViewGroup || source.javaClass.name.startsWith("java.")) return null
        if (source is Collection<*>) return source.firstNotNullOfOrNull { resolveNativeMessage(it, visited, depth + 1) }
        var current: Class<*>? = source.javaClass
        while (current != null && current != Any::class.java) {
            for (field in KavaReflector.declaredFields(current)) {
                if (field.type.isPrimitive || field.type.isArray || field.type == String::class.java) continue
                val value = KavaReflector.readField(field, source) ?: continue
                resolveNativeMessage(value, visited, depth + 1)?.let { return it }
            }
            current = current.superclass
        }
        return null
    }

    private fun isLikelyMessage(value: Any): Boolean {
        return value.javaClass.name.startsWith("com.tencent.mm.storage.") ||
            KavaReflector.declaredMethods(value.javaClass).any { method ->
                method.parameterTypes.isEmpty() && method.name in setOf("getMsgId", "getMsgID")
            }
    }

    private fun messageFromNative(source: Any): WeChatMessage? {
        val msgId = numberValue(source, "getMsgId", "getMsgID", "field_msgId", "msgId", "msgID")
        if (msgId <= 0) return null
        val stored = runCatching { WeChatApis.messageStore()?.getMessageById(msgId) }.getOrNull()
        val content = stringValue(source, "getContent", "field_content", "content")
            .ifBlank { stored?.content.orEmpty() }
        val type = numberValue(source, "getType", "field_type", "type").toInt().takeIf { it > 0 }
            ?: stored?.type?.takeIf { it > 0 }
            ?: WeChatMessage.inferType(content)
        val talker = stringValue(source, "getTalker", "field_talker", "talker")
            .ifBlank { stored?.talker.orEmpty() }
            .ifBlank { runCatching { WeChatApis.chatPage()?.currentTalker().orEmpty() }.getOrDefault("") }
        val msgSource = stringValue(
            source,
            "getMsgSource", "field_msgSource", "msgSource",
            "getG", "field_G", "G", "g"
        ).ifBlank { stored?.msgSource.orEmpty() }
        val reserved = stringValue(source, "getReserved", "field_reserved", "reserved")
            .ifBlank { stored?.reserved.orEmpty() }
        val merged = WeChatMessage(
            msgId, stored?.msgSvrId ?: 0L, type, stored?.status ?: 0,
            numberValue(source, "getIsSend", "field_isSend", "isSend").toInt(),
            stored?.createTime ?: 0L, talker, content, stored?.imagePath.orEmpty(),
            reserved, stored?.translatedContent.orEmpty(), stored?.flag ?: 0,
            msgSource, stored?.selfWxId.orEmpty()
        )
        HLog.d(TAG + " message msgId=$msgId contentLen=${content.length} msgSourceLen=${msgSource.length} reservedLen=${reserved.length} atUsers=${merged.getAtUserList().size}")
        return merged
    }

    private fun stringValue(source: Any, vararg names: String): String = names.firstNotNullOfOrNull { name ->
        val value = if (name.startsWith("get")) {
            KavaReflector.invoke(KavaReflector.findMethod(source.javaClass, name), source)
        } else KavaReflector.readField(source, name)
        value as? String
    }.orEmpty()

    private fun numberValue(source: Any, vararg names: String): Long = names.firstNotNullOfOrNull { name ->
        val value = if (name.startsWith("get")) {
            KavaReflector.invoke(KavaReflector.findMethod(source.javaClass, name), source)
        } else KavaReflector.readField(source, name)
        when (value) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    } ?: 0L

    private fun locateBindMethod(): Method? = runCatching {
        context.dexKitBridge().findMethod(FindMethod().apply {
            matcher(MethodMatcher().apply {
                usingStrings(listOf("MicroMsg.ChattingDataAdapterV3", "_onBindViewHolder[", "msgInfo"))
            })
        }).mapNotNull { runCatching { it.getMethodInstance(context.hostClassLoader()) }.getOrNull() }
            .firstOrNull { it.parameterTypes.size == 2 && it.parameterTypes[1] == Int::class.javaPrimitiveType }
    }.getOrElse { HLog.e(TAG + " 绑定方法定位异常", it); null }

    private fun isAtAll(user: String): Boolean =
        user.equals("notify@all", true) || user.equals("announcement@all", true)

    private data class BuildResult(val text: CharSequence, val mentionCount: Int, val appliedCount: Int)

    private class ProfileSpan(private val action: (View) -> Unit) : ClickableSpan() {
        override fun onClick(widget: View) = action(widget)
        override fun updateDrawState(ds: TextPaint) { ds.isUnderlineText = false }
    }

    private class MentionColorSpan(private val start: Int, private val end: Int) : CharacterStyle(), UpdateAppearance {
        override fun updateDrawState(paint: TextPaint) {
            paint.shader = LinearGradient(0f, 0f, paint.measureText("艾特对象").coerceAtLeast(1f), 0f, start, end, Shader.TileMode.CLAMP)
            paint.isFakeBoldText = true
        }
    }

    companion object {
        private const val TAG = "[Hchat:MentionHighlight]"
        private const val AT_SEPARATOR = '\u2005'
    }
}