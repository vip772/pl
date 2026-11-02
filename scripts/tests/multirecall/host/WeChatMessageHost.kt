package com.tencent.mm.storage.fake

/** Synthetic native message: msgId / isSend through the fields the runtime reads. */
class FakeMessage(
    @JvmField val field_msgId: Long,
    @JvmField val field_isSend: Boolean
) {
    fun getMsgId(): Long = field_msgId

    fun getIsSend(): Boolean = field_isSend

    override fun toString(): String = "FakeMessage($field_msgId,$field_isSend)"
}
