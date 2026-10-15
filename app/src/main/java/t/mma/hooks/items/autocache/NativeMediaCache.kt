package t.mma.hooks.items.autocache

import org.luckypray.dexkit.wrap.DexMethod
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
    private val cache = DexMethodCache.prefs(context.hostContext(), "Hchat_native_media_cache_v4")
    @Volatile private var imageConstructor: Constructor<*>? = null
    @Volatile private var fileConstructor: Constructor<*>? = null
    @Volatile private var initAttach: Method? = null
    @Volatile private var downloadVideo: Method? = null

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
            ?: run {
                val version = WeChatApis.version()?.versionName().orEmpty()
                if (version == "8.0.78") {
                    // 已核对用户 APK classes12.dex 与持久化结果一致：
                    // AppAttachNewDownloadUI.Y7 -> AppMsgLogic.s(J,String,String,String):String。
                    // 用精确 DEX 描述符解析，绕过日志查询和无条件方法枚举。
                    val descriptor = "Lcom/tencent/mm/pluginsdk/model/app/k0;->s(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"
                    try {
                        val method = DexMethod(descriptor).getMethodInstance(context.hostClassLoader())
                        check(Modifier.isStatic(method.modifiers) && method.returnType == String::class.java &&
                            method.parameterTypes.toList() == listOf(long, String::class.java, String::class.java, String::class.java)) {
                            "附件初始化描述符签名不符: ${method.toGenericString()}"
                        }
                        DexMethodCache.save(cache, runtime, "attach_init", method)
                        report("8.0.78 精确初始化入口已解析: ${method.toGenericString()}")
                        method
                    } catch (error: Throwable) {
                        report("8.0.78 精确初始化解析失败: ${error.javaClass.name}: ${error.message}")
                        t.mma.utils.HLog.e("[Hchat:AutoCache] 精确初始化入口解析失败 version=$version descriptor=$descriptor", error)
                        null
                    }
                } else {
                    val candidates = methods("summerbig initDownloadAttach msgLocalId[%d], msgXml[%s], downloadPath[%s]")
                        .filter { Modifier.isStatic(it.modifiers) && it.returnType == String::class.java &&
                            it.parameterTypes.toList() == listOf(long, String::class.java, String::class.java, String::class.java) }
                    report("附件初始化定位 version=$version candidates=${candidates.map { it.toGenericString() }}")
                    candidates.singleOrNull()?.also { DexMethodCache.save(cache, runtime, "attach_init", it) }
                }
            }
        downloadVideo = DexMethodCache.load(cache, runtime, context.hostClassLoader(), "video_offline")
            ?: run {
                val online = methods("start complete online video, but can not stream video now!").toSet()
                context.dexKitBridge().findMethod(
                    FindMethod().apply { matcher(MethodMatcher().apply {
                        usingEqStrings("MicroMsg.VideoLogic", "ERR:", " getinfo failed: ", " get status failed: ", " update failed: ")
                        // 接收状态 112、更新掩码 3328；排除暂停接收和发送入口。
                        usingNumbers(112, 3328)
                    }) }
                ).map { it.getMethodInstance(context.hostClassLoader()) }
                    .filter { it !in online && Modifier.isStatic(it.modifiers) && it.returnType == int &&
                        it.parameterTypes.toList() == listOf(String::class.java) }.singleOrNull()
                    ?.also { DexMethodCache.save(cache, runtime, "video_offline", it) }
            }
        report("原生入口 image=${imageConstructor?.toGenericString()} file=${fileConstructor?.toGenericString()} init=${initAttach?.toGenericString()} video=${downloadVideo?.toGenericString()}")
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

    private val waitReasons = java.util.concurrent.ConcurrentHashMap<Long, String>()
    private fun report(message: String) {
        AutoCacheDiagnostics.record(context.hostContext(), message)
        t.mma.utils.HLog.i("[Hchat:AutoCache] $message")
    }
    private fun waiting(message: WeChatMessage, reason: String): Boolean {
        if (waitReasons.put(message.msgSvrId, reason) != reason) report("等待 msgId=${message.msgId} server=${message.msgSvrId}: $reason")
        return false
    }
    private fun attachment(scene: Any): Any? {
        // NetSceneDownloadAppAttach 构造器会按 msgInfoId/talker 查询微信附件存储。
        // 复用其真实记录，不依赖公共消息数据库是否包含 appattach 表。
        val fields = KavaReflector.declaredFields(scene.javaClass).filter {
            !Modifier.isStatic(it.modifiers) &&
                KavaReflector.findFieldRecursive(it.type, "field_msgInfoId") != null &&
                KavaReflector.findFieldRecursive(it.type, "field_fileFullPath") != null
        }
        check(fields.size == 1) { "附件实体字段不唯一: ${fields.size}" }
        return KavaReflector.readField(fields.single(), scene)
    }
    fun complete(message: WeChatMessage): Boolean {
        if (message.msgId <= 0 || message.msgSvrId <= 0) return false
        val db = WeChatApis.database() ?: return false
        if (message.isVideo()) {
            val info = WeChatApis.media()?.videos()?.resolveDownloadInfo(message.imagePath) ?: return false
            return info.totalLength > 0 && info.currentLength >= info.totalLength
        }
        if (message.isFile()) {
            val ctor = fileConstructor ?: return false
            val scene = KavaReflector.newInstance(ctor, message.msgId, message.talker,
                WeChatMessage.xmlTag(message.content, "attachid"), callback(ctor.parameterTypes[3]))
                ?: return waiting(message, "完成状态场景构造失败")
            val record = attachment(scene) ?: return false
            return (KavaReflector.readField(record, "field_status") as? Number)?.toLong() == 199L
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
        if (fileConstructor == null) return waiting(message, "附件下载构造器未定位")
        // 与微信附件页一致：不强制要求 CDN URL/AES；原生场景自行选择 CDN 或普通下载。
        // 这里只读取 attachid，不调用通用文件模型的 CDN 解析作为入队门槛。
        var mediaId = WeChatMessage.xmlTag(message.content, "attachid")
        val ctor = fileConstructor ?: return waiting(message, "附件下载构造器未定位")
        fun createScene(): Any = KavaReflector.newInstance(ctor, message.msgId, message.talker,
            mediaId, callback(ctor.parameterTypes[3]))
            ?: error("微信附件下载场景构造失败: ${ctor.toGenericString()}")
        var scene = createScene()
        var record = attachment(scene)
        if (record == null) {
            if (initAttach == null) return waiting(message, "附件初始化接口未定位")
            val initialized = KavaReflector.invokeOrThrow(initAttach, null,
                message.msgId, message.talker, message.content, null) as? String
            if (initialized.isNullOrBlank()) return waiting(message, "微信附件初始化返回空")
            mediaId = initialized
            scene = createScene()
            record = attachment(scene) ?: return waiting(message, "初始化后原生附件记录仍为空")
            report("附件记录已初始化 msgId=${message.msgId}")
        }
        val status = (KavaReflector.readField(record, "field_status") as? Number)?.toLong()
        if (status == 199L) return true
        val accepted = WeChatApis.network().sendRequest(scene)
        report("附件请求 accepted=$accepted msgId=${message.msgId} status=$status init=${initAttach != null}")
        return accepted
    }
}