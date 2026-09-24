package h.Hchat.hooks.items.mentionhighlight

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
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

    companion object {
        const val ID = "mention_highlight"
    }
}

private class MentionHighlightRuntime(private val context: FeatureContext) {
    private val rendered = Collections.synchronizedMap(WeakHashMap<View, Long>())
    private var installed = false

    @Synchronized
    fun install(): Boolean {
        if (installed) return true
        val method = locateBindMethod() ?: return false
        return runCatching {
            HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    apply(param)
                }
            })
            installed = true
            true
        }.getOrElse {
            HLog.e("[Hchat:MentionHighlight] 安装消息绑定 Hook 失败", it)
            false
        }
    }

    private fun apply(param: XC_MethodHook.MethodHookParam) {
        val prefs = MentionHighlightSettings.preferences(context.hostContext())
        val root = findRoot(param.args?.firstOrNull()) ?: return
        val position = param.args?.getOrNull(1) as? Int ?: return
        val message = findMessage(param.thisObject, position) ?: return
        if (!prefs.getBoolean(MentionHighlightSettings.KEY_ENABLE, false)) {
            rendered.remove(root)
            return
        }
        if (rendered[root] == message.msgId) return

        val body = message.bodyContent()
        val atUsers = message.getEffectiveAtUserList()
        if (atUsers.isEmpty() || !body.contains("@")) return
        val color = runCatching {
            Color.parseColor(prefs.getString(MentionHighlightSettings.KEY_COLOR, MentionHighlightSettings.DEFAULT_COLOR))
        }.getOrDefault(Color.RED)

        findTextViews(root).forEach { view ->
            val current = view.text?.toString().orEmpty()
            if (current.isBlank()) return@forEach
            val output = SpannableString(current)
            var searchFrom = 0
            atUsers.forEachIndexed { index, userId ->
                if (userId == "notify@all" || userId == "announcement@all") return@forEachIndexed
                val name = extractMentionName(body, index) ?: return@forEachIndexed
                val start = output.toString().indexOf("@$name", searchFrom)
                if (start < 0) return@forEachIndexed
                val end = start + name.length + 1
                output.setSpan(
                    MentionSpan(color) { widget -> openProfile(widget, message.talker, userId) },
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                searchFrom = end
            }
            view.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            view.highlightColor = Color.TRANSPARENT
            view.text = output
        }
        rendered[root] = message.msgId
    }

    private fun extractMentionName(body: String, index: Int): String? {
        val parts = body.split("@").drop(1)
        return parts.getOrNull(index)
            ?.takeWhile { !it.isWhitespace() && it != ':' && it != '：' }
            ?.takeIf { it.isNotBlank() }
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
            .onFailure { HLog.e("[Hchat:MentionHighlight] 打开艾特对象资料页失败", it) }
    }

    private fun locateBindMethod(): Method? = runCatching {
        context.dexKitBridge().findMethod(FindMethod().apply {
            matcher(MethodMatcher().apply {
                usingStrings(listOf("MicroMsg.ChattingDataAdapterV3", "msgInfo"))
            })
        }).mapNotNull { data ->
            runCatching { data.getMethodInstance(context.hostClassLoader()) }.getOrNull()
        }.firstOrNull {
            it.parameterTypes.size == 2 && it.parameterTypes[1] == Int::class.javaPrimitiveType
        }
    }.getOrElse {
        HLog.e("[Hchat:MentionHighlight] 定位聊天消息绑定方法失败", it)
        null
    }

    private fun findMessage(adapter: Any?, position: Int): WeChatMessage? {
        if (adapter == null || position < 0) return null
        val method = KavaReflector.declaredMethods(adapter.javaClass).firstOrNull {
            it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                it.name in setOf("J0", "getItem", "get")
        } ?: return null
        val raw = KavaReflector.invoke(method, adapter, position) ?: return null
        val content = readString(raw, "field_content", "content")
        val talker = readString(raw, "field_talker", "talker")
        val id = readLong(raw, "field_msgId", "msgId")
        val type = readInt(raw, "field_type", "type") ?: WeChatMessage.inferType(content)
        return WeChatMessage(id, 0L, type, 0, 0, 0L, talker, content, "", "", "", 0, "", "")
    }

    private fun readString(source: Any, vararg names: String): String {
        names.forEach { name ->
            (KavaReflector.readField(source, name) as? String)?.let { return it }
        }
        return ""
    }

    private fun readLong(source: Any, vararg names: String): Long = names.firstNotNullOfOrNull {
        (KavaReflector.readField(source, it) as? Number)?.toLong()
    } ?: 0L

    private fun readInt(source: Any, vararg names: String): Int? = names.firstNotNullOfOrNull {
        (KavaReflector.readField(source, it) as? Number)?.toInt()
    }

    private fun findRoot(value: Any?): View? = when (value) {
        is View -> value
        null -> null
        else -> KavaReflector.readField(value, "itemView") as? View
    }

    private fun findTextViews(root: View): List<TextView> {
        if (root is TextView) return listOf(root)
        if (root !is ViewGroup) return emptyList()
        return (0 until root.childCount).flatMap { findTextViews(root.getChildAt(it)) }
    }

    private class MentionSpan(
        private val color: Int,
        private val action: (View) -> Unit
    ) : ClickableSpan() {
        override fun onClick(widget: View) = action(widget)
        override fun updateDrawState(ds: TextPaint) {
            ds.color = color
            ds.isUnderlineText = false
            ds.isFakeBoldText = true
        }
    }
}