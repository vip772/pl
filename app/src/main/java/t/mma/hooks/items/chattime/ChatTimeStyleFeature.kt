package t.mma.hooks.items.chattime

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import t.mma.dexkit.DexMethodCache
import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.FeatureResourceScope
import t.mma.hooks.core.HookRegistry
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.preferences.HchatStorage
import t.mma.utils.HLog
import t.mma.utils.KavaReflector
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap
import java.util.WeakHashMap

/** 会话时间样式：一个启用区间一个运行时，独占安装票根、DexReady 订阅、Hook 句柄与恢复投递。 */
class ChatTimeStyleFeature : BaseFeature() {
    override fun featureId(): String = ID
    override fun name(): String = "会话时间样式"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(ChatTimeStyleSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), ChatTimeStyleSettings.PREFS_NAME)
        val runtime = PreferenceRuntimeOwner(
            prefs,
            setOf(ChatTimeStyleSettings.KEY_MODE, ChatTimeStyleSettings.KEY_TIME_FORMAT),
            enabled = { ChatTimeStyleSettings.isConfigured(prefs) },
            create = { resources -> startGeneration(context, resources) },
            onError = { logError("会话时间样式启停失败", it) }
        )
        trackResource { runtime.close() }
        runtime.start()
    }

    /** 只登记本代资源；任何一步失败都由作用域逆序关闭已持有的句柄。 */
    private fun startGeneration(context: FeatureContext, resources: FeatureResourceScope) {
        val owner = ChatTimeStyleRuntime(context, ::report)
        resources.register { owner.close() }
        val installation = DexInstallScheduler.installation(
            ID, name(), stage = DexInstallScheduler.Stage.BRIDGE
        )
        resources.register { installation.close() }
        val subscription = context.eventBus().subscribe(Events.DexReady::class.java, {
            if (!resources.isClosed()) installation.schedule { owner.install() }
        })
        resources.register { context.eventBus().unsubscribe(subscription) }
        installation.schedule { owner.install() }
    }

    private fun report(message: String, throwable: Throwable?) {
        logError(message, throwable)
    }

    companion object {
        const val ID = "chat_time_style"
    }
}

private class ChatTimeStyleRuntime(
    private val context: FeatureContext,
    private val logger: (String, Throwable?) -> Unit
) {
    /** 原生快照只在视图仍由本代写入时复用；styledText 用于识别宿主复用。 */
    private class BoundTime(
        var createTime: Long,
        val nativeText: String,
        val nativeVisibility: Int
    ) {
        var styledText: String? = nativeText
    }

    private class ResolutionMetadata {
        var nestedFields: List<Field>? = null
        val methods = HashMap<String, Method?>()
        val fields = HashMap<String, Field?>()
    }

    // Keys use Class identity (including loader); values contain metadata, never messages.
    private val resolutionMetadata = object : LinkedHashMap<Class<*>, ResolutionMetadata>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Class<*>, ResolutionMetadata>?
        ): Boolean = size > 128
    }

    private val bindings = Collections.synchronizedMap(WeakHashMap<TextView, BoundTime>())
    private val prefs = HchatStorage.preferences(context.hostContext(), ChatTimeStyleSettings.PREFS_NAME)
    private val methodPrefs = DexMethodCache.prefs(context.hostContext(), METHOD_PREFS_NAME)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val hooks = ArrayList<XC_MethodHook.Unhook>()
    private val rootFieldCache = BoundedLruMap<Class<*>, Field>(FIELD_CACHE_LIMIT)
    private val timeFieldCache = BoundedLruMap<Class<*>, Field>(FIELD_CACHE_LIMIT)
    private val unsupportedTimeHolders = BoundedLruSet<Class<*>>(FIELD_CACHE_LIMIT)
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ChatTimeStyleSettings.KEY_MODE || key == ChatTimeStyleSettings.KEY_TIME_FORMAT) {
            refreshAttachedTimes()
        }
    }

    @Volatile private var closed = false
    @Volatile private var installed = false

    init {
        try {
            prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        } catch (error: Throwable) {
            logger("$TAG 注册会话时间样式偏好监听失败: ${error.message}", error)
        }
    }

    /** 关闭只拒绝新工作：先失活 Hook，再恢复本次代写入的视图，最后清空缓存。 */
    fun close() {
        val pending = synchronized(lock) {
            if (closed) return
            closed = true
            installed = false
            hooks.toList().also { hooks.clear() }
        }
        pending.asReversed().forEach { unhook ->
            runCatching { HookRegistry.get().unhook(unhook) }
                .onFailure { logger("$TAG 会话时间样式Hook释放失败: ${it.message}", it) }
        }
        runCatching { prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener) }
            .onFailure { logger("$TAG 会话时间样式偏好监听释放失败: ${it.message}", it) }
        val attached = synchronized(bindings) {
            bindings.entries.map { it.key to it.value }.also { bindings.clear() }
        }
        attached.forEach { (view, bound) ->
            runCatching { restoreOriginal(view, bound, 0) }
                .onFailure { logger("$TAG 会话时间样式恢复异常: ${it.message}", it) }
        }
        rootFieldCache.clear()
        timeFieldCache.clear()
        unsupportedTimeHolders.clear()
        synchronized(resolutionMetadata) { resolutionMetadata.clear() }
    }

    /** 返回本次是否真正装上；失败保留票根由调度器退避重试。 */
    @Synchronized
    fun install(): Boolean {
        if (closed) return false
        if (installed) return true
        val method = locateBindMethod() ?: run {
            HLog.e("$TAG 定位聊天时间绑定方法失败")
            return false
        }
        return runCatching {
            val unhook = HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (closed) return
                    bindTime(param.args)
                }
            })
            synchronized(lock) {
                if (closed) {
                    runCatching { HookRegistry.get().unhook(unhook) }
                } else {
                    hooks.add(unhook)
                }
            }
            if (closed) return false
            installed = true
            true
        }.getOrElse {
            logger("$TAG 安装聊天时间样式 Hook 失败: ${it.message}", it)
            false
        }
    }

    private fun bindTime(args: Array<Any?>?) {
        val mode = currentMode()
        if (mode == ChatTimeStyleSettings.MODE_ORIGINAL) return
        val holder = messageHolder(args) ?: return
        val root = findRootView(holder) ?: return
        val taggedHolder = root.tag ?: holder
        val timeView = findTimeView(taggedHolder) ?: findTimeView(holder) ?: return
        val createTime = (resolveMessageCreateTime(args?.getOrNull(1))
            ?: resolveMessageCreateTime(args?.filterIndexed { index, _ -> index != 1 }))
            ?: 0L
        val currentText = timeView.text?.toString().orEmpty()
        val existing = synchronized(bindings) { bindings[timeView] }
        // 同一视图仍是本代写入的文本时沿用原生快照，避免把“已被我们改成隐藏”的状态当成原生状态。
        val bound = if (existing != null && existing.styledText == currentText) {
            existing.createTime = createTime
            existing
        } else {
            BoundTime(createTime, currentText, timeView.visibility)
        }
        synchronized(bindings) { bindings[timeView] = bound }
        applyStyle(timeView, bound, mode)
    }

    private fun applyStyle(view: TextView, bound: BoundTime, mode: String) {
        when (mode) {
            ChatTimeStyleSettings.MODE_HIDDEN -> view.visibility = View.GONE
            ChatTimeStyleSettings.MODE_CUSTOM -> {
                view.visibility = bound.nativeVisibility
                view.text = if (bound.nativeVisibility == View.VISIBLE && bound.createTime > 0L) {
                    formatTime(bound.createTime)
                } else {
                    bound.nativeText
                }
            }
            else -> {
                view.text = bound.nativeText
                view.visibility = bound.nativeVisibility
            }
        }
        bound.styledText = view.text?.toString().orEmpty()
    }

    /** 恢复只作用于仍由本代写入的视图；宿主复用后的新内容保持原样。 */
    private fun restoreOriginal(view: TextView, bound: BoundTime, attempt: Int) {
        if (!stillOwned(view, bound)) return
        if (attempt >= RESTORE_RETRY_LIMIT) {
            if (Looper.myLooper() == Looper.getMainLooper() && stillOwned(view, bound)) {
                view.text = bound.nativeText
                view.visibility = bound.nativeVisibility
            } else {
                logger("$TAG 会话时间样式恢复放弃，视图已不可投递", null)
            }
            return
        }
        val posted = runCatching {
            view.post {
                val restored = runCatching {
                    if (stillOwned(view, bound)) {
                        view.text = bound.nativeText
                        view.visibility = bound.nativeVisibility
                    }
                }
                if (restored.isFailure) restoreOriginal(view, bound, attempt + 1)
            }
        }.getOrDefault(false)
        if (!posted) {
            val fallback = runCatching {
                mainHandler.post {
                    if (stillOwned(view, bound)) {
                        view.text = bound.nativeText
                        view.visibility = bound.nativeVisibility
                    }
                }
            }.getOrDefault(false)
            if (!fallback) logger("$TAG 会话时间样式恢复投递失败，保留宿主视图状态", null)
        }
    }

    private fun stillOwned(view: TextView, bound: BoundTime): Boolean =
        view.text?.toString().orEmpty() == bound.styledText

    private fun refreshAttachedTimes() {
        if (closed) return
        val mode = currentMode()
        if (mode == ChatTimeStyleSettings.MODE_ORIGINAL) return
        val attached = synchronized(bindings) { bindings.entries.map { it.key to it.value } }
        attached.forEach { (view, bound) ->
            val posted = runCatching {
                view.post {
                    if (closed) return@post
                    if (synchronized(bindings) { bindings[view] } !== bound) return@post
                    applyStyle(view, bound, mode)
                }
            }.getOrDefault(false)
            if (!posted) {
                mainHandler.post { if (!closed) applyStyle(view, bound, mode) }
            }
        }
    }

    private fun currentMode(): String = ChatTimeStyleSettings.normalizeMode(
        prefs.getString(ChatTimeStyleSettings.KEY_MODE, ChatTimeStyleSettings.DEFAULT_MODE)
    )

    private val timeFormatter = ChatTimeFormatter()

    private fun formatTime(timestamp: Long): String {
        val pattern = prefs.getString(
            ChatTimeStyleSettings.KEY_TIME_FORMAT,
            ChatTimeStyleSettings.DEFAULT_TIME_FORMAT
        ).orEmpty().ifBlank { ChatTimeStyleSettings.DEFAULT_TIME_FORMAT }
        return timeFormatter.format(timestamp, pattern, ChatTimeStyleSettings.DEFAULT_TIME_FORMAT)
    }

    private fun locateBindMethod(): Method? {
        val cacheKey = methodCacheKey()
        DexMethodCache.load(methodPrefs, cacheKey, context.hostClassLoader(), CACHE_BIND_METHOD)
            ?.takeIf(::isBindCandidate)
            ?.let { return it }
        val methods = runCatching {
            context.dexKitManager().withCurrentBridge { bridge ->
                bridge.findMethod(
                    FindMethod().apply {
                        matcher(
                            MethodMatcher().apply {
                                usingStrings("MicroMsg.MvvmChattingItem", "[onBindView]")
                            }
                        )
                    }
                ).mapNotNull { data ->
                    runCatching { data.getMethodInstance(context.hostClassLoader()) }.getOrNull()
                }
            }
        }.getOrElse {
            HLog.e("$TAG 定位聊天时间绑定方法异常: ${it.message}", it)
            emptyList()
        }
        val method = methods.firstOrNull(::isBindCandidate)
        if (method != null) {
            DexMethodCache.save(methodPrefs, cacheKey, CACHE_BIND_METHOD, method)
        } else {
            DexMethodCache.clear(methodPrefs, cacheKey, CACHE_BIND_METHOD)
        }
        return method
    }

    private fun isBindCandidate(method: Method): Boolean {
        val types = method.parameterTypes
        return method.returnType == Void.TYPE &&
            types.size >= 3 &&
            types.any { it == Integer.TYPE || it == java.lang.Integer::class.java } &&
            types.any(::isLikelyViewHolderClass)
    }

    private fun isLikelyViewHolderClass(clazz: Class<*>): Boolean {
        if (findRootField(clazz) != null) return true
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            if (KavaReflector.declaredFields(current).any { View::class.java.isAssignableFrom(it.type) }) {
                return true
            }
            current = current.superclass
        }
        return false
    }

    private fun messageHolder(args: Array<Any?>?): Any? {
        args ?: return null
        return args.getOrNull(0)?.takeIf { findRootView(it) != null }
            ?: args.firstOrNull { it != null && findRootView(it) != null }
    }

    private fun findRootView(holder: Any): View? {
        (KavaReflector.readField(holder, "itemView") as? View)?.let { return it }
        return KavaReflector.readField(findRootField(holder.javaClass), holder) as? View
    }

    private fun findRootField(clazz: Class<*>): Field? {
        rootFieldCache.get(clazz)?.let { return it }
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            val field = KavaReflector.declaredFields(current).firstOrNull {
                it.name == "itemView" || it.type == View::class.java
            }
            if (field != null) {
                rootFieldCache.put(clazz, field)
                return field
            }
            current = current.superclass
        }
        return null
    }

    private fun findTimeView(holder: Any): TextView? {
        timeFieldCache.get(holder.javaClass)?.let {
            return KavaReflector.readField(it, holder) as? TextView
        }
        if (unsupportedTimeHolders.contains(holder.javaClass)) return null
        var current: Class<*>? = holder.javaClass
        while (current != null && current != Any::class.java) {
            val field = KavaReflector.declaredFields(current).firstOrNull {
                it.name == "timeTV" && TextView::class.java.isAssignableFrom(it.type)
            }
            if (field != null) {
                timeFieldCache.put(holder.javaClass, field)
                return KavaReflector.readField(field, holder) as? TextView
            }
            current = current.superclass
        }
        unsupportedTimeHolders.add(holder.javaClass)
        return null
    }

    private fun resolveMessageCreateTime(source: Any?): Long? {
        return resolveMessageCreateTime(
            source,
            Collections.newSetFromMap(IdentityHashMap<Any, Boolean>()),
            0
        )
    }

    private fun resolveMessageCreateTime(source: Any?, visited: MutableSet<Any>, depth: Int): Long? {
        if (source == null || depth > 4 || !visited.add(source)) return null
        nativeMessageCreateTime(source)?.let { return it }
        if (source is Array<*>) {
            source.forEach { resolveMessageCreateTime(it, visited, depth + 1)?.let { result -> return result } }
            return null
        }
        if (source is Collection<*>) {
            source.forEach { resolveMessageCreateTime(it, visited, depth + 1)?.let { result -> return result } }
            return null
        }
        val className = source.javaClass.name
        if (className.startsWith("java.") || className.startsWith("android.") || source is View) return null
        var current: Class<*>? = source.javaClass
        while (current != null && current != Any::class.java) {
            for (field in resolutionNestedFields(current)) {
                val value = KavaReflector.readField(field, source) ?: continue
                resolveMessageCreateTime(value, visited, depth + 1)?.let { return it }
            }
            current = current.superclass
        }
        return null
    }

    // Reuse only within this resolution; do not cache mutable message objects.
    private fun nativeMessageCreateTime(value: Any): Long? {
        if (!value.javaClass.name.startsWith("com.tencent.mm.storage.")) return null
        val createTime = messageCreateTime(value)
        return createTime.takeIf { it > 0L && messageId(value) > 0L }
    }

    private fun messageCreateTime(message: Any): Long {
        var owner: Class<*>? = message.javaClass
        while (owner != null && owner != Any::class.java) {
            val method = resolutionMethod(owner, "getCreateTime")
            if (method != null) {
                parseLong(KavaReflector.invoke(method, message))?.let { if (it > 0L) return it }
            }
            owner = owner.superclass
        }
        for (name in arrayOf("field_createTime", "createTime")) {
            parseLong(readResolutionField(message, name))?.let { if (it > 0L) return it }
        }
        return 0L
    }

    private fun messageId(message: Any): Long {
        var owner: Class<*>? = message.javaClass
        while (owner != null && owner != Any::class.java) {
            for (name in arrayOf("getMsgId", "getMsgID")) {
                val method = resolutionMethod(owner, name)
                if (method != null) {
                    parseLong(KavaReflector.invoke(method, message))?.let { if (it > 0L) return it }
                }
            }
            owner = owner.superclass
        }
        for (name in arrayOf("field_msgId", "msgId", "msgID")) {
            parseLong(readResolutionField(message, name))?.let { if (it > 0L) return it }
        }
        return 0L
    }

    private fun metadata(clazz: Class<*>): ResolutionMetadata =
        resolutionMetadata.getOrPut(clazz) { ResolutionMetadata() }

    private fun resolutionNestedFields(clazz: Class<*>): List<Field> = synchronized(resolutionMetadata) {
        val entry = metadata(clazz)
        entry.nestedFields ?: KavaReflector.declaredFields(clazz).filter { field ->
            !KavaReflector.isStatic(field) && !field.type.isPrimitive && !field.type.isArray &&
                field.type != String::class.java && !Number::class.java.isAssignableFrom(field.type)
        }.also { entry.nestedFields = it }
    }

    private fun resolutionMethod(clazz: Class<*>, name: String): Method? = synchronized(resolutionMetadata) {
        val entry = metadata(clazz)
        if (!entry.methods.containsKey(name)) entry.methods[name] = KavaReflector.findMethod(clazz, name)
        entry.methods[name]
    }

    private fun resolutionField(clazz: Class<*>, name: String): Field? = synchronized(resolutionMetadata) {
        val entry = metadata(clazz)
        if (!entry.fields.containsKey(name)) entry.fields[name] = KavaReflector.findDeclaredField(clazz, name)
        entry.fields[name]
    }

    private fun readResolutionField(receiver: Any, name: String): Any? {
        var current: Class<*>? = receiver.javaClass
        while (current != null && current != Any::class.java) {
            val field = resolutionField(current, name)
            if (field != null) return KavaReflector.readField(field, receiver)
            current = current.superclass
        }
        return null
    }

    private fun parseLong(value: Any?): Long? = when (value) {
        is Number -> value.toLong()
        is String -> value.trim().toLongOrNull()
        else -> null
    }

    private fun methodCacheKey(): String {
        return DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader())
            .takeIf { it.isNotBlank() }
            ?.let { "$it|$CACHE_SCHEMA" }
            .orEmpty()
    }

    private companion object {
        const val TAG = "[Hchat:ChatTimeStyle]"
        const val METHOD_PREFS_NAME = "Hchat_chat_time_style_method_cache"
        const val CACHE_SCHEMA = "chat_time_style_v1"
        const val CACHE_BIND_METHOD = "chat_time_bind"
        const val FIELD_CACHE_LIMIT = 128
        const val RESTORE_RETRY_LIMIT = 3
    }
}

/** 反射缓存有界：容量满即淘汰最久未用的类，关闭时整体清空。 */
internal class BoundedLruMap<K, V>(private val limit: Int) {
    private val entries = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > limit
    }

    @Synchronized fun get(key: K): V? = entries[key]

    @Synchronized fun put(key: K, value: V) {
        entries[key] = value
    }

    @Synchronized fun clear() {
        entries.clear()
    }
}

/** 不支持持有类的记录同样有界，避免随会话增长。 */
internal class BoundedLruSet<E>(private val limit: Int) {
    private val entries = object : LinkedHashMap<E, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<E, Boolean>?): Boolean = size > limit
    }

    @Synchronized fun contains(value: E): Boolean = entries.containsKey(value)

    @Synchronized fun add(value: E) {
        entries[value] = true
    }

    @Synchronized fun clear() {
        entries.clear()
    }
}
