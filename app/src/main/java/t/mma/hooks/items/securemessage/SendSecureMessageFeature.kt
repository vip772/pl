package t.mma.hooks.items.securemessage

import android.content.Context
import android.content.SharedPreferences
import de.robv.android.xposed.XC_MethodHook
import t.mma.dexkit.DexMethodCache
import t.mma.event.Events
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.model.WeChatMessageTypes
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.preferences.HchatStorage
import t.mma.utils.KavaReflector
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Runtime backing 特殊消息 → 安全消息; deliberately has no standalone settings provider. */
class SendSecureMessageFeature : BaseFeature() {
    override fun featureId(): String = SecureMessageSettings.SEND_ID

    override fun name(): String = "安全消息"

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), SecureMessageSettings.SEND_PREFS)
        val runtime = PreferenceRuntimeOwner(
            prefs, setOf(SecureMessageSettings.KEY_ENABLE),
            enabled = { isEnabled(context.hostContext()) },
            create = { resources ->
                val owner = SendSecureMessageRuntime(context, prefs, ::logFeatureError, ::logInfo)
                // 先登记释放再装 Hook：create 中途抛错时已装句柄仍归本代所有。
                resources.register { owner.close() }
                val installation = DexInstallScheduler.installation(
                    SecureMessageSettings.SEND_ID, name(), stage = DexInstallScheduler.Stage.BRIDGE
                )
                resources.register { installation.close() }
                installation.schedule { owner.install() }
                val subscription = context.eventBus().subscribe(Events.DexReady::class.java, {
                    if (!resources.isClosed()) installation.schedule { owner.install() }
                })
                resources.register { context.eventBus().unsubscribe(subscription) }
            },
            onError = { logFeatureError("安全消息启停失败", it) }
        )
        trackResource { runtime.close() }
        runtime.start()
    }

    private fun logFeatureError(message: String, throwable: Throwable?) {
        logError(message, throwable)
    }

    companion object {
        /** 设置读取入口：安装期与每组回调都用同一判定，不缓存布尔。 */
        @JvmStatic
        fun isEnabled(context: Context?): Boolean {
            if (context == null) return false
            return HchatStorage.preferences(context, SecureMessageSettings.SEND_PREFS)
                .getBoolean(SecureMessageSettings.KEY_ENABLE, SecureMessageSettings.DEFAULT_ENABLE)
        }
    }
}

/**
 * 一次启用周期一个运行时：独占安装票根、DexReady 订阅与全部 Hook 句柄。
 *
 * 开关与状态都归本代：关闭时只拒绝新工作，已装句柄在 close() 里反序释放。
 */
private class SendSecureMessageRuntime(
    private val context: FeatureContext,
    private val prefs: SharedPreferences,
    private val logger: (String, Throwable?) -> Unit,
    private val info: (String) -> Unit
) : AutoCloseable {
    private val lock = Any()
    private val hooks = ArrayList<XC_MethodHook.Unhook>()
    private val installedMembers = LinkedHashSet<String>()
    private var insertInstalled = false
    private var mediaInstalled = false
    private var mergeInstalled = false
    private var emojiSourceInstalled = false
    private var emojiDispatchInstalled = false
    private var emojiCheckInstalled = false
    private var markerLogged = false
    private var imageMarkerLogged = false
    private var markerFailureLogged = false
    private var hookMissLogged = false
    private var emojiSourceTriggeredLogged = false
    private var emojiDispatchTriggeredLogged = false
    private var emojiCheckTriggeredLogged = false
    private var unsupportedEmojiVersionLogged = false
    private lateinit var methodPrefs: SharedPreferences
    @Volatile private var closed = false

    private fun report(message: String, error: Throwable?) {
        runCatching { logger(message, error) }
    }

    private fun log(message: String) {
        runCatching { info(message) }
    }

    fun isClosed(): Boolean = closed

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        insertInstalled = false
        mediaInstalled = false
        mergeInstalled = false
        emojiSourceInstalled = false
        emojiDispatchInstalled = false
        emojiCheckInstalled = false
        val pending = synchronized(lock) { hooks.toList().also { hooks.clear() } }
        pending.asReversed().forEach { unhook ->
            runCatching { HookRegistry.get().unhook(unhook) }
                .onFailure { report("安全消息Hook释放失败", it) }
        }
        synchronized(lock) { installedMembers.clear() }
    }

    /** 返回本次是否三条链路都就绪；任一失败保留票据供调度器重试。 */
    @Synchronized
    fun install(): Boolean {
        if (closed) return false
        methodPrefs = DexMethodCache.prefs(
            context.hostContext(), "Hchat_secure_message_method_cache"
        )
        val runtimeKey = methodCacheKey()
        if (runtimeKey.isBlank()) {
            report("安全消息安装跳过：微信运行时版本信息未就绪", null)
            return false
        }
        val direct = context.dexFinder().localMessageInsertMethod?.takeIf(::isInsertMethod)
        if (direct != null) {
            DexMethodCache.save(methodPrefs, runtimeKey, SecureMessageSettings.CACHE_INSERT, direct)
            log("安全消息使用微信本地消息插入 API: ${direct.toGenericString()}")
        }
        val insert = direct
            ?: cachedOrLocate(runtimeKey, SecureMessageSettings.CACHE_INSERT, INSERT_ANCHOR, ::isInsertMethod)
            ?: run {
                report("安全消息入库方法未定位到，微信版本可能不匹配", null)
                return false
            }
        val insertReady = installInsertHook(insert)
        val mergeReady = installSourceMergeHooks(insert)
        val mediaReady = installMediaEncodingHooks()
        val emojiReady = installEmojiHooks()
        val emojiCheckReady = if (emojiCheckInstalled) true else installEmojiSelfCheckHooks(runtimeKey)
        return insertReady && mergeReady && mediaReady && emojiReady && emojiCheckReady
    }

    /**
     * 8.0.78 rebuilds media MsgSource in its upload tasks, after local insertion.
     * Patch the verified request field before BOTH size calculation and encoding;
     * changing only writeFields would invalidate nested protobuf length prefixes.
     */
    private fun installMediaEncodingHooks(): Boolean {
        if (mediaInstalled) return true
        val version = WeChatApis.version()?.current()
        if (version?.versionName != "8.0.78" || version.versionCode != 3180L) return true
        val loader = context.hostClassLoader()
        var complete = true
        val fields = mapOf("pc5.ot" to "i", "pc5.h37" to "t",
            "pc5.q76" to "p", "pc5.kk0" to "p")
        fields.forEach { (className, fieldName) ->
            val ready = runCatching {
                val owner = requireNotNull(KavaReflector.loadClass(className, loader))
                val field = requireNotNull(KavaReflector.findFieldRecursive(owner, fieldName))
                check(field.type == String::class.java)
                val target = KavaReflector.declaredMethods(owner).single {
                    it.name == "op" && it.returnType == Int::class.javaPrimitiveType &&
                        it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Array<Any>::class.java))
                }
                hookOnce(target, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (closed || !enabled()) return
                        val operation = param.args?.getOrNull(0) as? Int ?: return
                        if (operation != 0 && operation != 1) return
                        val request = param.thisObject ?: return
                        val source = KavaReflector.readField(field, request) as? String
                        if (SecureMessageSource.containsMarker(source)) return
                        if (!KavaReflector.writeField(field, request, SecureMessageSource.addMarker(source))) {
                            report("媒体请求 MsgSource 不可写: $className.$fieldName", null)
                        }
                    }
                })
            }.getOrElse { report("媒体安全消息编码入口安装失败: $className", it); false }
            complete = ready && complete
        }
        // PB3 UploadMsgImg uses indexed fields rather than the legacy op() protocol.
        val imageReady = runCatching {
            val imageClass = requireNotNull(KavaReflector.loadClass("pc5.v27", loader))
            val owner = requireNotNull(KavaReflector.loadClass("com.tencent.mm.protobuf.e", loader))
            check(owner.isAssignableFrom(imageClass))
            val getter = requireNotNull(KavaReflector.findMethodRecursive(owner, "getString", Int::class.javaPrimitiveType!!))
            val setter = requireNotNull(KavaReflector.findMethodRecursive(owner, "set", Int::class.javaPrimitiveType!!, Any::class.java))
            val targets = KavaReflector.declaredMethods(owner).filter {
                (it.name == "computeSize" && it.parameterCount == 0 && it.returnType == Int::class.javaPrimitiveType) ||
                    (it.name == "writeFields" && it.parameterCount == 1 && it.returnType == Void.TYPE)
            }
            check(targets.size == 2)
            targets.map { target ->
                hookOnce(target, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // Immediate type gate: other PB3 messages do no preference/reflection/XML work.
                        val request = param.thisObject ?: return
                        if (request.javaClass != imageClass || closed || !enabled()) return
                        val source = KavaReflector.invoke(getter, request, 9) as? String
                        if (SecureMessageSource.containsMarker(source)) return
                        if (!KavaReflector.invokeSuccessfully(setter, request, 9, SecureMessageSource.addMarker(source))) {
                            report("图片请求 MsgSource 不可写: pc5.v27[9]", null)
                        }
                    }
                })
            }.all { it }
        }.getOrElse { report("图片安全消息 PB3 编码入口安装失败", it); false }
        mediaInstalled = complete && imageReady
        return mediaInstalled
    }

    private fun hookOnce(method: Method, callback: XC_MethodHook): Boolean {
        val memberKey = method.toGenericString()
        synchronized(lock) {
            if (closed) return false
            if (!installedMembers.add(memberKey)) return true
        }
        val unhook = runCatching { HookRegistry.get().hook(method, callback) }.getOrElse {
            synchronized(lock) { installedMembers.remove(memberKey) }
            report("安全消息Hook安装失败: $memberKey", it)
            return false
        }
        synchronized(lock) {
            if (!closed) {
                hooks.add(unhook)
                return true
            }
        }
        runCatching { HookRegistry.get().unhook(unhook) }
            .onFailure { report("安全消息Hook释放失败", it) }
        synchronized(lock) { installedMembers.remove(memberKey) }
        return false
    }

    private fun installInsertHook(insert: Method): Boolean {
        if (insertInstalled) return true
        val ready = hookOnce(insert, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                // 关闭后不再写标记，但不会因此丢弃已登记的句柄。
                if (closed || !enabled()) return
                val args = param.args ?: return
                val message = args.firstOrNull { it != null && isMessageLike(it) } ?: run {
                    if (!hookMissLogged) {
                        hookMissLogged = true
                        log("安全消息 Hook 已触发，但未找到消息参数")
                    }
                    return
                }
                if (!isSupportedOutgoingMessage(message)) return
                addSecureMarker(message)
            }
        })
        if (ready) {
            insertInstalled = true
            log("安全消息 Hook 已安装: ${insert.toGenericString()}")
        }
        return ready
    }

    /**
     * Media senders assign msgSource before the final local insert. Hook the setter layer
     * as well so the marker reaches the actual image/video/emoji/AppMsg send request.
     * Candidates are derived from the already verified insert argument class; no
     * obfuscated method name is guessed.
     */
    private fun installSourceMergeHooks(insert: Method): Boolean {
        if (mergeInstalled) return true
        val messageClass = insert.parameterTypes.firstOrNull {
            !it.isPrimitive && it != String::class.java && it.name.startsWith(MESSAGE_PACKAGE)
        } ?: return false
        val candidates = generateSequence<Class<*>>(messageClass) { current -> current.superclass }
            .takeWhile { it != Any::class.java }
            .flatMap { KavaReflector.declaredMethods(it).asSequence() }
            .filter(::isStringSetter)
            .distinctBy { it.toGenericString() }
            .toList()
        if (candidates.isEmpty()) {
            report("安全消息 msgSource 合并入口未找到: ${messageClass.name}", null)
            return false
        }
        var count = 0
        candidates.forEach { method ->
            val ready = hookOnce(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (closed || !enabled()) return
                    val source = param.args?.getOrNull(0) as? String ?: return
                    if (!looksLikeMsgSource(source)) return
                    val message = param.thisObject ?: return
                    if (!isSupportedOutgoingMessage(message)) return
                    param.args[0] = SecureMessageSource.addMarker(source)
                }
            })
            if (ready) count++
        }
        mergeInstalled = count > 0
        if (mergeInstalled) log("安全消息 msgSource 合并 Hook 已安装: $count 个候选")
        return mergeInstalled
    }

    /**
     * 表情发送会在本地消息入库后重新生成 MsgSource，因此通用入库 Hook 不足以覆盖
     * 最终网络请求。这里按已确认的 8.0.76/8.0.77 链路补写两次标记。
     */
    private fun installEmojiHooks(): Boolean {
        if (emojiSourceInstalled && emojiDispatchInstalled) return true
        val version = WeChatApis.version()?.current()
        val profile = EmojiSecureMessageProfile.forVersion(version?.versionName, version?.versionCode ?: 0L)
        if (profile == null) {
            if (!unsupportedEmojiVersionLogged) {
                unsupportedEmojiVersionLogged = true
                log(
                    "表情安全消息专用链路未启用: 微信 ${version?.displayVersion() ?: "版本未知"} " +
                        "不在已验证映射中"
                )
            }
            return true
        }
        val hostVersion = version?.displayVersion() ?: "未知"
        val sourceReady = emojiSourceInstalled || installEmojiSourceHook(profile, hostVersion)
        val dispatchReady = emojiDispatchInstalled || installEmojiDispatchHook(profile, hostVersion)
        return sourceReady && dispatchReady
    }

    /**
     * 自发出的表情在本地回显/数据库更新后可能丢失 msgSource，但长按菜单仍会经过
     * 微信的 sec_msg_node 检查。对 type=47 且 isSend=1 的消息做精确兜底，避免自己
     * 可以转发表情、别人却不能转发的不一致行为；反安全消息开关不参与此判断。
     */
    private fun installEmojiSelfCheckHooks(runtimeKey: String): Boolean {
        val candidates = linkedSetOf<Method>()
        DexMethodCache.load(
            methodPrefs, runtimeKey, context.hostClassLoader(), SecureMessageSettings.CACHE_EMOJI_CHECK
        )?.takeIf(::isCheckMethod)?.let(candidates::add)
        candidates += findMethods(SECURE_CHECK_ANCHOR).filter(::isCheckMethod)
        if (candidates.isEmpty()) {
            report("自发表情安全检查兜底入口未定位到", null)
            return true
        }
        if (candidates.size == 1) {
            DexMethodCache.save(
                methodPrefs, runtimeKey, SecureMessageSettings.CACHE_EMOJI_CHECK, candidates.first()
            )
        } else {
            DexMethodCache.clear(methodPrefs, runtimeKey, SecureMessageSettings.CACHE_EMOJI_CHECK)
        }
        var hooked = false
        candidates.take(MAX_CHECK_HOOKS).forEach { method ->
            val ready = hookOnce(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (closed || !enabled()) return
                    val message = param.args?.firstOrNull { it != null && isMessageLike(it) } ?: return
                    if (!isOutgoingEmoji(message)) return
                    if (SecureMessageSource.containsMarker(readMessageSource(message))) return
                    param.result = true
                    if (!emojiCheckTriggeredLogged) {
                        emojiCheckTriggeredLogged = true
                        log("自发表情安全检查兜底已生效: ${method.name}")
                    }
                }
            })
            if (ready) hooked = true
        }
        emojiCheckInstalled = hooked
        if (hooked) log("自发表情安全检查兜底 Hook 已安装: ${candidates.size} 个入口")
        return hooked
    }

    private fun isOutgoingEmoji(message: Any): Boolean {
        val send = readNumber(message, "field_isSend", "isSend", "getIsSend", "getSend")?.toInt()
        if (send != 1) return false
        return readNumber(message, "field_type", "type", "getType", "getMsgType")?.toInt() == EMOJI_TYPE
    }

    private fun isCheckMethod(method: Method): Boolean =
        KavaReflector.isStatic(method) &&
            method.returnType == Boolean::class.javaPrimitiveType &&
            method.parameterCount == 1 &&
            !method.parameterTypes[0].isPrimitive

    private fun installEmojiSourceHook(
        profile: EmojiSecureMessageProfile,
        hostVersion: String
    ): Boolean = runCatching {
        val loader = context.hostClassLoader()
        val messageClass = requireNotNull(KavaReflector.loadClass(EMOJI_MESSAGE_CLASS, loader)) {
            "未找到 $EMOJI_MESSAGE_CLASS"
        }
        val owner = requireNotNull(KavaReflector.loadClass(profile.sourceOwner, loader)) {
            "未找到 ${profile.sourceOwner}"
        }
        val target = requireNotNull(KavaReflector.findMethodRecursive(owner, EMOJI_SOURCE_METHOD, messageClass)) {
            "未找到 ${profile.sourceOwner}.$EMOJI_SOURCE_METHOD(${messageClass.name})"
        }
        check(target.returnType == String::class.java) { "表情 MsgSource 方法返回类型不匹配: $target" }
        val ready = hookOnce(target, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (closed || !enabled() || param.hasThrowable()) return
                val message = param.args?.getOrNull(0) ?: return
                if (readNumber(message, "field_type", "type", "getType", "getMsgType")?.toInt() != EMOJI_TYPE) {
                    return
                }
                param.result = SecureMessageSource.addMarker(param.result as? String)
                if (!emojiSourceTriggeredLogged) {
                    emojiSourceTriggeredLogged = true
                    log("表情 MsgSource 安全标记已写入")
                }
            }
        })
        if (ready) {
            emojiSourceInstalled = true
            log("表情 MsgSource Hook 已安装[$hostVersion]: ${target.toGenericString()}")
        }
        ready
    }.getOrElse {
        report("表情 MsgSource Hook 安装失败[$hostVersion]", it)
        false
    }

    private fun installEmojiDispatchHook(
        profile: EmojiSecureMessageProfile,
        hostVersion: String
    ): Boolean = runCatching {
        val loader = context.hostClassLoader()
        val scene = requireNotNull(KavaReflector.loadClass(profile.emojiScene, loader)) {
            "未找到 ${profile.emojiScene}"
        }
        val network = requireNotNull(KavaReflector.loadClass(EMOJI_NETWORK_CLASS, loader)) {
            "未找到 $EMOJI_NETWORK_CLASS"
        }
        val dispatcher = requireNotNull(KavaReflector.loadClass(EMOJI_DISPATCHER_CLASS, loader)) {
            "未找到 $EMOJI_DISPATCHER_CLASS"
        }
        val target = requireNotNull(
            KavaReflector.findMethodRecursive(scene, EMOJI_DISPATCH_METHOD, network, dispatcher)
        ) {
            "未找到 ${profile.emojiScene}.$EMOJI_DISPATCH_METHOD(${network.name},${dispatcher.name})"
        }
        check(target.returnType == Int::class.javaPrimitiveType) { "表情请求分发方法返回类型不匹配: $target" }
        val ready = hookOnce(target, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (closed || !enabled()) return
                try {
                    val request = KavaReflector.readField(param.thisObject, EMOJI_SCENE_REQUEST_FIELD) ?: return
                    val wrapper = KavaReflector.readField(request, EMOJI_REQUEST_WRAPPER_FIELD) ?: return
                    val list = KavaReflector.readField(wrapper, EMOJI_REQUEST_LIST_FIELD) ?: return
                    val items = KavaReflector.readField(list, EMOJI_ITEMS_FIELD) as? java.util.List<*> ?: return
                    val item = items.firstOrNull() ?: return
                    val field = KavaReflector.findFieldRecursive(item.javaClass, EMOJI_SOURCE_FIELD)
                    val source = KavaReflector.readField(field, item) as? String
                    check(KavaReflector.writeField(field, item, SecureMessageSource.addMarker(source))) {
                        "写入 sendemoji 请求 MsgSource 失败"
                    }
                    if (!emojiDispatchTriggeredLogged) {
                        emojiDispatchTriggeredLogged = true
                        log("表情 sendemoji 请求安全标记已写入")
                    }
                } catch (error: Throwable) {
                    report("表情 sendemoji 请求安全标记注入失败[$hostVersion]", error)
                }
            }
        })
        if (ready) {
            emojiDispatchInstalled = true
            log("表情 sendemoji Hook 已安装[$hostVersion]: ${target.toGenericString()}")
        }
        ready
    }.getOrElse {
        report("表情 sendemoji Hook 安装失败[$hostVersion]", it)
        false
    }

    private fun enabled(): Boolean =
        prefs.getBoolean(SecureMessageSettings.KEY_ENABLE, SecureMessageSettings.DEFAULT_ENABLE)

    private fun isImageMessage(message: Any): Boolean {
        val type = readNumber(message, "field_type", "type", "getType", "getMsgType")?.toInt() ?: return false
        return WeChatMessageTypes.normalize(type) == WeChatMessageTypes.IMAGE
    }

    private fun isMessageLike(value: Any): Boolean =
        readNumber(value, "field_type", "type", "getType", "getMsgType") != null ||
            SOURCE_FIELDS.any { KavaReflector.readField(value, it) != null }

    private fun addSecureMarker(message: Any) {
        val current = readMessageSource(message)
        if (SecureMessageSource.containsMarker(current)) return
        val updated = SecureMessageSource.addMarker(current)
        if (!setMessageSource(message, updated)) {
            if (!markerFailureLogged) {
                markerFailureLogged = true
                report("安全消息标记注入失败: msgSource 不可写，类型=${message.javaClass.name}", null)
            }
        } else if (isImageMessage(message) && !imageMarkerLogged) {
            imageMarkerLogged = true
            log("图片安全消息标记已写入 MsgSource")
        } else if (!markerLogged) {
            markerLogged = true
            log("安全消息标记已写入消息")
        }
    }

    private fun readMessageSource(message: Any): String {
        for (fieldName in SOURCE_FIELDS) {
            (KavaReflector.readField(message, fieldName) as? String)?.let { return it }
        }
        return (KavaReflector.invokeMethod(message, "getMsgSource") as? String).orEmpty()
    }

    private fun setMessageSource(message: Any, value: String): Boolean {
        for (name in SOURCE_SETTERS) {
            val method = KavaReflector.findCompatibleMethod(message.javaClass, name, value)
            if (KavaReflector.invokeSuccessfully(method, message, value)) return true
        }
        return SOURCE_FIELDS.any { fieldName -> KavaReflector.writeField(message, fieldName, value) }
    }

    private fun isSupportedOutgoingMessage(message: Any): Boolean {
        val send = readNumber(message, "field_isSend", "isSend", "getIsSend", "getSend")
        if (send?.toInt() != 1) return false
        val type = readNumber(message, "field_type", "type", "getType", "getMsgType")
        if (type == null) return true
        val normalized = WeChatMessageTypes.normalize(type.toInt())
        return normalized == WeChatMessageTypes.TEXT ||
            normalized == WeChatMessageTypes.IMAGE ||
            normalized == WeChatMessageTypes.VIDEO ||
            normalized == VIDEO_COMPAT ||
            normalized == WeChatMessageTypes.EMOJI ||
            normalized == WeChatMessageTypes.APP
    }

    private fun readNumber(receiver: Any, vararg names: String): Number? {
        for (name in names) {
            val value = KavaReflector.readField(receiver, name) ?: KavaReflector.invokeMethod(receiver, name)
            if (value is Number) return value
        }
        return null
    }

    private fun isInsertMethod(method: Method): Boolean {
        if (method.parameterCount !in 1..2) return false
        if (method.returnType != Void.TYPE && method.returnType != Long::class.javaPrimitiveType) return false
        return method.parameterTypes.any { !it.isPrimitive }
    }

    private fun isStringSetter(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            !Modifier.isAbstract(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(arrayOf(String::class.java))
    }

    private fun looksLikeMsgSource(value: String): Boolean {
        val trimmed = value.trimStart()
        return trimmed.startsWith("<msgsource", ignoreCase = true) &&
            trimmed.contains("</msgsource>", ignoreCase = true)
    }

    private fun cachedOrLocate(
        runtimeKey: String,
        name: String,
        anchor: String,
        predicate: (Method) -> Boolean
    ): Method? {
        DexMethodCache.load(methodPrefs, runtimeKey, context.hostClassLoader(), name)
            ?.takeIf(predicate)?.let { return it }
        val candidates = findMethods(anchor)
            .filter(predicate).distinctBy { it.toGenericString() }
        val method = candidates.maxByOrNull { candidate ->
            val params = candidate.parameterTypes
            var score = 0
            if (params.size == 2 && params[1] == Boolean::class.javaPrimitiveType) score += 8
            if (params.any { it.name.startsWith("com.tencent.mm.storage.") }) score += 6
            if (params.any { it.name.contains("Msg", true) || it.simpleName.equals("k9", true) }) score += 2
            score
        }
        if (method != null) DexMethodCache.save(methodPrefs, runtimeKey, name, method)
        else DexMethodCache.clear(methodPrefs, runtimeKey, name)
        return method
    }

    private fun methodCacheKey(): String = DexMethodCache
        .runtimeKey(context.hostContext(), context.hostClassLoader())
        .takeIf { it.isNotBlank() }
        ?.let { "$it|${SecureMessageSettings.CACHE_SCHEMA}" }
        .orEmpty()

    private fun findMethods(anchor: String): List<Method> = runCatching {
        context.dexKitManager().withCurrentBridge { bridge ->
            val exact = bridge.findMethod(FindMethod().apply {
                matcher(MethodMatcher().apply { usingEqStrings(anchor) })
            })
            val candidates = if (exact.isNotEmpty()) exact else {
                runCatching {
                    bridge.findMethod(FindMethod().apply {
                        matcher(MethodMatcher().apply { usingStrings(anchor) })
                    })
                }.getOrDefault(emptyList())
            }
            candidates.mapNotNull { data ->
                runCatching { data.getMethodInstance(context.hostClassLoader()) }.getOrNull()
            }.distinctBy { it.toGenericString() }
        }
    }.getOrElse {
        report("安全消息方法定位失败", it)
        emptyList()
    }

    private companion object {
        const val MAX_CHECK_HOOKS = 6
        const val INSERT_ANCHOR = "Error insert message msg:%s talker:%s"
        const val SECURE_CHECK_ANCHOR = ".msgsource.sec_msg_node.sfn"
        val SOURCE_SETTERS = arrayOf("setMsgSource", "setMsgsource", "setSource")
        // 8.0.77 (e9) stores MsgInfo.msgSource in the obfuscated G field.
        val SOURCE_FIELDS = arrayOf("field_msgSource", "msgSource", "G", "g")
        const val VIDEO_COMPAT = 62
        const val MESSAGE_PACKAGE = "com.tencent.mm.storage."
        const val EMOJI_TYPE = 47
        const val EMOJI_MESSAGE_CLASS = "com.tencent.mm.storage.e9"
        const val EMOJI_SOURCE_METHOD = "a"
        const val EMOJI_DISPATCH_METHOD = "doScene"
        const val EMOJI_NETWORK_CLASS = "com.tencent.mm.network.s"
        const val EMOJI_DISPATCHER_CLASS = "com.tencent.mm.modelbase.u0"
        const val EMOJI_SCENE_REQUEST_FIELD = "d"
        const val EMOJI_REQUEST_WRAPPER_FIELD = "a"
        const val EMOJI_REQUEST_LIST_FIELD = "a"
        const val EMOJI_ITEMS_FIELD = "e"
        const val EMOJI_SOURCE_FIELD = "p"
    }
}
