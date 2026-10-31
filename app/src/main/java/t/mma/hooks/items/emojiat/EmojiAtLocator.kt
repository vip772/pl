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
        val storage = t.mma.hooks.api.message.OutgoingAtMessageHook.locate(context)
        val cache = DexMethodCache.prefs(context.hostContext(), "Hchat_emoji_at_dex_v2")
        val key = DexMethodCache.runtimeKey(context.hostContext(), loader)
        fun owners(anchors: List<String>): List<Class<*>> = anchors.flatMap { anchor ->
            val matcher = ClassMatcher().apply { usingStrings(listOf(anchor)) }
            context.dexKitManager().withCurrentBridge { bridge ->
                bridge.findClass(FindClass().apply { matcher(matcher) })
                    .mapNotNull { R.loadClass(it.name, loader) }
            }
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
        fun validScene(c: Constructor<*>): Boolean {
            val p = c.parameterTypes
            return p.size == 6 && p[0] == String::class.java && p[1] == String::class.java && p[3] == java.lang.Long.TYPE && p[4] == Integer.TYPE && p[5] == Integer.TYPE
        }
        val scene = DexMethodCache.loadConstructor(cache, key, loader, "scene")?.takeIf(::validScene)
            ?: owners(listOf("NetSceneUploadEmoji: md5 %s, size %s fromScene %d", "/cgi-bin/micromsg-bin/sendemoji"))
                .flatMap { R.declaredConstructors(it) }.filter(::validScene).distinct().singleOrNull()
                    ?.also { DexMethodCache.saveConstructor(cache, key, "scene", it) }
            ?: error("scene 定位结果不唯一或缺失")
        // 8.0.78 c72.y creates pc5.kk0 then appends to raw h27.e; no generic Signature exists.
        val itemType = R.loadClass("pc5.kk0", loader) ?: error("8.0.78表情请求项缺失")
        require(R.findFieldRecursive(itemType, "p")?.type == String::class.java) { "MsgSource 字段签名不符" }
        val serialize = R.declaredMethods(itemType).singleOrNull {
            it.name == "op" && !R.isStatic(it) && !R.isAbstract(it) && it.returnType == Integer.TYPE &&
                it.parameterTypes.size == 2 && it.parameterTypes[0] == Integer.TYPE && it.parameterTypes[1].isArray
        } ?: error("序列化入口不匹配")
        return Targets(send, storage, scene, serialize)
    }
}
