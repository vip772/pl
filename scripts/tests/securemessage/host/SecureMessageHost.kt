package com.tencent.mm.storage.fake

/**
 * Synthetic WeChat message mirroring the descriptor the runtimes resolve through plain
 * reflection: fields in the real package prefix, a settable msgSource and a send flag.
 * Not the WeChat class.
 */
class FakeMsg {
    @JvmField var field_msgId: Long = 0L
    @JvmField var field_type: Int = 1
    @JvmField var field_isSend: Int = 1
    @JvmField var field_content: String? = "hello"
    @JvmField var field_msgSource: String? = null

    fun getMsgSource(): String = field_msgSource.orEmpty()

    fun setMsgSource(value: String) {
        field_msgSource = value
    }
}

/** Synthetic local insert entry: Long insert(int, FakeMsg) is exactly the accepted shape. */
class FakeMsgStorage {
    fun insert(mode: Int, msg: FakeMsg): Long = 1L
}

/** Synthetic secure-message check: one non-primitive parameter, boolean result, static. */
class SecureCheckHost {
    companion object {
        @JvmStatic
        fun checkSecNode(msg: Any?): Boolean = true
    }
}
