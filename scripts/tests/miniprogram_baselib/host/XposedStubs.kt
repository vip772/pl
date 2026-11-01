package de.robv.android.xposed

/** Synthetic Xposed surface: hook handles are explicit objects tests can inspect. */
open class XC_MethodHook {
    open fun beforeHookedMethod(param: MethodHookParam) {}
    open fun afterHookedMethod(param: MethodHookParam) {}

    class MethodHookParam(val args: Array<Any?>, var thisObject: Any?) {
        var result: Any? = null
    }

    class Unhook(
        val callback: XC_MethodHook,
        private val onRelease: (Unhook) -> Unit
    ) {
        var unhooked = false
        fun unhook() {
            if (unhooked) return
            unhooked = true
            onRelease(this)
        }
    }
}