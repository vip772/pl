package org.luckypray.dexkit.query.matchers

import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Method

class MethodMatcher {
    val names = mutableListOf<String>()
    val invokedMatchers = mutableListOf<MethodMatcher>()
    val calledMatchers = mutableListOf<MethodMatcher>()
    var returnTypeName: String? = null
    var paramTypeNames: List<String> = emptyList()
        private set
    var eqStrings: List<String> = emptyList()
        private set
    var partialStrings: List<String> = emptyList()
        private set

    constructor()

    constructor(method: Method?) {
        if (method != null) names.add(method.name)
    }

    fun name(value: String): MethodMatcher {
        names.add(value)
        return this
    }

    fun returnType(typeName: String): MethodMatcher {
        returnTypeName = typeName
        return this
    }

    fun paramCount(count: Int): MethodMatcher = this

    fun paramTypes(vararg typeNames: String): MethodMatcher {
        paramTypeNames = typeNames.toList()
        return this
    }

    fun declaredClass(className: String): MethodMatcher = this

    fun declaredClass(className: String, matchType: StringMatchType): MethodMatcher = this

    fun declaredClass(
        className: String,
        matchType: StringMatchType,
        caseInsensitive: Boolean
    ): MethodMatcher = this

    fun usingEqStrings(vararg values: String): MethodMatcher {
        eqStrings = values.toList()
        return this
    }

    fun usingStrings(vararg values: String): MethodMatcher {
        partialStrings = values.toList()
        return this
    }

    fun addInvoke(matcher: MethodMatcher): MethodMatcher {
        invokedMatchers.add(matcher)
        return this
    }

    fun addCall(matcher: MethodMatcher): MethodMatcher {
        calledMatchers.add(matcher)
        return this
    }
}
