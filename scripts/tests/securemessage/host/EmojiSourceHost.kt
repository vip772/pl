package ft1

/** Synthetic emoji MsgSource owner: String a(e9) is the verified 8.0.77 shape. */
class d1 {
    fun a(msg: com.tencent.mm.storage.e9): String = msg.field_msgSource.orEmpty()
}
