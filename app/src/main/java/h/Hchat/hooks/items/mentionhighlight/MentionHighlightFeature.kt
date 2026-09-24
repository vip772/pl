package h.Hchat.hooks.items.mentionhighlight

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.CharacterStyle
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
    override fun onFeatureInit(context: FeatureContext) { registerSettingsProvider(MentionHighlightSettingsProvider()) }
    override fun onFeatureInstall(context: FeatureContext) {
        DexInstallScheduler.schedule(ID, name()) { MentionHighlightRuntime(context).install() }
    }
    companion object { const val ID = "mention_highlight" }
}

private class MentionHighlightRuntime(private val context: FeatureContext) {
    private val messages = Collections.synchronizedMap(WeakHashMap<View, WeChatMessage>())
    private var installed = false

    @Synchronized
    fun install(): Boolean {
        if (installed) return true
        val bind = locateBindMethod() ?: return false
        HookRegistry.get().hook(bind, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) { captureBinding(param) }
        })
        val textMethods = linkedSetOf<Method>()
        listOf(TextView::class.java, "com.tencent.neattextview.textview.view.NeatTextView", "com.tencent.mm.ui.widget.MMNeat7extView")
            .forEach { target ->
                val clazz = if (target is Class<*>) target else runCatching { context.hostClassLoader().loadClass(target as String) }.getOrNull()
                clazz ?: return@forEach
                KavaReflector.declaredMethods(clazz).filter {
                    it.name == "setText" && it.parameterTypes.isNotEmpty() &&
                        CharSequence::class.java.isAssignableFrom(it.parameterTypes[0])
                }.forEach { method ->
                    if (textMethods.add(method)) HookRegistry.get().hook(method, textHook)
                }
            }
        installed = textMethods.isNotEmpty()
        HLog.i(TAG + " 文本Hook=$installed methods=${textMethods.size}")
        return installed
    }

    private val textHook = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val view = param.thisObject as? View ?: return
            val prefs = MentionHighlightSettings.preferences(context.hostContext())
            if (!prefs.getBoolean(MentionHighlightSettings.KEY_ENABLE, false)) return
            val message = findMessage(view) ?: return
            val input = param.args?.firstOrNull() as? CharSequence ?: return
            val output = buildMentionText(input, message, prefs.getString(MentionHighlightSettings.KEY_COLOR, MentionHighlightSettings.DEFAULT_COLOR))
            if (output != null) param.args[0] = output
        }
    }

    private fun captureBinding(param: XC_MethodHook.MethodHookParam) {
        val holder = param.args?.firstOrNull() ?: return
        val position = param.args?.getOrNull(1) as? Int ?: return
        val root = findRoot(holder) ?: return
        val adapter = param.thisObject ?: return
        val item = adapterItem(adapter, position) ?: return
        val msgId = readLong(item, "getMsgId", "getMsgID", "field_msgId", "msgId", "msgID")
        if (msgId <= 0) return
        WeChatApis.messageStore()?.getMessageById(msgId)?.let { messages[root] = it }
    }

    private fun findMessage(view: View): WeChatMessage? {
        var current: View? = view
        while (current != null) {
            messages[current]?.let { return it }
            current = current.parent as? View
        }
        return null
    }

    private fun buildMentionText(text: CharSequence, message: WeChatMessage, spec: String): CharSequence? {
        val ids = message.getEffectiveAtUserList()
        if (ids.isEmpty()) return null
        val source = text.toString()
        val body = message.bodyContent()
        val names = body.split("@").drop(1).mapNotNull { part ->
            part.takeWhile { !it.isWhitespace() && it != ':' && it != '：' }.takeIf { it.isNotBlank() }
        }
        if (names.isEmpty()) return null
        val colors = MentionHighlightSettings.parseColors(spec)
        val out = SpannableStringBuilder(text)
        var search = 0
        var changed = false
        names.forEachIndexed { index, name ->
            val start = source.indexOf("@$name", search)
            if (start < 0) return@forEachIndexed
            val end = start + name.length + 1
            val user = ids.getOrNull(index).orEmpty()
            if (user.isBlank() || user == "notify@all" || user == "announcement@all") return@forEachIndexed
            out.setSpan(MentionColorSpan(colors.first, colors.second), start, end, 33)
            out.setSpan(ProfileSpan { view -> openProfile(view, message.talker, user) }, start, end, 33)
            search = end
            changed = true
        }
        return if (changed) out else null
    }

    private fun openProfile(view: View, room: String, user: String) {
        val intent = Intent().setComponent(ComponentName(view.context.packageName, "com.tencent.mm.plugin.profile.ui.ContactInfoUI"))
            .putExtra("Contact_User", user).putExtra("Contact_ChatRoomId", room)
            .putExtra("room_name", room).putExtra("Contact_Scene", 14)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (view.context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { view.context.startActivity(intent) }.onFailure { HLog.e(TAG + " 资料页打开失败", it) }
    }

    private fun locateBindMethod(): Method? = runCatching {
        context.dexKitBridge().findMethod(FindMethod().apply {
            matcher(MethodMatcher().apply { usingStrings(listOf("MicroMsg.ChattingDataAdapterV3", "_onBindViewHolder[", "msgInfo")) })
        }).mapNotNull { runCatching { it.getMethodInstance(context.hostClassLoader()) }.getOrNull() }
            .firstOrNull { it.parameterTypes.size == 2 && it.parameterTypes[1] == Int::class.javaPrimitiveType }
    }.getOrElse { HLog.e(TAG + " 绑定方法定位失败", it); null }

    private fun findRoot(value: Any): View? = when (value) {
        is View -> value
        else -> KavaReflector.readField(value, "itemView") as? View
    }
    private fun adapterItem(adapter: Any, position: Int): Any? {
        var c: Class<*>? = adapter.javaClass
        while (c != null && c != Any::class.java) {
            KavaReflector.declaredMethods(c).firstOrNull { it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType && it.name in setOf("J0", "getItem", "get") }?.let {
                return KavaReflector.invoke(it, adapter, position)
            }
            c = c.superclass
        }
        return null
    }
    private fun readLong(source: Any, vararg names: String): Long = names.firstNotNullOfOrNull { name ->
        val v = if (name.startsWith("get")) KavaReflector.invoke(KavaReflector.findMethod(source.javaClass, name), source) else KavaReflector.readField(source, name)
        (v as? Number)?.toLong()?.takeIf { it > 0 }
    } ?: 0L

    private class MentionColorSpan(private val start: Int, private val end: Int) : CharacterStyle(), UpdateAppearance {
        override fun updateDrawState(paint: TextPaint) {
            paint.shader = if (start == end) null else LinearGradient(0f, 0f, paint.measureText("艾特对象"), 0f, start, end, Shader.TileMode.CLAMP)
            paint.color = start
            paint.isFakeBoldText = true
        }
    }
    private class ProfileSpan(private val action: (View) -> Unit) : ClickableSpan() {
        override fun onClick(widget: View) = action(widget)
        override fun updateDrawState(ds: TextPaint) { ds.isUnderlineText = false }
    }
    companion object { private const val TAG = "[Hchat:MentionHighlight]" }
}