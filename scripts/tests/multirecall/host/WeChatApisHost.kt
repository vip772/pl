package t.mma.hooks.api.core

import android.app.Activity

/** Synthetic message sender: records every native revoke call in order. */
class FakeMessageSender {
    val revoked = mutableListOf<Any>()
    val stored = LinkedHashMap<Long, Boolean>()
    var onRevoke: ((Any) -> Unit)? = null
    var failEvery = 0

    private var calls = 0

    fun revokeNative(message: Any?): Boolean {
        if (message == null) return false
        calls++
        val outgoing = stored[(message as? com.tencent.mm.storage.fake.FakeMessage)?.field_msgId ?: 0L]
        revoked.add(message)
        onRevoke?.invoke(message)
        if (failEvery > 0 && calls % failEvery == 0) return false
        return outgoing != false
    }

    fun store(): FakeMessageStore = FakeMessageStore(stored)

    fun reset() {
        revoked.clear()
        stored.clear()
        onRevoke = null
        failEvery = 0
        calls = 0
    }
}

class FakeMessageStore(private val stored: Map<Long, Boolean>) {
    fun getMessageById(msgId: Long): FakeStoredMessage? {
        val outgoing = stored[msgId] ?: return null
        return FakeStoredMessage(msgId, outgoing)
    }
}

class FakeStoredMessage(@JvmField val msgId: Long, @JvmField val outgoing: Boolean) {
    @JvmField val isSend: Int = if (outgoing) 1 else 0
}

class FakeActivityApi(private var activity: Activity?) {
    fun currentActivity(): Activity? = activity

    fun set(value: Activity?) {
        activity = value
    }
}

object WeChatApis {
    private val sender = FakeMessageSender()
    private var activityApi: FakeActivityApi? = null

    class MessageGroup {
        fun sender(): FakeMessageSender = sender

        fun store(): FakeMessageStore = sender.store()
    }

    @JvmStatic
    fun message(): MessageGroup = MessageGroup()

    @JvmStatic
    fun currentActivity(): FakeActivityApi? = activityApi

    fun senderHost(): FakeMessageSender = sender

    fun installActivity(api: FakeActivityApi) {
        activityApi = api
    }

    fun resetHost() {
        sender.reset()
        activityApi = null
    }
}
