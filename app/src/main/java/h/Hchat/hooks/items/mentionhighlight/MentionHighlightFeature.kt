package h.Hchat.hooks.items.mentionhighlight

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.UpdateAppearance
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
        DexInstallScheduler.schedule(ID, name()) { MentionHighlightRuntime(context).install() }
    }

    companion object { const val ID = "mention_highlight" }
}

private class MentionHighlightRuntime(private val context: FeatureContext) {
    private val states = Collections.synchronizedMap(WeakHashMap<View, RenderState>())
    private var installed = false

    @Synchronized
    fun install(): Boolean {
        if (installed) return true
        val method = locateAdapterBindMethod() ?: run {
            HLog.e(TAG + " 未定位到聊天消息绑定方法")
            return false
        }
        return runCatching {
            HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    apply(param)
                }
            })
            installed = true
            HLog.i(TAG + " 已安装消息绑定 Hook: ${method.declaringClass.name}#${method.name}")
            true
        }.getOrElse {
            HLog.e(TAG + " 安装消息绑定 Hook 失败", it)
            false
        }
    }

    private fun apply(param: XC_MethodHook.MethodHookParam) {
        val root = findRoot(param.args?.firstOrNull()) ?: return
        val position = param.args?.getOrNull(1) as? Int ?: return
        val prefs = MentionHighlightSettings.preferences(context.hostContext())
        if (!prefs.getBoolean(MentionHighlightSettings.KEY_ENABLE, false)) {
            clear(root)
            return
        }
        val adapter = param.thisObject ?: return
        val item = adapterItem(adapter, position) ?: return
        val msgId = readLong(item, "getMsgId", "getMsgID", "field_msgId", "msgId", "msgID")
        if (msgId <= 0L) return
        val message = WeChatApis.messageStore()?.getMessageById(msgId) ?: run {
            HLog.w(TAG + " 消息存储未命中 msgId=$msgId")
            return
        }
        if (message.getEffectiveAtUserList().isEmpty() && !message.bodyContent().contains("@")) return
        if (states[root]?.msgId == msgId && states[root]?.valid == true) return
        clear(root)
        val colors = MentionHighlightSettings.parseColors(
            prefs.getString(MentionHighlightSettings.KEY_COLOR, MentionHighlightSettings.DEFAULT_COLOR)
        )
        val targets = findTextTargets(root)
        var applied = false
        targets.forEach { target ->
            val view = target.host
            val textView = target.textView
            val text = renderedText(view, textView) ?: return@forEach
            if (text !is Spanned) return@forEach
            val spans = text.getSpans(0, text.length, ClickableSpan::class.java)
            spans.filter { span ->
                val start = text.getSpanStart(span)
                val end = text.getSpanEnd(span)
                isMentionText(text, start, end, message)
            }.forEachIndexed { mentionIndex, span ->
                val start = text.getSpanStart(span)
                val end = text.getSpanEnd(span)
                if (start < 0 || end <= start) return@forEachIndexed
                val flags = text.getSpanFlags(span)
                val highlight = MentionColorSpan(colors.first, colors.second)
                (text as Spannable).setSpan(highlight, start, end, flags)
                val user = message.getEffectiveAtUserList().getOrNull(mentionIndex).orEmpty()
                if (user.isNotBlank() && user != "notify@all" && user != "announcement@all") {
                    (text as Spannable).setSpan(ProfileClickableSpan { widget ->
                        openProfile(widget, message.talker, user)
                    }, start, end, flags)
                }
                applied = true
            }
            if (applied) {
                if (isNeatTextView(view)) {
                    KavaReflector.invoke(KavaReflector.findMethod(view.javaClass, "setText", CharSequence::class.java), view, text)
                } else if (textView.text !== text) {
                    textView.text = text
                }
                textView.movementMethod = android.text.method.LinkMovementMethod.getInstance()
                textView.highlightColor = Color.TRANSPARENT
                view.invalidate()
            }
        }
        if (applied) states[root] = RenderState(msgId, true)
    }

    private fun isMentionText(text: CharSequence, start: Int, end: Int, message: WeChatMessage): Boolean {
        if (start < 0 || end <= start || end > text.length) return false
        val value = text.subSequence(start, end).toString()
        if (!value.contains("@")) return false
        return message.getEffectiveAtUserList().isNotEmpty()
    }

    private fun resolveUserId(message: WeChatMessage, text: CharSequence, start: Int, end: Int): String {
        val visible = text.subSequence(start, end).toString().removePrefix("@").trim()
        if (visible.isBlank()) return ""
        val names = message.bodyContent().split("@").drop(1)
            .map { it.takeWhile { c -> !c.isWhitespace() && c != ':' && c != '：' } }
        val index = names.indexOfFirst { it == visible }
        return message.getEffectiveAtUserList().getOrNull(index).orEmpty()
    }

    private fun openProfile(view: View, room: String, user: String) {
        if (user.isBlank() || user == "notify@all" || user == "announcement@all") return
        val intent = Intent().setComponent(
            ComponentName(view.context.packageName, "com.tencent.mm.plugin.profile.ui.ContactInfoUI")
        ).putExtra("Contact_User", user)
            .putExtra("Contact_ChatRoomId", room)
            .putExtra("room_name", room)
            .putExtra("Contact_Scene", 14)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (view.context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { view.context.startActivity(intent) }
            .onFailure { HLog.e(TAG + " 打开资料页失败 user=$user", it) }
    }

    private fun clear(root: View) {
        states.remove(root)
        findTextTargets(root).forEach { target ->
            val text = renderedText(target.host, target.textView) as? Spannable ?: return@forEach
            text.getSpans(0, text.length, MentionColorSpan::class.java).forEach(text::removeSpan)
            text.getSpans(0, text.length, ProfileClickableSpan::class.java).forEach(text::removeSpan)
        }
    }

    private fun locateAdapterBindMethod(): Method? = runCatching {
        context.dexKitBridge().findMethod(FindMethod().apply {
            matcher(MethodMatcher().apply {
                usingStrings(listOf("MicroMsg.ChattingDataAdapterV3", "_onBindViewHolder[", "msgInfo"))
            })
        }).mapNotNull { runCatching { it.getMethodInstance(context.hostClassLoader()) }.getOrNull() }
            .firstOrNull { method ->
                method.parameterTypes.size == 2 &&
                    method.parameterTypes[1] == Int::class.javaPrimitiveType
            }
    }.getOrElse {
        HLog.e(TAG + " 定位聊天绑定方法异常", it)
        null
    }

    private fun adapterItem(adapter: Any, position: Int): Any? {
        var current: Class<*>? = adapter.javaClass
        while (current != null && current != Any::class.java) {
            val method = KavaReflector.declaredMethods(current).firstOrNull {
                it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    it.name in setOf("J0", "getItem", "get")
            }
            if (method != null) return KavaReflector.invoke(method, adapter, position)
            current = current.superclass
        }
        return null
    }

    private fun findRoot(value: Any?): View? = when (value) {
        is View -> value
        null -> null
        else -> KavaReflector.readField(value, "itemView") as? View
    }

    private fun findTextTargets(root: View): List<TextTarget> {
        if (root is TextView) return listOf(TextTarget(root, root))
        if (isNeatTextView(root)) {
            val wrapped = (KavaReflector.invoke(KavaReflector.findMethod(root.javaClass, "getWrappedTextView"), root) as? TextView)
                ?: TextView(root.context)
            return listOf(TextTarget(root, wrapped))
        }
        if (root !is ViewGroup) return emptyList()
        return (0 until root.childCount).flatMap { findTextTargets(root.getChildAt(it)) }
    }

    private fun renderedText(host: View, textView: TextView): CharSequence? {
        if (isNeatTextView(host)) {
            (KavaReflector.invoke(KavaReflector.findMethod(host.javaClass, "a"), host) as? CharSequence)?.let { return it }
            (KavaReflector.readField(host, "x") as? CharSequence)?.let { return it }
        }
        return textView.text
    }

    private fun isNeatTextView(view: View): Boolean {
        var current: Class<*>? = view.javaClass
        while (current != null && current != Any::class.java) {
            if (current.name.contains("NeatTextView") || current.name == "com.tencent.mm.ui.widget.MMNeat7extView") return true
            current = current.superclass
        }
        return false
    }

    private fun readLong(source: Any, vararg names: String): Long {
        names.forEach { name ->
            val value = if (name.startsWith("get")) {
                KavaReflector.invoke(KavaReflector.findMethod(source.javaClass, name), source)
            } else {
                KavaReflector.readField(source, name)
            }
            if (value is Number && value.toLong() > 0L) return value.toLong()
        }
        return 0L
    }

    private data class TextTarget(val host: View, val textView: TextView)
    private data class RenderState(val msgId: Long, val valid: Boolean)

    private class MentionColorSpan(private val startColor: Int, private val endColor: Int) : CharacterStyle(), UpdateAppearance {
        override fun updateDrawState(tp: TextPaint) {
            tp.shader = if (startColor == endColor) null else LinearGradient(
                0f, 0f, tp.measureText("艾特对象").coerceAtLeast(1f), 0f,
                startColor, endColor, Shader.TileMode.CLAMP
            )
            tp.color = startColor
            tp.isFakeBoldText = true
        }
    }

    private class ProfileClickableSpan(private val action: (View) -> Unit) : ClickableSpan() {
        override fun onClick(widget: View) = action(widget)
        override fun updateDrawState(ds: TextPaint) { ds.isUnderlineText = false }
    }

    private class MentionClickableSpan : ClickableSpan()

    companion object {
        private const val TAG = "[Hchat:MentionHighlight]"
    }
}