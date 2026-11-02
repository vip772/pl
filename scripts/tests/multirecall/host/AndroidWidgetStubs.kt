package android.widget

import android.app.Activity

class Toast {
    val text: String

    private constructor(text: String) {
        this.text = text
    }

    fun show() {
        shown.add(text)
    }

    companion object {
        const val LENGTH_SHORT = 0
        val shown = mutableListOf<String>()

        @JvmStatic
        fun makeText(context: Activity?, text: CharSequence, duration: Int): Toast = Toast(text.toString())

        fun reset() {
            shown.clear()
        }
    }
}
