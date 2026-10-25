package t.mma.hooks.items.mentionhighlight
import t.mma.utils.HLog

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ViewConfiguration
import java.lang.reflect.Modifier
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.util.LruCache
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.model.WeChatMessageTypes
import t.mma.hooks.api.model.WeChatMessage
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.utils.KavaReflector
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.ArrayDeque
import java.util.Collections
import java.util.WeakHashMap

class MentionHighlightFeature : BaseFeature() {
    override fun featureId() = ID
    override fun name() = "艾特高亮"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(t.mma.hooks.items.mentionextension.MentionExtensionSettingsProvider(), enabledCheck = {
            val host = context.hostContext()
            t.mma.hooks.items.hiddenat.HiddenAtSettings.preferences(host).getBoolean(t.mma.hooks.items.hiddenat.HiddenAtSettings.ENABLE, false) ||
                t.mma.hooks.items.emojiat.EmojiAtSettings.preferences(host).getBoolean(t.mma.hooks.items.emojiat.EmojiAtSettings.ENABLE, false) ||
                MentionHighlightSettings.preferences(host).getBoolean(MentionHighlightSettings.KEY_ENABLE, false) ||
                t.mma.hooks.items.mentionextension.MentionNoticeSettings.enabled(host)
        })
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
    private inline fun debugLog(message: () -> String) {
        if (DEBUG_RENDER_LOGS) HLog.d(message())
    }
    private val DEBUG_RENDER_LOGS = false
    private val gestures = WeakHashMap<View, Gesture>()
    private val renderedCache = LruCache<String, CharSequence>(96)
    // X2C may receive the same message again with a different CharSequence
    // representation (plain text vs. the Neat Spannable).  The original
    // implementation carries the rendered result by its hx2 message context,
    // not by the incoming CharSequence identity.  Keep that association too.
    private val renderedByMessage = LruCache<String, CharSequence>(96)
    private val bindStates = ThreadLocal.withInitial { ArrayDeque<BindState>() }
    private var installed = false
    // Bounded metadata only: receiver values are read again on every binding.
    private class BindingMembers {
        val methods = HashMap<String, Method?>()
        val fields = HashMap<String, Field?>()
    }
    private val bindingMembers = object : LinkedHashMap<Class<*>, BindingMembers>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Class<*>, BindingMembers>?): Boolean =
            size > 128
    }

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
        // Do not hook TextView.setText globally.  The original implementation
        // only processes the message renderer's text methods; a global hook
        // also sees timestamps, labels and recycled child views.
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
        // Do not hook TextView.onTouchEvent globally.  Only the message renderer
        // classes are allowed to consume touch events.
        val touchClasses = classes
        for (clazz in touchClasses) {
            val method = KavaReflector.findMethodRecursive(clazz, "onTouchEvent", MotionEvent::class.java) ?: continue
            // Never hook View.onTouchEvent globally as an accidental fallback.
            if (method.declaringClass == View::class.java ||
                method.declaringClass == TextView::class.java) continue
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
            val colorSpec = prefs.getString(
                MentionHighlightSettings.KEY_COLOR,
                MentionHighlightSettings.DEFAULT_COLOR
            ) ?: MentionHighlightSettings.DEFAULT_COLOR
            val cacheKey = cacheKey(message, input, colorSpec)
            val messageCacheKey = messageCacheKey(message, colorSpec)
            val inputCached = renderedCache.get(cacheKey)
            val messageCached = renderedByMessage.get(messageCacheKey)
            // X2C calls its final text entry again with a new/plain CharSequence
            // after MMNeat has already produced the coloured Spannable.  The
            // input-based key can never hit in that case; carry the rendered
            // result by message. The bind ThreadLocal is the safety boundary, so a
            // recycled holder cannot receive another message's spans.
            val cached = when {
                // A recycled X2C view can call setText while another message is
                // being bound. Never inject a cached Spannable unless the
                // visible text is exactly the same; msgId alone is not enough.
                messageCached != null && messageCached.toString() == input.toString() -> messageCached
                inputCached != null -> inputCached
                else -> null
            }
            if (input !is Spanned && cached != null) {
                param.args?.set(0, cached)
                return
            }
            val result = buildMentionText(input, message, colorSpec)
            val effective = if (result.appliedCount > 0) {
                renderedCache.put(cacheKey, result.text)
                renderedByMessage.put(messageCacheKey, result.text)
                result.text
            } else cached
            debugLog { val parsedColors = MentionHighlightSettings.parseColors(colorSpec); TAG + " setText前置 msgId=${message.msgId} enabled=true class=${view.javaClass.name} colorSpec=$colorSpec colors=${parsedColors.first},${parsedColors.second} spanned=${input is Spanned} mentions=${result.mentionCount} applied=${result.appliedCount} cacheHit=${cached != null} messageCache=${messageCached != null}" }
            if (effective == null) return
            param.args?.set(0, effective)
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
                        debugLog { TAG + " 艾特触摸命中" }
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
        // MMNeat7extView extends TextView, but its glyph hit-test is not
        // android.text.Layout.  The Neat layout must be checked first.
        if (view.javaClass.name.contains("MMNeat7extView") ||
            view.javaClass.name.contains("NeatTextView")) {
            return hitNeatMention(view, eventX, eventY)
        }
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
        for (entry in visual ?: emptyList<Any>()) {
            if (entry == null) continue
            val span = KavaReflector.readField(entry, "g") as? ProfileSpan ?: continue
            if (text.getSpanStart(span) < 0) continue
            val rects = KavaReflector.readField(entry, "d") as? Iterable<*> ?: continue
            if (rects.any { it is RectF && it.contains(x, y) }) return span
        }
        return null
    }

    private fun hitNeatMention(view: View, eventX: Float, eventY: Float): ProfileSpan? {
        val text = (KavaReflector.invokeMethod(view, "a") as? CharSequence
            ?: KavaReflector.readField(view, "x") as? CharSequence) as? Spanned ?: return null
        val layout = KavaReflector.invokeMethod(view, "getLayout") ?: return null
        val visual = KavaReflector.invokeMethod(layout, "m") as? Iterable<*>
        val horizontal = (KavaReflector.invokeMethod(view, "getHorizontalOffset") as? Number)?.toFloat() ?: 0f
        val vertical = (KavaReflector.invokeMethod(view, "getVerticalOffset") as? Number)?.toFloat() ?: 0f
        val x = eventX - horizontal
        val y = eventY - vertical
        for (entry in visual ?: emptyList<Any>()) {
            if (entry == null) continue
            val span = KavaReflector.readField(entry, "g") as? ProfileSpan ?: continue
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            if (start < 0 || end <= start) continue
            val rects = KavaReflector.readField(entry, "d") as? Iterable<*> ?: continue
            if (rects.any { it is RectF && it.contains(x, y) }) return span
        }
        // Neat's internal hit-test returns a UTF-16 offset and handles emoji /
        // combining marks correctly.  Use it only as a fallback when the
        // visual span records do not expose rectangles.
        val offset = KavaReflector.invokeMethod(
            layout, "k", x.toInt(), y.toInt()
        ) as? Number
        if (offset != null) {
            val safe = offset.toInt().coerceIn(0, text.length)
            return text.getSpans(safe, safe, ProfileSpan::class.java).firstOrNull {
                safe >= text.getSpanStart(it) && safe < text.getSpanEnd(it)
            }
        }
        return null
    }

    private fun captureBinding(param: XC_MethodHook.MethodHookParam): BindState {
        val holder = param.args?.firstOrNull() ?: return BindState(null)
        val position = param.args?.getOrNull(1) as? Int ?: return BindState(null)
        val root = findRoot(holder) ?: return BindState(null)
        val item = adapterItem(param.thisObject ?: return BindState(null), position) ?: return BindState(null)
        val native = resolveNativeMessage(item) ?: return BindState(null)
        gestures.remove(root)
        val message = messageFromNative(native) ?: return BindState(null)
        debugLog { TAG + " bind-before msgId=${message.msgId} talker=${message.talker} atUsers=${message.getEffectiveAtUserList().size}" }
        return BindState(message)
    }

    private fun cacheKey(message: WeChatMessage, text: CharSequence, spec: String): String =
        messageCacheKey(message, spec) + "|" + text.toString()
    private fun messageCacheKey(message: WeChatMessage, spec: String): String =
        message.msgId.toString() + "|" + spec + "|" + message.talker + "|" +
            message.msgSource + "|" + message.reserved

    private fun buildMentionText(text: CharSequence, message: WeChatMessage, spec: String): BuildResult {
        // Keep the wire order intact. The text ranges and atuserlist are
        // positional; distinct() would shift every later mention when the same
        // member is mentioned more than once.
        val ids = message.getEffectiveAtUserList()
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
                    runCatching { contacts?.getGroupMemberDisplayName(message.talker, user) }.getOrNull(),
                    runCatching { contacts?.getGroupMemberRoomDisplayName(message.talker, user) }.getOrNull(),
                    contact?.nickname, contact?.remarkName, user
                ).filterNotNull().filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
            }
        }
        val colors = MentionHighlightSettings.parseColors(spec)
        val builder = SpannableStringBuilder(text)
        val raw = text.toString()
        val nativeRanges = if (text is Spanned) {
            text.getSpans(0, text.length, ClickableSpan::class.java)
                .mapNotNull { span ->
                    val start = text.getSpanStart(span)
                    val end = text.getSpanEnd(span)
                    if (start >= 0 && end > start && start < raw.length && raw[start] == '@') start until end else null
                }
                .distinct()
                .sortedBy { it.first }
        } else emptyList()
        val orderedNativeRanges = if (nativeRanges.size == ids.size) {
            nativeRanges
        } else {
            debugLog { TAG + " 原生艾特范围未对齐: msgId=${message.msgId} " +
                "ranges=${nativeRanges.size} atUsers=${ids.size} reason=name-fallback" }
            emptyList()
        }
        var cursor = 0
        var rangeIndex = 0
        var applied = 0
        var found = 0
        while (cursor < raw.length) {
            val explicitRange = orderedNativeRanges.getOrNull(rangeIndex)
            val at = explicitRange?.first ?: raw.indexOf('@', cursor)
            if (at < 0) break
            // Prefer the actual WeChat span range. This preserves emoji, variation selectors and combining characters.
            if (explicitRange != null) {
                val user = ids.getOrNull(rangeIndex) ?: break
                val end = explicitRange.last + 1
                found++
                if (isAtAll(user)) {
                    debugLog { TAG + " 艾特跳过: msgId=${message.msgId} range=$at..$end user=$user reason=at-all" }
                    cursor = end
                    rangeIndex++
                    continue
                }
                // A highlighted mention must always carry our profile span.
                builder.setSpan(ProfileSpan { view -> openProfile(view, message.talker, user) },
                    at, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                applyForegroundColors(builder, raw, at, end, colors.first, colors.second)
                applied++
                debugLog { TAG + " 艾特处理成功: msgId=${message.msgId} range=$at..$end user=$user source=native" }
                cursor = end
                rangeIndex++
                continue
            }
            // Fallback only when the platform did not provide an explicit mention span.
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
            if (best.size != 1) {
                debugLog { TAG + " 艾特未处理: msgId=${message.msgId} offset=$at reason=" +
                    if (best.isEmpty()) "name-not-resolved" else "ambiguous-name" }
                cursor = at + 1
                continue
            }
            val match = best[0]
            val end = match.second
            found++
            if (isAtAll(match.first)) {
                debugLog { TAG + " 艾特跳过: msgId=${message.msgId} range=$at..$end user=${match.first} reason=at-all" }
                cursor = end
                continue
            }
            val overlaps = builder.getSpans(at, end, ClickableSpan::class.java)
            if (overlaps.any { builder.getSpanStart(it) < at || builder.getSpanEnd(it) > end }) {
                debugLog { TAG + " 艾特未处理: msgId=${message.msgId} range=$at..$end user=${match.first} reason=span-overlap" }
                cursor = end
                continue
            }
            // Replace only spans fully contained by this mention. The profile
            // span and the color are committed together, so color implies click.
            for (span in overlaps) builder.removeSpan(span)
            builder.setSpan(ProfileSpan { view -> openProfile(view, message.talker, match.first) },
                at, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            applyForegroundColors(builder, raw, at, end, colors.first, colors.second)
            applied++
            debugLog { TAG + " 艾特处理成功: msgId=${message.msgId} range=$at..$end user=${match.first} source=name" }
            cursor = end
        }
        return BuildResult(if (applied > 0) builder else text, found, applied)
    }
    private fun applyForegroundColors(builder: SpannableStringBuilder, raw: String, start: Int, end: Int, from: Int, to: Int) {
        val length = (end - start).coerceAtLeast(1)
        var index = start
        while (index < end) {
            val codePoint = raw.codePointAt(index)
            val count = Character.charCount(codePoint)
            val fraction = ((index - start).toFloat() / length).coerceIn(0f, 1f)
            val color = android.graphics.Color.argb(
                (android.graphics.Color.alpha(from) + (android.graphics.Color.alpha(to) - android.graphics.Color.alpha(from)) * fraction).toInt(),
                (android.graphics.Color.red(from) + (android.graphics.Color.red(to) - android.graphics.Color.red(from)) * fraction).toInt(),
                (android.graphics.Color.green(from) + (android.graphics.Color.green(to) - android.graphics.Color.green(from)) * fraction).toInt(),
                (android.graphics.Color.blue(from) + (android.graphics.Color.blue(to) - android.graphics.Color.blue(from)) * fraction).toInt()
            )
            builder.setSpan(ForegroundColorSpan(color), index, (index + count).coerceAtMost(end), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            index += count
        }
    }

    private fun openProfile(view: View, room: String, user: String) {
        debugLog { TAG + " 请求打开艾特成员资料页" }
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
        // Only proven ineligible native values can bypass enrichment. Blank/unknown
        // values must keep the database fallback (notably msgSource and reserved).
        val nativeType = numberValue(source, "getType", "field_type", "type").toInt()
        val nativeTalker = stringValue(source, "getTalker", "field_talker", "talker")
        if (nativeType > 0 && !WeChatMessageTypes.isText(nativeType)) return null
        if (nativeTalker.isNotBlank() && !WeChatMessage.isGroupTalker(nativeTalker)) return null
        val stored = runCatching { WeChatApis.messageStore()?.getMessageById(msgId) }.getOrNull()
        val content = stringValue(source, "getContent", "field_content", "content")
            .ifBlank { stored?.content.orEmpty() }
        val type = nativeType.takeIf { it > 0 }
            ?: stored?.type?.takeIf { it > 0 }
            ?: WeChatMessage.inferType(content)
        val talker = nativeTalker
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
        debugLog { TAG + " message msgId=$msgId contentLen=${content.length} msgSourceLen=${msgSource.length} reservedLen=${reserved.length} atUsers=${merged.getAtUserList().size}" }
        return merged
    }

    private fun bindingMethod(clazz: Class<*>, name: String): Method? = synchronized(bindingMembers) {
        val entry = bindingMembers.getOrPut(clazz) { BindingMembers() }
        if (!entry.methods.containsKey(name)) entry.methods[name] = KavaReflector.findMethod(clazz, name)
        entry.methods[name]
    }

    private fun bindingField(clazz: Class<*>, name: String): Field? = synchronized(bindingMembers) {
        val entry = bindingMembers.getOrPut(clazz) { BindingMembers() }
        if (!entry.fields.containsKey(name)) entry.fields[name] = KavaReflector.findDeclaredField(clazz, name)
        entry.fields[name]
    }

    private fun bindingFieldValue(receiver: Any, name: String): Any? {
        var current: Class<*>? = receiver.javaClass
        while (current != null && current != Any::class.java) {
            val field = bindingField(current, name)
            if (field != null) return KavaReflector.readField(field, receiver)
            current = current.superclass
        }
        return null
    }

    private fun stringValue(source: Any, vararg names: String): String = names.firstNotNullOfOrNull { name ->
        val value = if (name.startsWith("get")) {
            KavaReflector.invoke(bindingMethod(source.javaClass, name), source)
        } else bindingFieldValue(source, name)
        value as? String
    }.orEmpty()

    private fun numberValue(source: Any, vararg names: String): Long = names.firstNotNullOfOrNull { name ->
        val value = if (name.startsWith("get")) {
            KavaReflector.invoke(bindingMethod(source.javaClass, name), source)
        } else bindingFieldValue(source, name)
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

    companion object {
        private const val TAG = "[Hchat:MentionHighlight]"
        private const val AT_SEPARATOR = '\u2005'
    }
}
