package h.Hchat.hooks.items.mentionhighlight

import android.graphics.LinearGradient
import android.graphics.Shader
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import h.Hchat.hooks.core.BaseFeature
import h.Hchat.hooks.core.DexInstallScheduler
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.HLog
import h.Hchat.utils.KavaReflector
import java.lang.reflect.Method
import java.lang.reflect.Modifier

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
    private var installed = false
    private val reentry = ThreadLocal<Boolean>()

    @Synchronized
    fun install(): Boolean {
        if (installed) return true
        val methods = linkedSetOf<Method>()
        val bufferType = TextView.BufferType::class.java
        val targets: List<Class<*>?> = listOf(
            TextView::class.java,
            KavaReflector.loadClass(
                "com.tencent.neattextview.textview.view.NeatTextView",
                context.hostClassLoader()
            ),
            KavaReflector.loadClass(
                "com.tencent.mm.ui.widget.MMNeat7extView",
                context.hostClassLoader()
            )
        )
        targets.forEach { clazz ->
            clazz ?: return@forEach

            val method = KavaReflector.findMethodRecursive(
                clazz,
                "setText",
                CharSequence::class.java,
                bufferType
            ) ?: return@forEach
            if (methods.add(method)) {
                HookRegistry.get().hook(method, textHook)
            }
        }
        installed = methods.isNotEmpty()
        HLog.i(TAG + " setText后置Hook=$installed methods=${methods.size}")
        return installed
    }

    private val textHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            if (reentry.get() == true) return
            val view = param.thisObject as? TextView ?: return
            val prefs = MentionHighlightSettings.preferences(context.hostContext())
            if (!prefs.getBoolean(MentionHighlightSettings.KEY_ENABLE, false)) return
            val text = view.text as? Spanned ?: return
            val mentionSpans = findMentionSpans(text)
            if (mentionSpans.isEmpty()) return
            val colors = MentionHighlightSettings.parseColors(
                prefs.getString(
                    MentionHighlightSettings.KEY_COLOR,
                    MentionHighlightSettings.DEFAULT_COLOR
                ) ?: MentionHighlightSettings.DEFAULT_COLOR
            )
            val builder = SpannableStringBuilder(text)
            var changed = false
            mentionSpans.forEach { span ->
                val start = text.getSpanStart(span)
                val end = text.getSpanEnd(span)
                if (start < 0 || end <= start || end > text.length) return@forEach
                if (hasMentionColor(builder, start, end)) return@forEach
                builder.setSpan(
                    MentionColorSpan(colors.first, colors.second),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                changed = true
            }
            if (!changed) return
            reentry.set(true)
            try {
                view.setText(builder, TextView.BufferType.SPANNABLE)
            } finally {
                reentry.remove()
            }
        }
    }

    private fun findMentionSpans(text: Spanned): List<Any> {
        val all = text.getSpans(0, text.length, ClickableSpan::class.java)
        return all.filter { span ->
            val type = span.javaClass
            if (type.name == "android.text.style.URLSpan") return@filter false
            val fields = KavaReflector.declaredFields(type)
            val strings = fields.count { it.type == String::class.java }
            val longs = fields.count { it.type == Long::class.javaPrimitiveType }
            strings >= 2 && longs >= 1 && fields.none { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        }
    }

    private fun hasMentionColor(text: SpannableStringBuilder, start: Int, end: Int): Boolean {
        return text.getSpans(start, end, MentionColorSpan::class.java).any {
            text.getSpanStart(it) == start && text.getSpanEnd(it) == end
        }
    }

    private class MentionColorSpan(
        private val startColor: Int,
        private val endColor: Int
    ) : CharacterStyle(), UpdateAppearance {
        override fun updateDrawState(paint: TextPaint) {
            val width = paint.measureText("艾特对象").coerceAtLeast(1f)
            paint.shader = LinearGradient(
                0f,
                0f,
                width,
                0f,
                startColor,
                endColor,
                Shader.TileMode.CLAMP
            )
            paint.isFakeBoldText = true
        }
    }

    companion object {
        private const val TAG = "[Hchat:MentionHighlight]"
    }
}
