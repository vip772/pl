package t.mma.hooks.items.mentionhighlight

import t.mma.hooks.api.model.WeChatMessage

internal object MentionHighlightScope {
    class Prepared internal constructor(private val plain: Boolean, private val title: String?) {
        fun accepts(text: CharSequence): Boolean = plain || (title != null && text.toString() == title)
    }

    // null means absent/unreadable subtype: keep database enrichment in that case.
    fun knownAppSubtype(content: String): Int? =
        WeChatMessage.xmlTag(WeChatMessage.currentMessageXml(content), "type")
            .toIntOrNull()?.takeIf { it > 0 }

    fun supports(message: WeChatMessage): Boolean {
        if (message.isText()) return true
        if (!message.isApp()) return false
        val own = WeChatMessage.currentMessageXml(message.bodyContent())
        return WeChatMessage.xmlTag(own, "type").toIntOrNull() == 57 &&
            WeChatMessage.xmlTag(own, "title").isNotBlank()
    }

    fun prepare(message: WeChatMessage): Prepared {
        if (message.isText()) return Prepared(true, null)
        if (!message.isApp()) return Prepared(false, null)
        val own = WeChatMessage.currentMessageXml(message.bodyContent())
        if (WeChatMessage.xmlTag(own, "type").toIntOrNull() != 57) return Prepared(false, null)
        val isolated = WeChatMessage(0, 0, message.type, 0, 0, 0,
            message.talker, own, "", "", "", 0)
        val title = isolated.getQuoteMsg()?.title?.takeIf { it.isNotBlank() }
            ?: return Prepared(false, null)
        val quoted = message.getQuoteMsg()?.content.orEmpty()
        return Prepared(false, title.takeUnless { quoted.contains(it) })
    }

    // Convenience for isolated callers; runtime uses a binding-local Prepared.
    fun accepts(message: WeChatMessage, text: CharSequence): Boolean = prepare(message).accepts(text)
}
