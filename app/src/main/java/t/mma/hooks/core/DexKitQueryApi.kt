package t.mma.hooks.core

import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import t.mma.dexkit.DexKitBridgeManager
import t.mma.utils.KavaReflector
import java.lang.reflect.Member

/**
 * 新一代 DexKit 查询 API：native-backed 的 Data 对象只在共享查询区间内存在，
 * 离开本方法后只返回 Class、Member 或稳定字符串等脱离 native 的值。
 *
 * 查询结果保持脚本旧便捷接口的语义：类查询同时收集类命中和方法所属类，
 * 成员查询同时收集直接方法命中和类命中展开的声明成员，联合查询无结果时
 * 仍按单个字符串回退。
 */
object DexKitQueryApi {
    fun findClassesByStrings(
        manager: DexKitBridgeManager,
        classLoader: ClassLoader,
        vararg anchors: String
    ): List<Class<*>> {
        val strings = anchors.filter { it.isNotBlank() }
        if (strings.isEmpty()) return emptyList()
        return runCatching {
            manager.withCurrentBridge { bridge ->
                val out = linkedSetOf<Class<*>>()
                collectClassMatches(bridge, classLoader, strings, out)
                if (out.isEmpty() && strings.size > 1) {
                    strings.forEach { single ->
                        collectClassMatches(bridge, classLoader, listOf(single), out)
                    }
                }
                ArrayList(out)
            }
        }.getOrDefault(emptyList())
    }

    fun findMembersByStrings(
        manager: DexKitBridgeManager,
        classLoader: ClassLoader,
        vararg anchors: String
    ): List<Member> {
        val strings = anchors.filter { it.isNotBlank() }
        if (strings.isEmpty()) return emptyList()
        return runCatching {
            manager.withCurrentBridge { bridge ->
                val out = linkedSetOf<Member>()
                collectMemberMatches(bridge, classLoader, strings, out)
                if (out.isEmpty() && strings.size > 1) {
                    strings.forEach { single ->
                        collectMemberMatches(bridge, classLoader, listOf(single), out)
                    }
                }
                ArrayList(out)
            }
        }.getOrDefault(emptyList())
    }

    fun findClassNamesByStrings(
        manager: DexKitBridgeManager,
        vararg anchors: String
    ): List<String> {
        val strings = anchors.filter { it.isNotBlank() }
        if (strings.isEmpty()) return emptyList()
        return runCatching {
            manager.withCurrentBridge { bridge ->
                val out = linkedSetOf<String>()
                collectClassNames(bridge, strings, out)
                if (out.isEmpty() && strings.size > 1) {
                    strings.forEach { single -> collectClassNames(bridge, listOf(single), out) }
                }
                ArrayList(out)
            }
        }.getOrDefault(emptyList())
    }

    private fun collectClassMatches(
        bridge: org.luckypray.dexkit.DexKitBridge,
        classLoader: ClassLoader,
        strings: List<String>,
        out: MutableSet<Class<*>>
    ) {
        if (strings.isEmpty()) return
        val classMatcher = ClassMatcher().apply { usingStrings(strings) }
        val classQuery = FindClass().apply { matcher(classMatcher) }
        bridge.findClass(classQuery)
            .mapNotNull { KavaReflector.loadClass(it.name, classLoader) }
            .forEach(out::add)

        val methodMatcher = MethodMatcher().apply { usingStrings(strings) }
        val methodQuery = FindMethod().apply { matcher(methodMatcher) }
        bridge.findMethod(methodQuery)
            .mapNotNull { runCatching { it.getMethodInstance(classLoader).declaringClass }.getOrNull() }
            .forEach(out::add)
    }

    private fun collectMemberMatches(
        bridge: org.luckypray.dexkit.DexKitBridge,
        classLoader: ClassLoader,
        strings: List<String>,
        out: MutableSet<Member>
    ) {
        if (strings.isEmpty()) return
        val methodMatcher = MethodMatcher().apply { usingStrings(strings) }
        val methodQuery = FindMethod().apply { matcher(methodMatcher) }
        bridge.findMethod(methodQuery)
            .mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
            .forEach(out::add)

        val classMatcher = ClassMatcher().apply { usingStrings(strings) }
        val classQuery = FindClass().apply { matcher(classMatcher) }
        bridge.findClass(classQuery)
            .mapNotNull { KavaReflector.loadClass(it.name, classLoader) }
            .forEach { clazz ->
                clazz.declaredConstructors.forEach(out::add)
                clazz.declaredMethods.forEach(out::add)
            }
    }

    private fun collectClassNames(
        bridge: org.luckypray.dexkit.DexKitBridge,
        strings: List<String>,
        out: MutableSet<String>
    ) {
        if (strings.isEmpty()) return
        val classMatcher = ClassMatcher().apply { usingStrings(strings) }
        val classQuery = FindClass().apply { matcher(classMatcher) }
        bridge.findClass(classQuery).map { it.name }.forEach(out::add)
        val methodMatcher = MethodMatcher().apply { usingStrings(strings) }
        val methodQuery = FindMethod().apply { matcher(methodMatcher) }
        bridge.findMethod(methodQuery).map { it.className }.forEach(out::add)
    }
}