package t.mma.utils

object HLog {
    val logs = mutableListOf<String>()
    fun d(message: String) {}
    fun i(message: String) { logs.add(message) }
    fun w(message: String) { logs.add(message) }
    fun e(message: String, throwable: Throwable?) { logs.add(message) }
}

object KavaReflector {
    fun isStatic(method: java.lang.reflect.Method?): Boolean =
        method != null && java.lang.reflect.Modifier.isStatic(method.modifiers)
}