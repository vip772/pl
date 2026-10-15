package t.mma.hooks.items.autocache

import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import t.mma.dexkit.DexMethodCache
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.model.WeChatMessage
import t.mma.hooks.core.FeatureContext
import t.mma.utils.KavaReflector
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

/** 微信原生媒体下载入口；描述符由源码锚点定位并按微信运行时缓存。 */
internal class NativeMediaCache(private val context: FeatureContext) {
    private val cache = DexMethodCache.prefs(context.hostContext(), "Hchat_native_media_cache_v1")
    private var imageConstructor: Constructor<*>? = null
    private var fileConstructor: Constructor<*>? = null
    private var initAttach: Method? = null
    private var downloadVideo: Method? = null

    private fun methods(vararg strings: String): List<Method> = context.dexKitBridge().findMethod(
        FindMethod().apply { matcher(MethodMatcher().apply { usingEqStrings(*strings) }) }
    ).filter { !it.isConstructor }.map { it.getMethodInstance(context.hostClassLoader()) }

    private fun constructor(tag: String, name: String, shape: (Array<Class<*>>) -> Boolean): Constructor<*>? {
        val runtime = DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader())
        DexMethodCache.loadConstructor(cache, runtime, context.hostClassLoader(), name)
            ?.takeIf { shape(it.parameterTypes) }?.let { return it }
        val owners = context.dexKitBridge().findMethod(
            FindMethod().apply { matcher(MethodMatcher().apply { usingEqStrings(tag) }) }
        ).filter { it.isConstructor }.map { it.getConstructorInstance(context.hostClassLoader()).declaringClass }.distinct()
        return owners.flatMap { KavaReflector.declaredConstructors(it) }
            .singleOrNull { shape(it.parameterTypes) }?.also { DexMethodCache.saveConstructor(cache, runtime, name, it) }
    }

    fun install(): Boolean {
        val long = java.lang.Long.TYPE
        val int = Integer.TYPE
        imageConstructor = constructor("MicroMsg.NetSceneGetMsgImg", "image") {
            it.size == 6 && it[0] == long && it[1] == long && it[2] == String::class.java &&
                it[3] == int && it[4].isInterface && it[5] == int
        }
        fileConstructor = constructor("MicroMsg.NetSceneDownloadAppAttach", "file") {
            it.size == 4 && it[0] == long && it[1] == String::class.java &&
                it[2] == String::class.java && it[3].isInterface
        }
        val runtime = DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader())
        initAttach = DexMethodCache.load(cache, runtime, context.hostClassLoader(), "attach_init")
            ?: methods("summerbig initDownloadAttach msgLocalId[%d], msgXml[%s], downloadPath[%s]")
                .singleOrNull { Modifier.isStatic(it.modifiers) && it.parameterTypes.toList() ==
                    listOf(long, String::class.java, String::class.java, String::class.java) }
                ?.also { DexMethodCache.save(cache, runtime, "attach_init", it) }
        downloadVideo = DexMethodCache.load(cache, runtime, context.hostClassLoader(), "video_offline")
            ?: run {
                val online = methods("start complete online video, but can not stream video now!").toSet()
                methods("MicroMsg.VideoLogic", "ERR:", " getinfo failed: ", " get status failed: ", " update failed: ")
                    .filter { it !in online && Modifier.isStatic(it.modifiers) && it.returnType == int &&
                        it.parameterTypes.toList() == listOf(String::class.java) }.singleOrNull()
                    ?.also { DexMethodCache.save(cache, runtime, "video_offline", it) }
            }
        return imageConstructor != null && fileConstructor != null && initAttach != null && downloadVideo != null
    }

    private fun callback(type: Class<*>): Any = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
        when (method.name) {
            "toString" -> "HchatNativeMediaCache"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        }
    }

    fun complete(message: WeChatMessage): Boolean {
        if (message.msgId <= 0 || message.msgSvrId <= 0) return false
        val db = WeChatApis.database() ?: return false
        if (message.isVideo()) {
            val info = WeChatApis.media()?.videos()?.resolveDownloadInfo(message.imagePath) ?: return false
            return info.totalLength > 0 && info.currentLength >= info.totalLength
        }
        if (message.isFile()) {
            val cursor = db.rawQuery("SELECT status FROM appattach WHERE msgInfoId=? AND msgInfoTalker=?",
                arrayOf(message.msgId.toString(), message.talker)) ?: return false
            return cursor.use { it.moveToFirst() && it.getLong(0) == 199L }
        }
        if (message.isImage()) {
            val cursor = db.rawQuery("SELECT id, reserved1, offset, totalLen FROM ImgInfo2 WHERE msgSvrId=?",
                arrayOf(message.msgSvrId.toString())) ?: return false
            val values = cursor.use {
                if (!it.moveToFirst()) return false
                longArrayOf(it.getLong(0), it.getLong(1), it.getLong(2), it.getLong(3))
            }
            if (values[1] <= 0) return values[3] > 0 && values[2] >= values[3]
            val hd = db.rawQuery("SELECT offset, totalLen FROM ImgInfo2 WHERE id=?",
                arrayOf(values[1].toString())) ?: return false
            return hd.use { it.moveToFirst() && it.getLong(1) > 0 && it.getLong(0) >= it.getLong(1) }
        }
        return false
    }

    fun submit(message: WeChatMessage): Boolean {
        if (message.msgId <= 0 || message.msgSvrId <= 0) return false
        val database = WeChatApis.database() ?: return false
        if (message.isVideo()) {
            if (message.imagePath.isBlank()) return false
            val info = WeChatApis.media()?.videos()?.resolveDownloadInfo(message.imagePath) ?: return false
            if (info.totalLength > 0 && info.currentLength >= info.totalLength) return true
            return (KavaReflector.invokeOrThrow(downloadVideo, null, message.imagePath) as? Int) == 0
        }
        if (!WeChatApis.network().isReady()) return false
        if (message.isImage()) {
            // 8.0.78 ImgInfoStorage 的持久化结构和接收查询均已确认：
            // 入站按 msgSvrId 取 ImgInfo2，reserved1 是高清父图 id。
            val cursor = database.rawQuery(
                "SELECT id, reserved1, offset, totalLen FROM ImgInfo2 WHERE msgSvrId=?",
                arrayOf(message.msgSvrId.toString())
            ) ?: return false
            val values = cursor.use {
                if (!it.moveToFirst()) return false
                longArrayOf(it.getLong(0), it.getLong(1), it.getLong(2), it.getLong(3))
            }
            if (values[0] <= 0) return false
            val original = values[1] > 0
            if (!original && values[3] > 0 && values[2] >= values[3]) return true
            val ctor = imageConstructor ?: return false
            val scene = KavaReflector.newInstance(ctor, values[0], message.msgId, message.talker,
                if (original) 1 else 0, callback(ctor.parameterTypes[4]), 0) ?: return false
            return WeChatApis.network().sendRequest(scene)
        }
        if (!message.isFile()) return false
        val file = message.getFileMsg() ?: return false
        // 上传占位消息没有有效 CDN 信息时保留队列，等最新消息写入后再下载。
        if (file.url.isBlank() || file.key.isBlank()) return false
        val existing = database.rawQuery(
            "SELECT status FROM appattach WHERE msgInfoId=? AND msgInfoTalker=?",
            arrayOf(message.msgId.toString(), message.talker)
        ) ?: return false
        val status = existing.use { if (it.moveToFirst()) it.getLong(0) else null }
        if (status == 199L) return true
        if (status == null) {
            val initialized = KavaReflector.invokeOrThrow(initAttach, null,
                message.msgId, message.talker, message.content, null) as? String
            if (initialized.isNullOrBlank()) return false
        }
        val ctor = fileConstructor ?: return false
        val scene = KavaReflector.newInstance(ctor, message.msgId, message.talker,
            file.attachId.ifBlank { file.url }, callback(ctor.parameterTypes[3])) ?: return false
        return WeChatApis.network().sendRequest(scene)
    }
}