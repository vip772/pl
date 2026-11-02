package lq1

/** Synthetic emoji MsgSource owner for the 8.0.76 profile: String a(e9). */
class d1 {
    fun a(msg: com.tencent.mm.storage.e9): String = msg.field_msgSource.orEmpty()
}
