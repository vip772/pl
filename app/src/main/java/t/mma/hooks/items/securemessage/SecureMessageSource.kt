package t.mma.hooks.items.securemessage

/** Merge the policy while retaining UUIDs, attributes and unrelated MsgSource metadata. */
object SecureMessageSource {
    private val secureNode = Regex(
        "<sec_msg_node\\b[^>]*>.*?</sec_msg_node\\s*>|<sec_msg_node\\b[^>]*/>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val policyField = Regex(
        "<(sfn|show-h5|clip-len|share-tip-url|sec-ctrl-flag|fold-reduce|media-to-emoji|block-range|bubble-type|preview-type|url-click-type)\\b[^>]*(?:/>|>.*?</\\1\\s*>)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val enabledFlag = Regex("<sfn\\b[^>]*>\\s*1\\s*</sfn\\s*>", RegexOption.IGNORE_CASE)
    private val msgSourceOpen = Regex("<msgsource\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val msgSourceClose = Regex("</msgsource\\s*>", RegexOption.IGNORE_CASE)

    @JvmStatic
    fun containsMarker(value: String?): Boolean =
        secureNode.findAll(value.orEmpty()).any { enabledFlag.containsMatchIn(it.value) }

    @JvmStatic
    fun addMarker(value: String?): String {
        val current = value.orEmpty()
        val existing = secureNode.find(current)
        if (existing != null) {
            val raw = existing.value
            val opening = (raw.substringBefore('>') + ">").replace(Regex("/\\s*>$"), ">")
            val body = if (raw.trimEnd().endsWith("/>")) "" else
                raw.substringAfter('>').substringBeforeLast("</")
            val policy = SecureMessageSettings.SEC_XML
                .removePrefix("<sec_msg_node>").removeSuffix("</sec_msg_node>")
            return current.replaceRange(existing.range,
                opening + policyField.replace(body, "") + policy + "</sec_msg_node>")
        }
        val close = msgSourceClose.find(current)
        if (close != null) {
            return current.substring(0, close.range.first) + SecureMessageSettings.SEC_XML +
                current.substring(close.range.first)
        }
        val open = msgSourceOpen.find(current)
        if (open != null) {
            val insertAt = open.range.last + 1
            return current.substring(0, insertAt) + SecureMessageSettings.SEC_XML + current.substring(insertAt)
        }
        return "<msgsource>$current${SecureMessageSettings.SEC_XML}</msgsource>"
    }
}
