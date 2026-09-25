package h.Hchat.hooks.items.mentionhighlight

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ViewConfiguration
import java.lang.reflect.Modifier
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
import java.util.ArrayDeque
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
    private val gestures = WeakHashMap<View, Gesture>()
    private val bindStates = ThreadLocal.withInitial { ArrayDeque<BindState>() }
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
            override fun beforeHookedMethod(param: MethodHookParam) {
                // Push a sentinel BEFORE resolving: failures/nested binds must not expose an outer message.
                val states = bindStates.get()
                val frame = BindState(null)
                states.addLast(frame)
                try {
                    frame.message = captureBinding(param).message
                } catch (error: Throwable) {
                    HLog.e(TAG + " 绑定上下文解析失败", error)
                }
            }
            override fun afterHookedMethod(param: MethodHookParam) {
                val states = bindStates.get()
                if (states.isNotEmpty()) states.removeLast()
                if (states.isEmpty()) bindStates.remove()
            }
        })
        val textMethods = linkedSetOf<Method>()
        val touchMethods = linkedSetOf<Method>()
        KavaReflector.findMethodRecursive(TextView::class.java, "setText",
            CharSequence::class.java, TextView.BufferType::class.java)?.let { textMethods.add(it) }
        val classes = listOfNotNull(
            KavaReflector.loadClass("com.tencent.mm.view.x2c.X2CTextView", context.hostClassLoader()),
            KavaReflector.loadClass("com.tencent.neattextview.textview.view.NeatTextView", context.hostClassLoader()),
            KavaReflector.loadClass("com.tencent.mm.ui.widget.MMNeat7extView", context.hostClassLoader())
        )
        for (clazz in classes) {
            var current: Class<*>? = clazz
            while (current != null && current != View::class.java && current != TextView::class.java) {
                for (method in KavaReflector.declaredMethods(current)) {
                    val types = method.parameterTypes
                    if (Modifier.isStatic(method.modifiers) || Modifier.isAbstract(method.modifiers)) continue
                    if (types.isEmpty() || types[0] != CharSequence::class.java) continue
                    val supported = types.size == 1 ||
                        (types.size == 2 && types[1] == java.lang.Boolean.TYPE) ||
                        (types.size == 3 && types[1] == TextView.BufferType::class.java &&
                            types[2] == java.lang.Boolean::class.java)
                    if (supported) textMethods.add(method)
                }
                current = current.superclass
            }
        }
        for (method in textMethods) {
            HookRegistry.get().hook(method, textHook)
            HLog.i(TAG + " 文本入口 " + method.toGenericString())
        }
        val touchClasses = classes + listOfNotNull(TextView::class.java,
            KavaReflector.loadClass("com.tencent.mm.view.x2c.X2CTextView", context.hostClassLoader()))
        for (clazz in touchClasses) {
            val method = KavaReflector.findMethodRecursive(clazz, "onTouchEvent", MotionEvent::class.java) ?: continue
            // Never hook View.onTouchEvent globally as an accidental fallback.
            if (method.declaringClass == View::class.java) continue
            if (touchMethods.add(method)) HookRegistry.get().hook(method, touchHook)
        }
        installed = textMethods.isNotEmpty()
        HLog.i(TAG + " 安装完成 bind=true textMethods=${textMethods.size} touchMethods=${touchMethods.size}")
        return installed
    }

    private val textHook = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            if (reentry.get() == true) return
            val view = param.thisObject as? View ?: return
            val prefs = MentionHighlightSettings.preferences(context.hostContext())
            if (!prefs.getBoolean(MentionHighlightSettings.KEY_ENABLE, false)) return
            val message = bindStates.get().peekLast()?.message ?: return
            val input = param.args?.firstOrNull() as? CharSequence ?: return
            if (!message.isGroupChat() || !message.isText()) return
            val result = buildMentionText(input, message, prefs.getString(
                MentionHighlightSettings.KEY_COLOR,
                MentionHighlightSettings.DEFAULT_COLOR
            ) ?: MentionHighlightSettings.DEFAULT_COLOR)
            HLog.d(TAG + " setText前置 msgId=${message.msgId} enabled=true class=${view.javaClass.name} spanned=${input is Spanned} mentions=${result.mentionCount} applied=${result.appliedCount}")
            if (result.appliedCount == 0) return
            param.args?.set(0, result.text)

        }
    }

    private val touchHook = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val view = param.thisObject as? View ?: return
            val event = param.args?.firstOrNull() as? MotionEvent ?: return
            if (!MentionHighlightSettings.preferences(context.hostContext())
                    .getBoolean(MentionHighlightSettings.KEY_ENABLE, false)) {
                gestures.remove(view)
                return
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gestures.remove(view)
                    val span = hitMention(view, event.x, event.y) ?: return
                    gestures[view] = Gesture(span, event.x, event.y, event.eventTime)
                    param.setResult(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val gesture = gestures[view] ?: return
                    val slop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
                    val dx = event.x - gesture.x
                    val dy = event.y - gesture.y
                    if (dx * dx + dy * dy > slop * slop) gesture.cancelled = true
                    param.setResult(true)
                }
                MotionEvent.ACTION_UP -> {
                    val gesture = gestures.remove(view) ?: return
                    val slop = ViewConfiguration.get(view.context).scaledTouchSlop.toFloat()
                    val dx = event.x - gesture.x
                    val dy = event.y - gesture.y
                    if (!gesture.cancelled && dx * dx + dy * dy <= slop * slop &&
                        event.eventTime - gesture.time < ViewConfiguration.getLongPressTimeout() &&
                        hitMention(view, event.x, event.y) === gesture.span) {
                        gesture.span.onClick(view)
                        HLog.d(TAG + " 艾特触摸命中")
                    }
                    param.setResult(true)
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (gestures.remove(view) != null) param.setResult(true)
                }
            }
        }
    }

    private fun hitMention(view: View, eventX: Float, eventY: Float): ProfileSpan? {
        if (view is TextView) {
            val text = view.text as? Spanned ?: return null
            val layout = view.layout ?: return null
            val x = eventX - view.totalPaddingLeft + view.scrollX
            val y = eventY - view.totalPaddingTop + view.scrollY
            if (layout.lineCount == 0 || y < 0 || y >= layout.height) return null
            val line = layout.getLineForVertical(y.toInt())
            if (x < layout.getLineLeft(line) || x >= layout.getLineRight(line)) return null
            val offset = layout.getOffsetForHorizontal(line, x)
            return text.getSpans(offset, offset, ProfileSpan::class.java).firstOrNull {
                offset >= text.getSpanStart(it) && offset < text.getSpanEnd(it)
            }
        }
        // sx2/fx2: Neat's layout is NOT android.text.Layout.
        val text = (KavaReflector.invokeMethod(view, "a") as? CharSequence
            ?: KavaReflector.readField(view, "x") as? CharSequence) as? Spanned ?: return null
        val layout = KavaReflector.invokeMethod(view, "getLayout") ?: return null
        val visual = KavaReflector.invokeMethod(layout, "m") as? Iterable<*> ?: return null
        val x = eventX - ((KavaReflector.invokeMethod(view, "getHorizontalOffset") as? Number)?.toFloat() ?: 0f)
        val y = eventY - ((KavaReflector.invokeMethod(view, "getVerticalOffset") as? Number)?.toFloat() ?: 0f)
        for (entry in visual) {
            if (entry == null) continue
            val span = KavaReflector.readField(entry, "g") as? ProfileSpan ?: continue
            if (text.getSpanStart(span) < 0) continue
            val rects = KavaReflector.readField(entry, "d") as? Iterable<*> ?: continue
            if (rects.any { it is RectF && it.contains(x, y) }) return span
        }
        return null
    }

    private fun captureBinding(param: XC_MethodHook.MethodHookParam): BindState {
        val holder = param.args?.firstOrNull() ?: return BindState(null)
        val position = param.args?.getOrNull(1) as? Int ?: return BindState(null)
        val root = findRoot(holder) ?: return BindState(null)
        val item = adapterItem(param.thisObject ?: return BindState(null), position) ?: return BindState(null)
        val native = resolveNativeMessage(item) ?: return BindState(null)
        val message = messageFromNative(native) ?: return BindState(null)
        gestures.remove(root)
        HLog.d(TAG + " bind-before msgId=${message.msgId} talker=${message.talker} atUsers=${message.getEffectiveAtUserList().size}")
        return BindState(message)
    }

    private fun buildMentionText(text: CharSequence, message: WeChatMessage, spec: String): BuildResult {
        val ids = message.getEffectiveAtUserList().distinct()
        if (ids.isEmpty() || ids.size > 64 || text.length > 32768) return BuildResult(text, 0, 0)
        if (text is Spanned && text.getSpans(0, text.length, ProfileSpan::class.java).isNotEmpty()) {
            return BuildResult(text, 0, 0) // Nested text setter; never duplicate our spans.
        }
        val contacts = WeChatApis.contacts()
        val names = LinkedHashMap<String, List<String>>()
        for (user in ids) {
            names[user] = if (isAtAll(user)) listOf("所有人", "all", "everyone", "全員", "모두") else {
                val contact = runCatching { contacts?.getContact(user) }.getOrNull()
                listOf(
                    runCatching { contacts?.getGroupMemberRoomDisplayName(message.talker, user) }.getOrNull(),
                    contact?.nickname, contact?.remarkName, user
                ).filterNotNull().filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
            }
        }
        val colors = MentionHighlightSettings.parseColors(spec)
        val builder = SpannableStringBuilder(text)
        val raw = text.toString()
        var cursor = 0
        var applied = 0
        var found = 0
        while (cursor < raw.length) {
            val at = raw.indexOf('@', cursor)
            if (at < 0) break
            // Bind an explicit (range,user) pair. Never zip unrelated clickable ranges to IDs.
            val matches = ArrayList<Triple<String, Int, String>>()
            for ((user, candidates) in names) {
                for (name in candidates) {
                    val end = at + 1 + name.length
                    if (end > raw.length || !raw.startsWith(name, at + 1)) continue
                    val boundary = end == raw.length || raw[end] == '@' ||
                        Character.isWhitespace(raw[end]) || Character.isSpaceChar(raw[end])
                    if (boundary) matches.add(Triple(user, end, name))
                }
            }
            val longest = matches.maxOfOrNull { it.second }
            val best = matches.filter { it.second == longest }.distinctBy { it.first }
            if (best.size != 1) { cursor = at + 1; continue } // Ambiguous names: do not open a wrong person.
            val match = best[0]
            val end = match.second
            found++
            val overlaps = builder.getSpans(at, end, ClickableSpan::class.java)
            if (overlaps.any { builder.getSpanStart(it) < at || builder.getSpanEnd(it) > end }) {
                cursor = end
                continue
            }
            if (!isAtAll(match.first)) {
                for (span in overlaps) builder.removeSpan(span)
                builder.setSpan(ProfileSpan { view -> openProfile(view, message.talker, match.first) },
                    at, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            builder.setSpan(MentionColorSpan(colors.first, colors.second, raw.substring(at, end)),
                at, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            applied++
            cursor = end
        }
        return BuildResult(if (applied > 0) builder else text, found, applied)
    }
    private fun openProfile(view: View, room: String, user: String) {
        HLog.d(TAG + " 请求打开艾特成员资料页")
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

    private data class BindState(var message: WeChatMessage?)
    private data class Gesture(val span: ProfileSpan, val x: Float, val y: Float,
        val time: Long, var cancelled: Boolean = false)
    private data class BuildResult(val text: CharSequence, val mentionCount: Int, val appliedCount: Int)

    private class ProfileSpan(private val action: (View) -> Unit) : ClickableSpan() {
        override fun onClick(widget: View) = action(widget)
        override fun updateDrawState(ds: TextPaint) { ds.isUnderlineText = false }
    }

    private class MentionColorSpan(private val start: Int, private val end: Int, private val label: String) : CharacterStyle(), UpdateAppearance {
        override fun updateDrawState(paint: TextPaint) {
            paint.shader = LinearGradient(0f, 0f, paint.measureText(label).coerceAtLeast(1f), 0f, start, end, Shader.TileMode.CLAMP)
            paint.isFakeBoldText = true
        }
    }

    companion object {
        private const val TAG = "[Hchat:MentionHighlight]"
        private const val AT_SEPARATOR = '\u2005'
    }
}