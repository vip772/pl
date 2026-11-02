package t.mma.hooks.items.securemessage

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import t.mma.dexkit.DexMethodCache
import t.mma.event.Events
import t.mma.hooks.api.message.SingleMessageMenuLocator
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

/** Forces WeChat's secure-message checks to false so the normal message menu is shown. */
class AntiSecureMessageFeature : BaseFeature() {
    override fun featureId(): String = SecureMessageSettings.ANTI_ID

    override fun name(): String = "反安全消息"

    override fun onFeatureInit(context: FeatureContext) {
        // 设置入口统一放在“特殊消息 -> 安全消息”页面，避免单独占用顶层功能项。
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), SecureMessageSettings.ANTI_PREFS)
        val runtime = PreferenceRuntimeOwner(
            prefs, setOf(SecureMessageSettings.KEY_ENABLE),
            enabled = { isEnabled(context.hostContext()) },
            create = { resources ->
                val owner = AntiSecureMessageRuntime(context, prefs, ::logFeatureError, ::logInfo)
                // 先登记释放再装 Hook：create 中途抛错时已装句柄仍归本代所有。
                resources.register { owner.close() }
                val installation = DexInstallScheduler.installation(
                    SecureMessageSettings.ANTI_ID, name(), stage = DexInstallScheduler.Stage.BRIDGE
                )
                resources.register { installation.close() }
                installation.schedule { owner.install() }
                val subscription = context.eventBus().subscribe(Events.DexReady::class.java, {
                    if (!resources.isClosed()) installation.schedule { owner.install() }
                })
                resources.register { context.eventBus().unsubscribe(subscription) }
            },
            onError = { logFeatureError("反安全消息启停失败", it) }
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
            return HchatStorage.preferences(context, SecureMessageSettings.ANTI_PREFS)
                .getBoolean(SecureMessageSettings.KEY_ENABLE, SecureMessageSettings.DEFAULT_ENABLE)
        }
    }
}

/**
 * 一次启用周期一个运行时：独占安装票根、DexReady 订阅与全部 Hook 句柄。
 *
 * 关闭只拒绝新工作，不打断已进入的 after 回调：菜单标记的前后配对必须在原调用上完成，
 * 否则临时隐藏的 sec_msg_node 会被永久丢弃。
 */
private class AntiSecureMessageRuntime(
    private val context: FeatureContext,
    private val prefs: SharedPreferences,
    private val logger: (String, Throwable?) -> Unit,
    private val info: (String) -> Unit
) : AutoCloseable {
    private val lock = Any()
    private val hooks = ArrayList<XC_MethodHook.Unhook>()
    private val installedMembers = LinkedHashSet<String>()
    private var checkInstalled = false
    private var stripInstalled = false
    private var menuInstalled = false
    private var interceptedLogged = false
    private var disabledHitLogged = false
    private var strippedLogged = false
    private var menuMarkerHiddenLogged = false
    private var menuMarkerRestoreFailedLogged = false
    private val menuMarkerStates = ThreadLocal<ArrayDeque<MenuMarkerState>>()
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
        checkInstalled = false
        stripInstalled = false
        menuInstalled = false
        val pending = synchronized(lock) { hooks.toList().also { hooks.clear() } }
        pending.asReversed().forEach { unhook ->
            runCatching { HookRegistry.get().unhook(unhook) }
                .onFailure { report("反安全消息Hook释放失败", it) }
        }
        synchronized(lock) { installedMembers.clear() }
    }

    /** 返回本次是否至少有一组已可用；失败保留票据供调度器重试。 */
    @Synchronized
    fun install(): Boolean {
        if (closed) return false
        methodPrefs = DexMethodCache.prefs(
            context.hostContext(), "Hchat_anti_secure_message_method_cache"
        )
        val runtimeKey = methodCacheKey()
        if (runtimeKey.isBlank()) {
            report("反安全消息安装跳过：微信运行时版本信息未就绪", null)
            return false
        }
        val checksReady = if (checkInstalled) true else installCheckHooks(runtimeKey)
        val stripReady = if (stripInstalled) true else installIncomingMarkerStrip()
        val menuReady = if (menuInstalled) true else installMenuMarkerHooks()
        return checksReady || stripReady || menuReady
    }

    /** 同一成员只装一次；句柄归本代所有，关闭与安装竞争时立即回滚新句柄。 */
    private fun hookOnce(method: Method, callback: XC_MethodHook): Boolean {
        val memberKey = method.toGenericString()
        synchronized(lock) {
            if (closed) return false
            if (!installedMembers.add(memberKey)) return true
        }
        val unhook = runCatching { HookRegistry.get().hook(method, callback) }.getOrElse {
            synchronized(lock) { installedMembers.remove(memberKey) }
            report("反安全消息Hook安装失败: $memberKey", it)
            return false
        }
        synchronized(lock) {
            if (!closed) {
                hooks.add(unhook)
                return true
            }
        }
        runCatching { HookRegistry.get().unhook(unhook) }
            .onFailure { report("反安全消息Hook释放失败", it) }
        synchronized(lock) { installedMembers.remove(memberKey) }
        return false
    }

    private fun installCheckHooks(runtimeKey: String): Boolean {
        val candidates = linkedSetOf<Method>()
        DexMethodCache.load(
            methodPrefs, runtimeKey, context.hostClassLoader(), SecureMessageSettings.CACHE_CHECK
        )?.takeIf(::isCheckMethod)?.let(candidates::add)
        candidates += locateRawChecks()
        if (candidates.isEmpty()) {
            DexMethodCache.clear(methodPrefs, runtimeKey, SecureMessageSettings.CACHE_CHECK)
            report("反安全消息检查方法未定位到，微信版本可能不匹配", null)
            checkInstalled = false
            return false
        }
        if (candidates.size == 1) {
            DexMethodCache.save(
                methodPrefs, runtimeKey, SecureMessageSettings.CACHE_CHECK, candidates.first()
            )
        } else {
            DexMethodCache.clear(methodPrefs, runtimeKey, SecureMessageSettings.CACHE_CHECK)
            log("反安全消息定位到 ${candidates.size} 个检查入口，已全部安装")
        }
        val ready = installCheckMethods(candidates.toList())
        checkInstalled = ready
        return ready
    }

    private fun installCheckMethods(methods: List<Method>): Boolean {
        var hooked = false
        methods.take(MAX_CHECK_HOOKS).forEach { method ->
            if (installCheckHook(method)) hooked = true
        }
        return hooked
    }

    private fun installCheckHook(method: Method): Boolean {
        if (closed) return false
        val ready = hookOnce(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                // 关闭后不再拦截，但不会因此丢弃已登记的句柄。
                if (closed) return
                if (!enabled()) {
                    if (!disabledHitLogged) {
                        disabledHitLogged = true
                        log("反安全消息检查已触发，但功能开关已关闭")
                    }
                    return
                }
                param.result = false
                if (!interceptedLogged) {
                    interceptedLogged = true
                    log("反安全消息已拦截安全标记检查: ${method.name}")
                }
            }
        })
        if (ready) log("反安全消息 Hook 已安装: ${method.toGenericString()}")
        return ready
    }

    /**
     * The recognised check covers messages already stored before Hchat starts.  For
     * newly received messages, remove only the secure node at the verified local insert
     * boundary, so later UI paths cannot reapply the menu restriction.
     */
    private fun installIncomingMarkerStrip(): Boolean {
        val insert = context.dexFinder().localMessageInsertMethod?.takeIf(::isInsertMethod)
            ?: return false
        val ready = hookOnce(insert, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (closed || !enabled()) return
                val message = param.args?.firstOrNull { it != null && isMessageLike(it) } ?: return
                if (isOutgoing(message)) return
                val current = readMessageSource(message)
                if (!SECURE_NODE.containsMatchIn(current)) return
                if (!setMessageSource(message, SECURE_NODE.replace(current, ""))) return
                if (!strippedLogged) {
                    strippedLogged = true
                    log("反安全消息已清除收到消息的安全标记")
                }
            }
        })
        if (ready) {
            log("反安全消息入库清除 Hook 已安装: ${insert.toGenericString()}")
        }
        stripInstalled = ready
        return ready
    }


    /**
     * The direct security check is not the only menu gate on 8.0.77.  The verified
     * single-message menu creator receives the selected row as its second parameter,
     * so hide the marker only while that creator builds this one menu.  The original
     * source is restored in the matching after callback and is never persisted here.
     */
    private fun installMenuMarkerHooks(): Boolean {
        val methods = SingleMessageMenuLocator.menuCreateMethods(context) { message, throwable ->
            report(message, throwable)
        }
        if (methods.isEmpty()) {
            report("反安全消息菜单创建方法未定位到", null)
            return false
        }
        var hooked = false
        methods.forEach { method ->
            val installed = hookOnce(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    // 关闭后仍压入 None 占位，保证 after 的栈配对不被破坏。
                    val states = menuMarkerStates.get()
                        ?: ArrayDeque<MenuMarkerState>().also { menuMarkerStates.set(it) }
                    states.addLast(if (closed) MenuMarkerState.None else hideMenuMarker(param))
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val states = menuMarkerStates.get() ?: return
                    val state = if (states.isEmpty()) null else states.removeLast()
                    if (states.isEmpty()) menuMarkerStates.remove()
                    restoreMenuMarker(state)
                }
            })
            hooked = hooked || installed
        }
        if (hooked) {
            menuInstalled = true
            log("反安全消息菜单恢复 Hook 已安装: ${methods.size} 个入口")
        }
        return hooked
    }

    private fun hideMenuMarker(param: XC_MethodHook.MethodHookParam): MenuMarkerState {
        if (closed || !enabled()) return MenuMarkerState.None
        val view = param.args?.getOrNull(1) as? View ?: return MenuMarkerState.None
        val message = resolveNativeMessage(view.tag) ?: return MenuMarkerState.None
        val source = readMessageSource(message)
        if (!SECURE_NODE.containsMatchIn(source)) return MenuMarkerState.None
        val stripped = SECURE_NODE.replace(source, "")
        if (!setMessageSource(message, stripped)) {
            if (!menuMarkerRestoreFailedLogged) {
                menuMarkerRestoreFailedLogged = true
                report("反安全消息菜单恢复失败: 当前消息 msgSource 不可写", null)
            }
            return MenuMarkerState.None
        }
        if (!menuMarkerHiddenLogged) {
            menuMarkerHiddenLogged = true
            log("反安全消息已在长按菜单构建期间临时隐藏安全标记")
        }
        return MenuMarkerState.Restorable(message, source)
    }

    private fun restoreMenuMarker(state: MenuMarkerState?) {
        val restorable = state as? MenuMarkerState.Restorable ?: return
        if (!setMessageSource(restorable.message, restorable.source) && !menuMarkerRestoreFailedLogged) {
            menuMarkerRestoreFailedLogged = true
            report("反安全消息菜单恢复后无法还原安全标记", null)
        }
    }


    private fun resolveNativeMessage(tag: Any?): Any? {
        tag ?: return null
        if (isNativeMessage(tag)) return tag
        var owner: Class<*>? = tag.javaClass
        while (owner != null && owner != Any::class.java) {
            for (field in KavaReflector.declaredFields(owner)) {
                if (KavaReflector.isStatic(field) || !field.type.name.startsWith(MESSAGE_PACKAGE)) continue
                KavaReflector.readField(field, tag)?.takeIf(::isNativeMessage)?.let { return it }
            }
            for (method in KavaReflector.declaredMethods(owner)) {
                if (KavaReflector.isStatic(method) || method.parameterTypes.isNotEmpty()) continue
                if (!method.returnType.name.startsWith(MESSAGE_PACKAGE)) continue
                KavaReflector.invoke(method, tag)?.takeIf(::isNativeMessage)?.let { return it }
            }
            owner = owner.superclass
        }
        return null
    }

    private fun isNativeMessage(value: Any): Boolean =
        value.javaClass.name.startsWith(MESSAGE_PACKAGE) &&
            (readNumber(value, "getMsgId", "field_msgId", "msgId", "msgID")?.toLong() ?: 0L) > 0L

    private fun enabled(): Boolean =
        prefs.getBoolean(SecureMessageSettings.KEY_ENABLE, SecureMessageSettings.DEFAULT_ENABLE)

    private fun isMessageLike(value: Any): Boolean =
        KavaReflector.readField(value, "field_type") != null ||
            KavaReflector.readField(value, "field_content") != null ||
            KavaReflector.readField(value, "field_msgSource") != null

    private fun isOutgoing(message: Any): Boolean =
        readNumber(message, "field_isSend", "isSend", "getIsSend", "getSend")?.toInt() == 1

    private fun readNumber(receiver: Any, vararg names: String): Number? {
        for (name in names) {
            val value = KavaReflector.readField(receiver, name)
                ?: KavaReflector.invokeMethod(receiver, name)
            if (value is Number) return value
        }
        return null
    }

    private fun readMessageSource(message: Any): String {
        for (fieldName in SOURCE_FIELDS) {
            (KavaReflector.readField(message, fieldName) as? String)
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
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

    private fun isCheckMethod(method: Method): Boolean =
        KavaReflector.isStatic(method) &&
            method.returnType == Boolean::class.javaPrimitiveType &&
            method.parameterCount == 1 &&
            !method.parameterTypes[0].isPrimitive

    private fun isInsertMethod(method: Method): Boolean {
        if (method.parameterCount !in 1..2) return false
        if (method.returnType != Void.TYPE && method.returnType != Long::class.javaPrimitiveType) return false
        return method.parameterTypes.any { !it.isPrimitive }
    }

    private fun locateRawChecks(): List<Method> = findMethods(listOf(SECURE_CHECK_ANCHOR))
        .filter(::isCheckMethod)
        .distinctBy { it.toGenericString() }

    private fun methodCacheKey(): String = DexMethodCache
        .runtimeKey(context.hostContext(), context.hostClassLoader())
        .takeIf { it.isNotBlank() }
        ?.let { "$it|${SecureMessageSettings.CACHE_SCHEMA}" }
        .orEmpty()

    private fun findMethods(strings: List<String>): List<Method> = runCatching {
        context.dexKitManager().withCurrentBridge { bridge ->
            val exact = bridge.findMethod(FindMethod().apply {
                matcher(MethodMatcher().apply { usingEqStrings(*strings.toTypedArray()) })
            })
            val candidates = if (exact.isNotEmpty()) exact else strings.asSequence()
                .flatMap { anchor ->
                    runCatching {
                        bridge.findMethod(FindMethod().apply {
                            matcher(MethodMatcher().apply { usingStrings(anchor) })
                        }).asSequence()
                    }.getOrDefault(emptySequence())
                }.toList()
            candidates.mapNotNull { data ->
                runCatching { data.getMethodInstance(context.hostClassLoader()) }.getOrNull()
            }.distinctBy { it.toGenericString() }
        }
    }.getOrElse {
        report("反安全消息方法定位失败", it)
        emptyList()
    }

    private companion object {
        const val MAX_CHECK_HOOKS = 6
        const val SECURE_CHECK_ANCHOR = ".msgsource.sec_msg_node.sfn"
        val SOURCE_SETTERS = arrayOf("setMsgSource", "setMsgsource", "setSource")
        // 8.0.77 (e9) stores MsgInfo.msgSource in the obfuscated G field.
        val SOURCE_FIELDS = arrayOf("field_msgSource", "msgSource", "G", "g")
        val SECURE_NODE = Regex(
            "<sec_msg_node" + "[^>]*>.*?</sec_msg_node>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        const val MESSAGE_PACKAGE = "com.tencent.mm.storage."
    }
}

private sealed interface MenuMarkerState {
    data object None : MenuMarkerState
    data class Restorable(val message: Any, val source: String) : MenuMarkerState
}
