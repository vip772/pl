package android.os

/** Synthetic main looper: posts run inline so the exit target call is observable. */
class Looper private constructor() {
    companion object {
        private val MAIN = Looper()

        @JvmStatic
        fun getMainLooper(): Looper = MAIN
    }
}

class Handler(looper: Looper?) {
    fun post(action: Runnable): Boolean {
        action.run()
        return true
    }

    fun removeCallbacksAndMessages(token: Any?) {
    }
}
