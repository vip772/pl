package android.view

/** Synthetic view: post() is switchable so dropped deliveries are testable. */
open class View {
    var tag: Any? = null
    var visibility: Int = VISIBLE
    var parent: Any? = "parent"
    var postEnabled: Boolean = true
    val posted = mutableListOf<Runnable>()

    open fun post(action: Runnable): Boolean {
        posted.add(action)
        if (!postEnabled) return false
        action.run()
        return true
    }

    companion object {
        const val VISIBLE = 0
        const val GONE = 8
    }
}
