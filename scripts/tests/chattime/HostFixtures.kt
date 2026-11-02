package com.tencent.mm.storage

/** Synthetic native message: only the two accessors the resolver validates. */
class FakeMsg(private val createTime: Long, private val msgId: Long) {
    var createTimeReads = 0
        private set

    fun getCreateTime(): Long {
        createTimeReads++
        return createTime
    }

    fun getMsgId(): Long = msgId
}
