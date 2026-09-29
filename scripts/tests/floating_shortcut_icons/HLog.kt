package t.mma.utils
object HLog { fun e(message: String, error: Throwable) { throw AssertionError(message, error) } }
