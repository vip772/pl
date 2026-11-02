package t.mma.dexkit

import java.lang.reflect.Method
import org.luckypray.dexkit.query.FindMethod

/** Synthetic DexKit bridge: results are queued by the regression, queries are counted. */
class DexKitBridge {
    fun findMethod(request: FindMethod): List<DexMethodData> {
        DexKitState.queries++
        if (DexKitState.failQueries) error("synthetic dexkit failure")
        val wanted = request.matchers.filterIsInstance<
            org.luckypray.dexkit.query.matchers.MethodMatcher
            >().flatMap { it.eqStrings + it.partialStrings }
        DexKitState.lastQuery = wanted
        return DexKitState.results.toList()
    }
}

class DexMethodData(private val method: Method?) {
    fun getMethodInstance(loader: ClassLoader): Method = method ?: error("not a method")
}

object DexKitState {
    val bridge = DexKitBridge()
    var results = mutableListOf<DexMethodData>()
    var queries = 0
    var failQueries = false
    var lastQuery = emptyList<String>()

    fun reset() {
        results = mutableListOf()
        queries = 0
        failQueries = false
        lastQuery = emptyList()
    }
}

class DexKitBridgeManager {
    fun <T> withCurrentBridge(action: (DexKitBridge) -> T): T = action(DexKitState.bridge)
}
