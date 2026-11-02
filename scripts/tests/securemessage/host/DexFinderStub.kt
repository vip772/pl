package t.mma.dexkit

import java.lang.reflect.Method

/** Synthetic DexFinder: only the locally verified insert entry the send feature reads. */
class DexFinder {
    @JvmField
    var localMessageInsertMethod: Method? = null

    fun reset() {
        localMessageInsertMethod = null
    }
}
