package t.mma.hooks.items.hchatextra

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import t.mma.dexkit.DexMethodCache
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.OwnedHandler
import t.mma.hooks.core.OwnedHooks
import t.mma.utils.KavaReflector
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.wrap.DexField
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

/** 合并转发详情的 dataitem 类型与聊天消息 rawType 是不同枚举，不能互相代入。 */
internal class ForwardedRecordTypeLabels(
    private val context: FeatureContext,
    private val enabled: () -> Boolean,
    private val styleLabel: (TextView, String) -> Unit,
    private val logger: (String, Throwable?) -> Unit
) {
    private data class Targets(val bind: Method, val detail: Class<*>, val dataType: Field)
    private data class RowState(val label: WeakReference<TextView>, var type: Int? = null)

    private val main = OwnedHandler({ error -> logger("聊天记录类型投递失败", error) })
    /** 摘除投递专用：main 在 destroy 时先关闭，这一步必须走不受本代关闭影响的通道。 */
    private val detachHandler = Handler(Looper.getMainLooper())
    /**
     * 本代独占的 Hook 句柄。HookRegistry 只做进程级兜底，不能证明单个功能关闭时已释放；
     * 句柄与主线程投递都必须由本类自己持有，destroy 时能证明全部撤销。
     */
    private val ownedHooks = OwnedHooks { logger("聊天记录类型Hook句柄异常", it) }
    // Values must not strongly retain a descendant of the weak row key.
    private val rows = WeakHashMap<View, RowState>()
    private val adapters = WeakHashMap<BaseAdapter, Boolean>()
    private var installed = false
    @Volatile private var destroyed = false
    private var lastFailure = 0L

    /** Called only inside the common DexInstallScheduler BRIDGE serial gate. */
    @Synchronized
    fun install(): Boolean {
        if (destroyed) return false
        if (installed) return true
        return try {
            val targets = locate() ?: return false
            // 句柄登记到 OwnedHooks：安装失败由它上报并返回 null，交由票据做有限重试。
            val handle = ownedHooks.hook(targets.bind, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (destroyed || Looper.myLooper() != Looper.getMainLooper()) return
                    (param.args.getOrNull(1) as? View)?.let(::clearRow)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    if (destroyed || param.hasThrowable() || Looper.myLooper() != Looper.getMainLooper()) return
                    val row = param.result as? View ?: return
                    clearRow(row)
                    val activity = activity(row.context) ?: return
                    if (!targets.detail.isInstance(activity)) return
                    val adapter = param.thisObject as? BaseAdapter ?: return
                    adapters[adapter] = true
                    if (!enabled()) return
                    try {
                        val position = param.args.getOrNull(0) as? Int ?: return
                        // BaseAdapter.getItem is the verified bridge to the same dataitem used by getView.
                        val item = adapter.getItem(position) ?: return
                        if (!targets.dataType.declaringClass.isInstance(item)) return
                        val type = KavaReflector.readField(targets.dataType, item) as? Int
                            ?: throw IllegalStateException("record datatype read failed: ${targets.dataType}")
                        bindLabel(row, type)
                    } catch (error: Throwable) {
                        logFailure("聊天记录类型绑定失败: ${targets.bind}", error)
                    }
                }
            })
            // 安装失败返回 false，交回票据做有限重试；不把失败态记成已安装。
            installed = handle != null
            installed
        } catch (error: Throwable) {
            logger("聊天记录类型Hook安装失败", error)
            false
        }
    }

    fun refresh() {
        // 投递被拒（本代已关闭或队列拒绝）时只是这一次刷新丢了，登记表本身不受污染。
        if (!main.post {
                if (destroyed) return@post
                try {
                    rows.values.forEach { state ->
                        state.label.get()?.let { label ->
                            if (!enabled() || state.type == null) label.visibility = View.GONE
                            else applyStyle(label, MessageTypeLabels.recordLabel(state.type!!))
                        }
                    }
                    // Rebind visible rows when enabling after their first bind ran with the option off.
                    adapters.keys.toList().forEach { it.notifyDataSetChanged() }
                } catch (error: Throwable) {
                    logFailure("聊天记录类型刷新失败", error)
                }
            }) {
            logFailure("聊天记录类型刷新投递被拒", IllegalStateException("main queue rejected refresh"))
        }
    }

    @Synchronized
    fun destroy() {
        // 先置门：此后 Hook 回调与安装一律拒绝，不能复活本代。
        if (destroyed) return
        destroyed = true
        // 句柄按注册逆序卸载，单项失败只上报。
        ownedHooks.close()
        // 撤销本代全部未执行投递；此后本类不再接受新投递。
        main.close()
        val labels = rows.values.mapNotNull { it.label.get() }
        rows.clear()
        adapters.clear()
        if (labels.isEmpty()) return
        // label 是本类挂进宿主视图的，必须摘除；不在主线程时投递一次，被拒说明主队列已停，
        // 宿主视图随之销毁，label 不会独立存活，只上报不重试。
        val detach = Runnable {
            labels.forEach { label -> (label.parent as? ViewGroup)?.removeView(label) }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            detach.run()
        } else if (!detachHandler.post(detach)) {
            logger("聊天记录类型摘除投递被拒", null)
        }
    }

    private fun clearRow(row: View) {
        rows[row]?.let { state ->
            state.type = null
            state.label.get()?.apply { text = ""; visibility = View.GONE }
        }
    }

    private fun bindLabel(row: View, type: Int) {
        val column = contentColumn(row) ?: return
        val existing = rows[row]
        val label = existing?.label?.get()?.takeIf { it.parent === column } ?: TextView(row.context).also {
            existing?.label?.get()?.let { old -> (old.parent as? ViewGroup)?.removeView(old) }
            // All six verified wrappers share a vertical body ending in a 1 px separator.
            // Insert above that separator, without wrapping/reparenting or changing host tags/clicks.
            val separator = column.getChildAt(column.childCount - 1)
            val index = if (separator is ImageView && separator.layoutParams?.height == 1) {
                column.childCount - 1
            } else column.childCount
            column.addView(it, index, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            rows[row] = RowState(WeakReference(it))
        }
        rows[row]?.type = type
        applyStyle(label, MessageTypeLabels.recordLabel(type))
    }

    private fun applyStyle(label: TextView, text: String) {
        styleLabel(label, text)
        label.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        label.isClickable = false
        label.isLongClickable = false
        label.isFocusable = false
        label.visibility = View.VISIBLE
    }

    private fun contentColumn(row: View): LinearLayout? {
        val root = row as? LinearLayout ?: return null
        if (root.orientation != LinearLayout.HORIZONTAL) return null
        // Structural validation, rather than version-specific obfuscated IDs or fixed child indices.
        val columns = (0 until root.childCount).mapNotNull { root.getChildAt(it) as? LinearLayout }
            .filter { it.orientation == LinearLayout.VERTICAL }
        return columns.singleOrNull()?.takeIf { it.childCount >= 2 }
    }

    private fun activity(context: Context): Activity? {
        var current = context
        repeat(12) {
            if (current is Activity) return current
            val next = (current as? ContextWrapper)?.baseContext ?: return null
            if (next === current) return null
            current = next
        }
        return null
    }

    private fun locate(): Targets? {
        val loader = context.hostClassLoader()
        val key = DexMethodCache.runtimeKey(context.hostContext(), loader)
        val prefs = DexMethodCache.prefs(context.hostContext(), "Hchat_forwarded_record_type_v1")
        val bind = DexMethodCache.load(prefs, key, loader, "bind")
        val detail = DexMethodCache.load(prefs, key, loader, "detail")
        val cachedType = prefs.getString("datatype", null)?.let { descriptor ->
            runCatching {
                val field = DexField(descriptor).getFieldInstance(loader)
                KavaReflector.findDeclaredField(field.declaringClass, field.name)
            }.getOrNull()
        }
        if (bind != null && detail != null && cachedType?.type == Integer.TYPE && validBind(bind) &&
            Activity::class.java.isAssignableFrom(detail.declaringClass)) {
            return Targets(bind, detail.declaringClass, cachedType)
        }
        // Never retain a partially parsed descriptor bundle after cache validation fails.
        DexMethodCache.clear(prefs, key, "bind")
        DexMethodCache.clear(prefs, key, "detail")
        prefs.edit().remove("datatype").apply()
        return context.dexKitManager().withCurrentBridge { bridge ->
            locateFresh(bridge, loader, key, prefs)
        }
    }
    private fun locateFresh(
        bridge: org.luckypray.dexkit.DexKitBridge,
        loader: ClassLoader,
        key: String,
        prefs: android.content.SharedPreferences
    ): Targets? {
        val bindData = bridge.findMethod(FindMethod().apply {
            matcher(MethodMatcher().apply {
                usingStrings(listOf("com/tencent/mm/plugin/record/ui/RecordMsgBaseAdapter"))
            })
        }).filter { it.methodName == "getView" }.singleOrNull() ?: return null
        val bindMethod = bindData.getMethodInstance(loader).takeIf(::validBind) ?: return null
        val detailData = bridge.findMethod(FindMethod().apply {
            matcher(MethodMatcher().apply { usingStrings(listOf("record_nest", "record_show_share")) })
        }).filter { it.paramCount == 0 && it.returnTypeName == "void" }.singleOrNull() ?: return null
        val detailMethod = detailData.getMethodInstance(loader)
        if (!Activity::class.java.isAssignableFrom(detailMethod.declaringClass)) return null
        val viewType = KavaReflector.findDeclaredMethod(bindMethod.declaringClass, "getItemViewType", Integer.TYPE)
            ?: return null
        // getItemViewType reads exactly one int: dataitem.datatype; getView reads the same field.
        val typeFieldData = bridge.getMethodData(viewType)?.usingFields.orEmpty()
            .map { it.field }.distinctBy { it.descriptor }
            .filter { it.typeName == "int" && it.declaredClassName != bindData.declaredClassName }
            .singleOrNull() ?: return null
        if (bindData.usingFields.none { it.field.descriptor == typeFieldData.descriptor }) return null
        val field = typeFieldData.getFieldInstance(loader)
        val accessibleField = KavaReflector.findDeclaredField(field.declaringClass, field.name) ?: return null
        DexMethodCache.save(prefs, key, "bind", bindMethod)
        DexMethodCache.save(prefs, key, "detail", detailMethod)
        prefs.edit().putString("datatype", typeFieldData.descriptor).apply()
        return Targets(bindMethod, detailMethod.declaringClass, accessibleField)
    }

    private fun validBind(method: Method): Boolean = !KavaReflector.isAbstract(method) &&
        BaseAdapter::class.java.isAssignableFrom(method.declaringClass) &&
        method.returnType == View::class.java && method.parameterTypes.contentEquals(
            arrayOf(Integer.TYPE, View::class.java, ViewGroup::class.java)
        )

    private fun logFailure(message: String, error: Throwable) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastFailure < 10_000L && lastFailure != 0L) return
        lastFailure = now
        logger(message, error)
    }
}
