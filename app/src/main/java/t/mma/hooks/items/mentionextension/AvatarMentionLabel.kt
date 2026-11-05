package t.mma.hooks.items.mentionextension

import t.mma.hooks.api.model.WeChatMessage

/** Uses real mention metadata; quoted/visible @ text alone never creates a badge. */
internal object AvatarMentionLabel {
    fun text(message: WeChatMessage): String {
        if (!message.isGroupChat()) return ""
        return when (message.getAtMentionType()) {
            WeChatMessage.AtMentionType.AT_ALL -> "@所有人"
            WeChatMessage.AtMentionType.ANNOUNCEMENT_ALL -> ""
            else -> {
                val users = message.getAtUserList().distinct()
                when {
                    users.isEmpty() -> ""
                    users.size == 1 && message.isIncoming() &&
                        message.selfWxId.isNotBlank() && users[0].equals(message.selfWxId, true) -> "@我"
                    else -> "@${users.size}人"
                }
            }
        }
    }
}
