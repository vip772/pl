package org.luckypray.dexkit.query

class FindMethod {
    val matchers = mutableListOf<Any>()
    fun matcher(matcher: org.luckypray.dexkit.query.matchers.MethodMatcher): FindMethod {
        matchers.add(matcher)
        return this
    }
}
