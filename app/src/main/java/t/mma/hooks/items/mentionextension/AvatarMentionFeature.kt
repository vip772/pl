package t.mma.hooks.items.mentionextension

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.model.WeChatMessage
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.hooks.items.hideavatar.ChatAvatarBinding
import t.mma.hooks.items.antirecall.DynamicRecallGradient
import t.mma.utils.KavaReflector
import java.util.WeakHashMap
import java.lang.reflect.Field
import java.lang.reflect.Method

internal class AvatarMentionFeature : BaseFeature() {
    override fun featureId() = "avatar_mention"
    override fun name() = "头像提醒"

    override fun onFeatureInstall(context: FeatureContext) {
        if (context.loadPackageParam().processName != context.loadPackageParam().packageName) return
        val prefs = AvatarMentionSettings.preferences(context.hostContext())
        var schedule: (() -> Unit)? = null
        val owner = PreferenceRuntimeOwner(prefs, setOf(AvatarMentionSettings.ENABLE),
            enabled = { prefs.getBoolean(AvatarMentionSettings.ENABLE, false) },
            create = { resources ->
                val runtime = AvatarMentionRuntime(prefs) { message, error -> logError(message, error) }
                resources.register { runtime.close() }
                val ticket = DexInstallScheduler.installation(featureId(), name())
                val install = {
                    ticket.schedule { runtime.install(context) }
                }
                schedule = install
                resources.register {
                    if (schedule === install) schedule = null
                    ticket.close()
                }
                install()
            }, onError = { logError("头像提醒启停失败", it) })
        trackResource { owner.close() }
        owner.start()
        subscribe(Events.DexReady::class.java) { schedule?.invoke() }
    }
}

/** Main-thread row state only; no worker, database lookup, message cache, or scroll observer. */
private class AvatarMentionRuntime(
    private val prefs: SharedPreferences,
    private val logger: (String, Throwable?) -> Unit
) {
    private data class Row(val decoration: AvatarMentionDecoration, var label: String)
    private val rows = WeakHashMap<ViewGroup, Row>()
    // Native schema is resolved once per class, never by scanning members per row.
    private data class Reader(val method: Method?, val field: Field?) {
        fun read(receiver: Any): Any? = method?.let { KavaReflector.invoke(it, receiver) }
            ?: field?.let { KavaReflector.readField(it, receiver) }
    }
    private val readers = HashMap<Class<*>, Map<String, Reader>>()
    private fun reader(native: Any, getter: String): Any? {
        val schema = readers.getOrPut(native.javaClass) {
            listOf("getMsgId" to "field_msgId", "getType" to "field_type", "getIsSend" to "field_isSend",
                "getTalker" to "field_talker", "getContent" to "field_content", "getReserved" to "field_reserved",
                "getMsgSource" to "field_msgSource", "getG" to "G").associate { (name, fieldName) ->
                name to Reader(KavaReflector.findMethodRecursive(native.javaClass, name),
                    KavaReflector.findFieldRecursive(native.javaClass, fieldName))
            }
        }
        return schema[getter]?.read(native)
    }
    private val main = Handler(Looper.getMainLooper())
    @Volatile
    private var closed = false
    private var position = AvatarMentionSettings.ABOVE
    private var spec = ""
    private var colors = intArrayOf()
    private val refresh = Runnable {
        if (!closed) {
            readStyle()
            rows.values.toList().forEach { it.decoration.update(it.label, position, spec, colors) }
        }
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (!closed && (key == null || key == AvatarMentionSettings.COLOR || key == AvatarMentionSettings.POSITION)) {
            main.removeCallbacks(refresh)
            main.post(refresh)
        }
    }
    init {
        readStyle()
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun readStyle() {
        position = AvatarMentionSettings.position(prefs.getString(AvatarMentionSettings.POSITION, AvatarMentionSettings.ABOVE))
        val next = prefs.getString(AvatarMentionSettings.COLOR, AvatarMentionSettings.DEFAULT_COLOR).orEmpty()
        if (spec != next || colors.isEmpty()) {
            colors = DynamicRecallGradient.parts(next).map(Color::parseColor).toIntArray()
            spec = next
        }
    }

    @Synchronized
    fun install(context: FeatureContext): Boolean =
        if (closed) true else ChatAvatarBinding.subscribe(context, this, ::bind)

    fun bind(binding: ChatAvatarBinding.Binding) {
        if (closed || Looper.myLooper() != Looper.getMainLooper()) return
        val row = binding.row ?: return
        try {
            // Always clear stale row state, including private chats and non-mention messages.
            val text = AvatarMentionLabel.text(message(binding.message))
            val old = rows[row]
            if (text.isEmpty()) {
                rows.remove(row)?.decoration?.close()
                return
            }
            val state = if (old != null && old.decoration.matches(binding.avatar)) old else {
                old?.decoration?.close()
                Row(AvatarMentionDecoration(row, binding.avatar), text).also { rows[row] = it }
            }
            state.label = text
            state.decoration.update(text, position, spec, colors)
        } catch (error: Throwable) {
            rows.remove(row)?.decoration?.close()
            logger("头像提醒消息绑定失败", error)
        }
    }

    private fun message(native: Any): WeChatMessage {
        fun string(getter: String) = reader(native, getter) as? String ?: ""
        fun number(getter: String) = (reader(native, getter) as? Number)?.toLong() ?: 0L
        val talker = string("getTalker")
        // No source/content XML work for private-chat rows.
        if (!WeChatMessage.isGroupTalker(talker)) return WeChatMessage(0, 0, 0, 0, 0, 0, talker, "", "", "", "", 0)
        return WeChatMessage(number("getMsgId"), 0L,
            number("getType").toInt(), 0, number("getIsSend").toInt(), 0L,
            string("getTalker"), string("getContent"), "",
            string("getReserved"), "", 0,
            string("getMsgSource").ifBlank { string("getG") },
            WeChatApis.account()?.selfWxId().orEmpty())
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        ChatAvatarBinding.unsubscribe(this)
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        main.removeCallbacks(refresh)
        val cleanup = Runnable {
            rows.values.toList().forEach { state ->
                try { state.decoration.close() } catch (error: Throwable) { logger("头像提醒布局释放失败", error) }
            }
            rows.clear()
            readers.clear()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) cleanup.run() else main.post(cleanup)
    }
}
