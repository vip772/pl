package org.luckypray.dexkit.query.matchers

class MethodMatcher {
    val strings = mutableListOf<String>()
    fun usingEqStrings(vararg values: String): MethodMatcher {
        strings.addAll(values)
        return this
    }
}