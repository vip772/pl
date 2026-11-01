package org.luckypray.dexkit

import java.lang.reflect.Constructor
import java.lang.reflect.Method

/** Synthetic DexKit result wrapper mirroring the members the production feature consumes. */
class DexMethodData(
    private val constructor: Constructor<*>?,
    private val method: Method?
) {
    val isConstructor: Boolean get() = constructor != null
    fun getConstructorInstance(loader: ClassLoader): Constructor<*> =
        constructor ?: error("not a constructor")
    fun getMethodInstance(loader: ClassLoader): Method = method ?: error("not a method")
}

class DexKitBridge {
    fun findMethod(request: org.luckypray.dexkit.query.FindMethod): List<DexMethodData> {
        BridgeState.queries++
        return BridgeState.results.toList()
    }
}

/** Synthetic DexKit result feed shared by every synthetic process. */
object BridgeState {
    val bridge = DexKitBridge()
    var results = mutableListOf<DexMethodData>()
    var queries = 0

    fun reset() {
        results = mutableListOf()
        queries = 0
    }
}