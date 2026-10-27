package t.mma.hooks.items.mentionextension

import t.mma.hooks.api.model.WeChatMessage

/** Only a fresh stored insertion is an ordering anchor, never an add_msg timestamp. */
internal object MentionNoticeOrder {
    fun after(createTime: Long): Long? {
        if (createTime <= 0) return null
        val millis = if (createTime < 100_000_000_000L) createTime * 1000 else createTime
        return if (millis < Long.MAX_VALUE) millis + 1 else null
    }

    fun accepts(insert: Boolean, msg: WeChatMessage, startedAt: Long, now: Long): Boolean {
        if (!insert || msg.msgId <= 0 || msg.msgSvrId <= 0 ||
            !msg.isIncoming() || !msg.isGroupChat() || startedAt <= 0) return false
        val time = after(msg.createTime)?.minus(1) ?: return false
        // Same freshness limits as the common observer's database fallback.
        return time >= startedAt - 10_000 && time >= now - 300_000 && time <= now + 300_000
    }
}
