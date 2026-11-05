package t.mma.hooks.items.hideavatar

import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import t.mma.dexkit.DexMethodCache
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.utils.HLog
import t.mma.utils.KavaReflector
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** One native avatar hook shared by hiding and avatar mention decorations. */
internal object ChatAvatarBinding {
    data class Binding(val avatar: View, val mask: View?, val row: ViewGroup?,
        val message: Any, val username: String?)
    private var context: FeatureContext? = null
    private var unhook: XC_MethodHook.Unhook? = null
    private val listeners = LinkedHashMap<Any, (Binding) -> Unit>()
    @Volatile private var callbacks: List<(Binding) -> Unit> = emptyList()
    private val holderFields = ConcurrentHashMap<Class<*>, AvatarFields>()
    private val unsupportedHolderClasses = ConcurrentHashMap.newKeySet<Class<*>>()
    private val methodPrefs get() = DexMethodCache.prefs(context!!.hostContext(), "Hchat_chat_avatar_method_cache")

    @Synchronized fun subscribe(featureContext: FeatureContext, owner: Any, callback: (Binding) -> Unit): Boolean {
        if (listeners.containsKey(owner)) return true
        context = featureContext
        if (unhook == null) {
            val method = locateAvatarBindMethod() ?: return false
            unhook = HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val subscribers = callbacks
                    if (subscribers.isEmpty()) return
                    try {
                        val args = param.args ?: return
                        val holder = args.firstOrNull() ?: return
                        val fields = avatarFields(holder.javaClass) ?: return
                        val avatar = KavaReflector.readField(fields.avatar, holder) as? View ?: return
                        val mask = fields.mask?.let { KavaReflector.readField(it, holder) as? View }
                        val row = fields.row?.let { KavaReflector.readField(it, holder) as? ViewGroup }
                        val message = args.firstOrNull { it != null && isMessageClass(it.javaClass) } ?: return
                        val binding = Binding(avatar, mask, row, message, args.getOrNull(2) as? String)
                        subscribers.forEach { listener ->
                            try { listener(binding) } catch (error: Throwable) { HLog.e("聊天头像订阅处理失败", error) }
                        }
                    } catch (error: Throwable) { HLog.e("聊天头像绑定解析失败", error) }
                }
            }) ?: return false
        }
        listeners[owner] = callback
        callbacks = listeners.values.toList()
        return true
    }

    @Synchronized fun unsubscribe(owner: Any) {
        listeners.remove(owner)
        callbacks = listeners.values.toList()
        if (listeners.isEmpty()) {
            val handle = unhook
            unhook = null
            context = null
            holderFields.clear()
            unsupportedHolderClasses.clear()
            handle?.let { HookRegistry.get().unhook(it) }
        }
    }
    private fun locateAvatarBindMethod(): Method? {
        val cacheKey = methodCacheKey()
        DexMethodCache.load(methodPrefs, cacheKey, context!!.hostClassLoader(), CACHE_AVATAR_BIND)
            ?.takeIf { isAvatarBindCandidate(it) }
            ?.let { return it }
        val candidates = findMethodsByStrings(
            "MicroMsg.ChattingItem",
            "attachAvatarClickListener: getBizKfWorker:%s"
        ).filter { isAvatarBindCandidate(it) }
        val method = candidates.singleOrNull()
        if (candidates.size > 1) HLog.e("$TAG 聊天头像入口不唯一: ${candidates.map { it.toGenericString() }}")
        if (method != null) {
            DexMethodCache.save(methodPrefs, cacheKey, CACHE_AVATAR_BIND, method)
        } else {
            DexMethodCache.clear(methodPrefs, cacheKey, CACHE_AVATAR_BIND)
        }
        return method
    }

    private fun findMethodsByStrings(vararg strings: String): List<Method> {
        return runCatching {
            context!!.dexKitManager().withCurrentBridge { bridge ->
                bridge.findMethod(
                    FindMethod().apply {
                        matcher(MethodMatcher().apply { usingStrings(strings.toList()) })
                    }
                ).mapNotNull { data ->
                    runCatching { data.getMethodInstance(context!!.hostClassLoader()) }.getOrNull()
                }
            }
        }.getOrElse {
            HLog.e("$TAG 定位聊天头像绑定方法异常: ${it.message}", it)
            emptyList()
        }
    }

    private fun isAvatarBindCandidate(method: Method): Boolean {
        val types = method.parameterTypes
        return method.returnType == Void.TYPE &&
            types.size == 4 &&
            types[2] == String::class.java &&
            avatarFields(types[0]) != null &&
            types.any(::isMessageClass)
    }

    private fun isMessageClass(clazz: Class<*>): Boolean {
        return clazz.name.startsWith("com.tencent.mm.storage.")
    }

    private fun avatarFields(clazz: Class<*>): AvatarFields? {
        holderFields[clazz]?.let { return it }
        if (unsupportedHolderClasses.contains(clazz)) return null
        var current: Class<*>? = clazz
        var avatar: Field? = null
        var mask: Field? = null
        var row: Field? = null
        while (current != null && current != Any::class.java) {
            for (field in KavaReflector.declaredFields(current)) {
                when {
                    row == null && field.name == "convertView" && View::class.java.isAssignableFrom(field.type) -> row = field
                    avatar == null && (field.name == "avatarIV" || field.type.name.endsWith(".ChattingAvatarImageView")) -> {
                        avatar = field
                    }
                    mask == null && (field.name == "avatarMask" || field.type.name == "com.tencent.mm.ui.base.MaskLayout") -> {
                        mask = field
                    }
                }
            }
            current = current.superclass
        }
        val result = avatar?.let { AvatarFields(it, mask, row) }
        if (result == null) {
            unsupportedHolderClasses += clazz
        } else {
            holderFields[clazz] = result
        }
        return result
    }

    private fun methodCacheKey(): String = DexMethodCache.runtimeKey(context!!.hostContext(), context!!.hostClassLoader())
        .takeIf { it.isNotBlank() }?.let { "$it|chat_avatar_v1" }.orEmpty()
    private data class AvatarFields(val avatar: Field, val mask: Field?, val row: Field?)
    private const val TAG = "[Hchat:ChatAvatar]"
    private const val CACHE_AVATAR_BIND = "avatar_bind"
}
