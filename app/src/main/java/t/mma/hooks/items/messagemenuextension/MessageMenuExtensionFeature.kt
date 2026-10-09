package t.mma.hooks.items.messagemenuextension

import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import de.robv.android.xposed.XC_MethodHook
import t.mma.event.Events
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.utils.HLog
import t.mma.utils.KavaReflector
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Diagnostic stage: never replace native creation, dispatch or popup placement. */
class MessageMenuExtensionFeature : BaseFeature() {
    private var runtime: MessageMenuExtensionRuntime? = null
    override fun featureId() = MessageMenuExtensionSettings.ID
    override fun name() = "消息菜单扩展"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(MessageMenuExtensionSettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        runtime = MessageMenuExtensionRuntime(context)
        scheduleInstall()
        subscribe(Events.DexReady::class.java) { scheduleInstall() }
    }
    private fun scheduleInstall() {
        DexInstallScheduler.schedule(featureId(), name()) { runtime?.install() == true }
    }
    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }
}

private class MessageMenuExtensionRuntime(private val context: FeatureContext) {
    private val prefs = MessageMenuExtensionSettings.preferences(context.hostContext())
    private val sequence = AtomicInteger()
    private val currentSession = ThreadLocal<Int>()
    private val constructedViews = Collections.synchronizedMap(WeakHashMap<View, Int>())
    private val constructedItems = Collections.synchronizedMap(WeakHashMap<Any, Int>())
    private val unhooks = mutableListOf<XC_MethodHook.Unhook>()
    private var installed = false
    @Volatile private var active = true
    private var version = "unknown"

    @Synchronized
    fun install(): Boolean {
        if (installed) return true
        if (!active) return false
        try {
            val host = context.hostContext()
            val info = host.packageManager.getPackageInfo(host.packageName, 0)
            @Suppress("DEPRECATION")
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            version = "${info.versionName}/$versionCode"
            // Only this version has user-supplied evidence. No guessed cross-version fallback.
            if (info.versionName != "8.0.78") {
                HLog.e("$TAG stage=versionGate version=$version unsupported; native retained")
                return false
            }
            val clazz = KavaReflector.loadClass("eu5.s0", context.hostClassLoader())
                ?: return missing("eu5.s0")
            val method = KavaReflector.findDeclaredMethod(clazz, "p", Integer.TYPE, Integer.TYPE)
                ?: return missing("eu5.s0.p(int,int)")
            if (Modifier.isAbstract(method.modifiers) || Modifier.isStatic(method.modifiers)) {
                return missing("executable instance method: ${method.toGenericString()}")
            }
            installItemDiagnostics()
            unhooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!enabled()) return
                    val session = sequence.incrementAndGet()
                    param.setObjectExtra(EXTRA_SESSION, session)
                    param.setObjectExtra(EXTRA_PREVIOUS, currentSession.get())
                    currentSession.set(session)
                    guarded("before", session) {
                        log(session, "showEnter target=${method.toGenericString()} requested=6x5 nativeUnmodified=true")
                        inspectObject(param.thisObject, session, "menu.before")
                    }
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    val session = param.getObjectExtra(EXTRA_SESSION) as? Int ?: return
                    currentSession.set(param.getObjectExtra(EXTRA_PREVIOUS) as? Int)
                    guarded("after", session) {
                        if (param.hasThrowable()) {
                            HLog.e("$TAG version=$version session=$session stage=nativeShow", param.throwable)
                        }
                        inspectObject(param.thisObject, session, "menu.after")
                        val views = synchronized(constructedViews) {
                            constructedViews.entries.filter { it.value == session }.map { it.key }
                        }
                        log(session, "showExit createdViews=${views.size} attached=${views.count { it.isAttachedToWindow }} parented=${views.count { it.parent != null }} capacityExpansion=NOT_IMPLEMENTED")
                        // Observe the next frame, without changing the menu or retaining its owner.
                        views.firstOrNull { it.isAttachedToWindow }?.postOnAnimation {
                            if (enabled()) guarded("nextFrame", session) {
                                log(session, "nextFrame createdViews=${views.size} attached=${views.count { it.isAttachedToWindow }} parented=${views.count { it.parent != null }}")
                            }
                        }
                    }
                }
            })
            installed = true
            HLog.i("$TAG stage=install version=$version target=${method.toGenericString()} mode=diagnostic enabled=${enabled()}")
            return true
        } catch (error: Throwable) {
            unhooks.forEach { HookRegistry.get().unhook(it) }
            unhooks.clear()
            HLog.e("$TAG stage=install version=$version", error)
            return false
        }
    }

    private fun installItemDiagnostics() {
        val clazz = KavaReflector.loadClass("eu5.l0", context.hostClassLoader())
        if (clazz == null) {
            HLog.e("$TAG stage=itemClass version=$version eu5.l0 missing; show diagnostics still available")
            return
        }
        val constructors = KavaReflector.declaredConstructors(clazz)
        val methods = KavaReflector.declaredMethods(clazz)
        HLog.i("$TAG stage=itemSignatures version=$version constructors=${constructors.joinToString { it.toGenericString() }} methods=${methods.joinToString { it.toGenericString() }}")
        constructors.forEach { constructor ->
            unhooks += HookRegistry.get().hook(constructor, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val session = currentSession.get() ?: return
                    if (!enabled()) return
                    guarded("itemConstructor", session) {
                        val item = param.thisObject ?: return@guarded
                        constructedItems[item] = session
                        val view = item as? View
                        if (view != null) constructedViews[view] = session
                        KavaReflector.declaredFields(item.javaClass).filterNot { Modifier.isStatic(it.modifiers) }.take(20).forEach { field ->
                            val value = KavaReflector.readField(field, item)
                            if (value is View) constructedViews[value] = session
                            log(session, "itemField name=${field.name}:${field.type.name} value=${shape(value)}")
                        }
                        log(session, "itemCreate target=${constructor.toGenericString()} args=${param.args.joinToString { shape(it) }} view=${view?.let { viewShape(it) }}")
                    }
                }
            })
        }
        // Signature-based observation only; never call or replace an unverified click callback.
        methods.filter { method ->
            !Modifier.isAbstract(method.modifiers) && !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.contentEquals(arrayOf(View::class.java))
        }.forEach { method ->
            unhooks += HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!enabled()) return
                    val receiver = param.thisObject ?: return
                    val session = constructedItems[receiver] ?: return
                    guarded("itemViewCallback", session) {
                        log(session, "itemViewCallback target=${method.toGenericString()} receiver=${shape(receiver)}")
                    }
                }
            })
        }
    }

    private fun inspectObject(owner: Any?, session: Int, stage: String) {
        if (owner == null) return
        val roots = Collections.newSetFromMap(java.util.IdentityHashMap<View, Boolean>())
        var clazz: Class<*>? = owner.javaClass
        var fieldsLeft = 60
        while (clazz != null && clazz != Any::class.java && fieldsLeft > 0) {
            for (field in KavaReflector.declaredFields(clazz)) {
                if (Modifier.isStatic(field.modifiers)) continue
                if (fieldsLeft-- <= 0) break
                val value = KavaReflector.readField(field, owner)
                log(session, "$stage field=${field.declaringClass.name}.${field.name}:${field.type.name} value=${shape(value)}")
                when (value) {
                    is View -> roots.add(value)
                    is PopupWindow -> value.contentView?.let { roots.add(it) }
                }
                if (field.name in setOf("V", "J", "t") && value != null) {
                    log(session, "$stage callback=${field.name} methods=${KavaReflector.declaredMethods(value.javaClass).take(20).joinToString { it.toGenericString() }}")
                }
            }
            clazz = clazz.superclass
        }
        val visited = Collections.newSetFromMap(java.util.IdentityHashMap<View, Boolean>())
        fun walk(view: View, depth: Int) {
            if (visited.size >= 100 || depth > 10 || !visited.add(view)) return
            log(session, "$stage tree depth=$depth ${viewShape(view)}")
            if (view is ViewGroup) for (index in 0 until view.childCount) walk(view.getChildAt(index), depth + 1)
        }
        roots.forEach { walk(it, 0) }
        log(session, "$stage snapshot roots=${roots.size} nodes=${visited.size} bounded=100")
    }

    // No titles, message text, coordinates, arguments' toString or business object dumps.
    private fun shape(value: Any?): String = when (value) {
        null -> "null"
        is View -> viewShape(value)
        is PopupWindow -> "${value.javaClass.name} width=${value.width} height=${value.height} showing=${value.isShowing}"
        is Collection<*> -> "${value.javaClass.name} size=${value.size} types=${value.take(8).map { it?.javaClass?.name }.distinct()}"
        is Boolean, is Number -> "$value"
        else -> value.javaClass.name
    }
    private fun viewShape(view: View): String =
        "${view.javaClass.name}@${System.identityHashCode(view)} id=0x${Integer.toHexString(view.id)} size=${view.width}x${view.height} measured=${view.measuredWidth}x${view.measuredHeight} visibility=${view.visibility} attached=${view.isAttachedToWindow} parent=${view.parent?.javaClass?.name} children=${(view as? ViewGroup)?.childCount ?: 0}"
    private fun enabled() = active && prefs.getBoolean(MessageMenuExtensionSettings.KEY_ENABLE, false)
    private fun log(session: Int, message: String) = HLog.i("$TAG version=$version session=$session $message")
    private fun missing(target: String): Boolean {
        HLog.e("$TAG stage=locate version=$version missing=$target native retained")
        return false
    }
    private inline fun guarded(stage: String, session: Int, block: () -> Unit) {
        try { block() } catch (error: Throwable) {
            HLog.e("$TAG version=$version session=$session stage=$stage", error)
        }
    }
    @Synchronized
    fun destroy() {
        active = false
        unhooks.forEach { HookRegistry.get().unhook(it) }
        unhooks.clear()
        constructedViews.clear()
        constructedItems.clear()
        currentSession.remove()
        installed = false
    }
    companion object {
        private const val TAG = "[Hchat:消息菜单扩展诊断]"
        private const val EXTRA_SESSION = "hchat.menuExtension.session"
        private const val EXTRA_PREVIOUS = "hchat.menuExtension.previous"
    }
}
