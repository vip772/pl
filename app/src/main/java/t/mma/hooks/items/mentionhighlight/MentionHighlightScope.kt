package t.mma.hooks.items.mentionhighlight

import t.mma.hooks.api.model.WeChatMessage

/** Limits quote highlighting to an unambiguous, complete current reply title. */
internal object MentionHighlightScope {
    fun supports(message: WeChatMessage): Boolean =
        message.isText() || replyTitle(message) != null

    fun accepts(message: WeChatMessage, text: CharSequence): Boolean {
        if (message.isText()) return true
        val title = replyTitle(message) ?: return false
        if (text.toString() != title) return false
        // A quoted preview can contain the same words as the new reply. Without
        // a verified view-role discriminator, refuse this ambiguous case.
        val quoted = message.getQuoteMsg()?.content.orEmpty()
        return !quoted.contains(title)
    }

    private fun replyTitle(message: WeChatMessage): String? {
        if (!message.isApp()) return null
        val own = WeChatMessage.currentMessageXml(message.bodyContent())
        if (WeChatMessage.xmlTag(own, "type").toIntOrNull() != 57) return null
        // Reuse the model's CDATA/entity decoder, after stripping reference data.
        val isolated = WeChatMessage(0, 0, message.type, 0, 0, 0,
            message.talker, own, "", "", "", 0)
        return isolated.getQuoteMsg()?.title?.takeIf { it.isNotBlank() }
    }
}
