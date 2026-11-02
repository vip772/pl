package org.luckypray.dexkit.query.matchers

import java.lang.reflect.Method

class MethodMatcher {
    val partialStrings = mutableListOf<String>()
    var queryStrings: List<String> = emptyList()
        private set

    constructor()

    constructor(method: Method?)

    fun usingStrings(vararg values: String): MethodMatcher {
        partialStrings.addAll(values)
        queryStrings = partialStrings.toList()
        return this
    }
}
