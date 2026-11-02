package com.tencent.mm.storage

/** Synthetic emoji message (type 47) resolved by the verified 8.0.77 descriptor. */
class e9 {
    @JvmField var field_type: Int = 47
    @JvmField var field_isSend: Int = 1
    @JvmField var field_msgSource: String? = null

    fun getMsgSource(): String = field_msgSource.orEmpty()

    fun setMsgSource(value: String) {
        field_msgSource = value
    }
}
