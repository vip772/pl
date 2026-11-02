package k52

import com.tencent.mm.modelbase.u0
import com.tencent.mm.network.s

/** Synthetic sendemoji request graph: y.d -> request.a -> wrapper.a -> list.e -> item.p. */
class EmojiRequest {
    @JvmField var a: Any? = null
}

class EmojiWrapper {
    @JvmField var a: Any? = null
}

class EmojiList {
    @JvmField var e: MutableList<Any?> = mutableListOf()
}

class EmojiItem {
    @JvmField var p: String? = null
}

/** Synthetic sendemoji scene: int doScene(s, u0) is the verified 8.0.77 shape. */
class y {
    @JvmField var d: Any? = null

    fun doScene(network: s, dispatcher: u0): Int = 0
}
