package android.app

open class Activity {
    var isFinishing: Boolean = false
    val uiRunnables = mutableListOf<Runnable>()

    fun runOnUiThread(action: Runnable) {
        uiRunnables.add(action)
        action.run()
    }
}
