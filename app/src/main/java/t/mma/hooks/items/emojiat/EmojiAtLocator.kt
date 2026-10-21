package t.mma.hooks.items.emojiat

import t.mma.dexkit.DexMethodCache
import t.mma.hooks.core.FeatureContext
import t.mma.utils.KavaReflector as R
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.matchers.ClassMatcher
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/** Script anchors are evidence for candidates, never proof of protocol compatibility. */
internal class EmojiAtLocator(private val context: FeatureContext) {
    data class Targets(val send: Method, val storage: Method, val scene: Constructor<*>, val serialize: Method)
    fun locate(): Targets {
        val loader = context.hostClassLoader()
        val cache = DexMethodCache.prefs(context.hostContext(), "Hchat_emoji_at_dex")
        val key = DexMethodCache.runtimeKey(context.hostContext(), loader)
        fun owners(anchors: List<String>): List<Class<*>> = anchors.flatMap { anchor ->
            val matcher = ClassMatcher().apply { usingStrings(listOf(anchor)) }
            context.dexKitBridge().findClass(FindClass().apply { matcher(matcher) })
                .mapNotNull { R.loadClass(it.name, loader) }
        }.distinct()
        fun method(name: String, anchors: List<String>, valid: (Method) -> Boolean): Method {
            DexMethodCache.load(cache, key, loader, name)?.takeIf(valid)?.let { return it }
            val candidates = owners(anchors).flatMap { R.declaredMethods(it) }.filter(valid).distinct()
            return candidates.singleOrNull()?.also { DexMethodCache.save(cache, key, name, it) }
                ?: error("$name 定位结果不唯一或缺失: ${candidates.size}")
        }
        val send = method("send", listOf("insert emoji msg, extCommonInfo null? :%b", "NetSceneUploadEmoji: msgId = %s, md5 %s, len %s")) {
            val p = it.parameterTypes
            !R.isStatic(it) && !R.isAbstract(it) && it.returnType == Void.TYPE && p.size == 6 && p[0] == String::class.java && p[5] == Integer.TYPE
        }
        val storage = method("storage", listOf("MsgInfo processAddMsg insert db error")) {
            !R.isStatic(it) && !R.isAbstract(it) && it.returnType == java.lang.Long.TYPE && it.parameterTypes.size == 1 &&
                listOf("N0", "getType", "z0").all { n -> R.findMethodRecursive(it.parameterTypes[0], n) != null } &&
                R.findMethodRecursive(it.parameterTypes[0], "r3", String::class.java) != null &&
                R.findFieldRecursive(it.parameterTypes[0], "G")?.type == String::class.java
        }
        fun validScene(c: Constructor<*>): Boolean {
            val p = c.parameterTypes
            return p.size == 6 && p[0] == String::class.java && p[1] == String::class.java && p[3] == java.lang.Long.TYPE && p[4] == Integer.TYPE && p[5] == Integer.TYPE
        }
        val scene = DexMethodCache.loadConstructor(cache, key, loader, "scene")?.takeIf(::validScene)
            ?: owners(listOf("NetSceneUploadEmoji: md5 %s, size %s fromScene %d", "/cgi-bin/micromsg-bin/sendemoji"))
                .flatMap { R.declaredConstructors(it) }.filter(::validScene).distinct().singleOrNull()
                    ?.also { DexMethodCache.saveConstructor(cache, key, "scene", it) }
            ?: error("scene 定位结果不唯一或缺失")
        var body: Class<*> = scene.declaringClass
        for (name in listOf("d", "a", "a")) body = R.findFieldRecursive(body, name)?.type ?: error("request.$name 不存在")
        val items = R.findFieldRecursive(body, "e") ?: error("request.items 不存在")
        require(List::class.java.isAssignableFrom(items.type))
        val itemType = (items.genericType as? java.lang.reflect.ParameterizedType)?.actualTypeArguments?.singleOrNull() as? Class<*>
            ?: error("请求项类型元数据不可用，禁止猜测")
        require(R.findFieldRecursive(itemType, "p")?.type == String::class.java) { "MsgSource 字段签名不符" }
        val serialize = R.declaredMethods(itemType).singleOrNull {
            it.name == "op" && !R.isStatic(it) && !R.isAbstract(it) && it.returnType == Integer.TYPE &&
                it.parameterTypes.size == 2 && it.parameterTypes[0] == Integer.TYPE && it.parameterTypes[1].isArray
        } ?: error("序列化入口不匹配")
        return Targets(send, storage, scene, serialize)
    }
}
