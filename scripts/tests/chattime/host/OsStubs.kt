package android.os

/** Synthetic main looper: posts run inline while the queue flag is open. */
class Looper private constructor() {
    companion object {
        private val MAIN = Looper()
        @JvmStatic
        fun getMainLooper(): Looper = MAIN
        @JvmStatic
        fun myLooper(): Looper? = MAIN
    }
}

class Handler(looper: Looper?) {
    var enabled: Boolean = true
    val queued = mutableListOf<Runnable>()

    fun post(action: Runnable): Boolean {
        queued.add(action)
        if (!enabled) return false
        action.run()
        return true
    }

    fun removeCallbacksAndMessages(token: Any?) {
    }
}
