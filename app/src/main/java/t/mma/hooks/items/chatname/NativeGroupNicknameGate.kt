package t.mma.hooks.items.chatname

import android.view.View
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.FieldMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.FieldUsingType
import org.luckypray.dexkit.wrap.DexField
import t.mma.dexkit.DexMethodCache
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.utils.KavaReflector
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/** Native ChatroomComponent flag, not userTV visibility (empty names are also GONE). */
internal class NativeGroupNicknameGate private constructor(
    private val flag: Field,
    private val componentGetter: Method,
    private val componentInterfaces: List<Class<*>>
) {
    private data class Binding(val component: WeakReference<Any>, val room: String, val sender: String)
    private val bindings = Collections.synchronizedMap(WeakHashMap<TextView, Binding>())
    private var refreshHook: XC_MethodHook.Unhook? = null
    private var owners = 0

    fun bind(view: TextView, chat: Any?, room: String, sender: String): Boolean {
        val component = chat?.let { context ->
            componentInterfaces.firstNotNullOfOrNull { api ->
                KavaReflector.invoke(componentGetter, context, api)?.takeIf { flag.declaringClass.isInstance(it) }
            }
        }
        if (component == null) {
            bindings.remove(view)
            suppress(view)
            return false
        }
        bindings[view] = Binding(WeakReference(component), room, sender)
        return allows(view, room, sender)
    }

    fun allows(view: TextView, room: String? = null, sender: String? = null): Boolean {
        val binding = bindings[view]
        val allowed = binding != null && (room == null || binding.room == room) &&
            (sender == null || binding.sender == sender) &&
            binding.component.get()?.let { KavaReflector.readField(flag, it) == true } == true
        if (!allowed && binding != null && (room == null || binding.room == room) &&
            (sender == null || binding.sender == sender)) suppress(view)
        return allowed
    }

    fun forget(view: TextView) { bindings.remove(view) }

    private fun onNativeRefresh(component: Any?) {
        if (component == null || KavaReflector.readField(flag, component) == true) return
        val views = synchronized(bindings) {
            bindings.entries.filter { it.value.component.get() === component }.map { it.key }
        }
        // Refresh runs in native UI lifecycle. Post only when the host calls off-main.
        views.forEach { view ->
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) allows(view)
            else view.post { allows(view) }
        }
    }

    fun release() = synchronized(Companion) {
        if (owners <= 0) return@synchronized
        owners--
        if (owners == 0) {
            HookRegistry.get().unhook(refreshHook)
            refreshHook = null
            bindings.clear()
            if (shared === this) shared = null
        }
    }

    companion object {
        private var shared: NativeGroupNicknameGate? = null
        @Synchronized
        fun acquire(context: FeatureContext, binder: Method, logger: (String, Throwable?) -> Unit): NativeGroupNicknameGate? {
            shared?.let { it.owners++; return it }
            return runCatching {
                val loader = context.hostClassLoader()
                val prefs = DexMethodCache.prefs(context.hostContext(), "Hchat_native_group_nickname_cache")
                val key = DexMethodCache.runtimeKey(context.hostContext(), loader)
                var refresh = DexMethodCache.load(prefs, key, loader, "refresh")
                var field = if (refresh != null) runCatching {
                    DexField(prefs.getString("flag", "").orEmpty()).getFieldInstance(loader)
                }.getOrNull() else null
                if (refresh == null || field == null || field.type != Boolean::class.javaPrimitiveType ||
                    refresh.declaringClass != field.declaringClass || refresh.parameterCount != 0 || refresh.returnType != Void.TYPE) {
                    val located = context.dexKitManager().withCurrentBridge { bridge ->
                        val nativeFields = bridge.getMethodData(binder)?.usingFields.orEmpty()
                            .mapNotNull { it.field }.filter { it.typeName == "boolean" }.map { it.descriptor }.toSet()
                        val candidates = bridge.findMethod(FindMethod().apply {
                            matcher(MethodMatcher().apply {
                                paramCount(0)
                                returnType("void")
                                addUsingField(FieldMatcher().apply { name("field_isShowname"); type("int") })
                            })
                        }).mapNotNull { method ->
                            if (!method.className.startsWith("com.tencent.mm.ui.chatting.component.")) return@mapNotNull null
                            val flags = method.usingFields.filter { it.usingType == FieldUsingType.Write }
                                .mapNotNull { it.field }.filter { it.descriptor in nativeFields && it.className == method.className }
                                .distinctBy { it.descriptor }
                            if (flags.size != 1) null else method.getMethodInstance(loader) to flags.single().getFieldInstance(loader)
                        }
                        candidates.singleOrNull()
                    } ?: error("原生 isShowname 刷新入口不唯一或缺失")
                    refresh = located.first
                    field = located.second
                    DexMethodCache.save(prefs, key, "refresh", refresh)
                    prefs.edit().putString("flag", DexField(field!!).toString()).apply()
                }
                val nativeFlag = requireNotNull(field)
                val nativeRefresh = requireNotNull(refresh)
                val chatClass = binder.parameterTypes[1]
                val getter = KavaReflector.declaredMethods(chatClass).singleOrNull {
                    it.parameterTypes.contentEquals(arrayOf(Class::class.java)) && it.returnType != Void.TYPE
                } ?: error("原生 ChattingContext 组件读取入口不唯一")
                val gate = NativeGroupNicknameGate(nativeFlag, getter, nativeFlag.declaringClass.interfaces.filter { getter.returnType.isAssignableFrom(it) })
                gate.refreshHook = HookRegistry.get().hook(nativeRefresh, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) { gate.onNativeRefresh(param.thisObject) }
                })
                gate.owners = 1
                shared = gate
                gate
            }.onFailure { logger("原生群昵称开关定位失败，停止头衔和尾字渲染", it) }.getOrNull()
        }

        private fun suppress(view: TextView) {
            ChatNameDecorationLayout.suppressNativeName(view)
            if (view.visibility != View.GONE) view.visibility = View.GONE
        }
    }
}
