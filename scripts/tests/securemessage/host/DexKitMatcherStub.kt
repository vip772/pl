package org.luckypray.dexkit.query.matchers

import org.luckypray.dexkit.query.enums.StringMatchType

class MethodMatcher {
    var eqStrings: List<String> = emptyList()
        private set
    var partialStrings: List<String> = emptyList()
        private set

    fun usingEqStrings(vararg values: String): MethodMatcher {
        eqStrings = values.toList()
        return this
    }

    fun usingStrings(vararg values: String): MethodMatcher {
        partialStrings = values.toList()
        return this
    }

    fun declaredClass(className: String, matchType: StringMatchType, caseInsensitive: Boolean): MethodMatcher = this

    fun returnType(typeName: String): MethodMatcher = this

    fun paramCount(count: Int): MethodMatcher = this

    fun paramTypes(vararg typeNames: String): MethodMatcher = this
}
