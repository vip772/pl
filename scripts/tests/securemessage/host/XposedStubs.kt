package de.robv.android.xposed

import java.lang.reflect.Member

open class XC_MethodHook {
    open fun beforeHookedMethod(param: MethodHookParam) {}
    open fun afterHookedMethod(param: MethodHookParam) {}

    class MethodHookParam(val args: Array<Any?>, var thisObject: Any?) {
        var result: Any? = null
        private var throwable: Throwable? = null

        fun hasThrowable(): Boolean = throwable != null
        fun setThrowable(error: Throwable?) { throwable = error }
        fun getThrowable(): Throwable? = throwable
    }

    /** Keeps the hooked member so the regression can address one handle by owner and name. */
    class Unhook(
        val callback: XC_MethodHook,
        val member: Member? = null,
        private val onRelease: (Unhook) -> Unit = {}
    ) {
        var unhooked = false

        fun unhook() {
            if (unhooked) return
            unhooked = true
            onRelease(this)
        }
    }
}
