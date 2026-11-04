package t.mma.hooks.items.messagemenuextension

import android.graphics.Rect
import android.os.Looper
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import de.robv.android.xposed.XC_MethodHook
import t.mma.hooks.api.message.SingleMessageMenuLocator
import t.mma.hooks.core.FeatureContext
import t.mma.hooks.core.HookRegistry
import t.mma.utils.HLog
import t.mma.utils.KavaReflector
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.IdentityHashMap

/** 8.0.78/3180 only. Reuse native item Views and native click dispatch. */
internal class MessageMenuGridRuntime(private val context: FeatureContext) {
    private class Frame(val owner: Any, val previous: Frame?) {
        val views = IdentityHashMap<MenuItem, View>()
        val originals = IdentityHashMap<Any, Any?>()
        var changed = false
        var originalFlag: Any? = null
        var originalHeight = 0
        var originalWidth = 0
        var undo: (() -> Unit)? = null
    }
    private val prefs = MessageMenuExtensionSettings.preferences(context.hostContext())
    private val frames = ThreadLocal<Frame>()
    private val hooks = mutableListOf<XC_MethodHook.Unhook>()
    private lateinit var binding: Field
    private lateinit var model: Field
    private lateinit var popup: Field
    private lateinit var anchorField: Field
    private lateinit var flag: Field
    private lateinit var bindMethod: Method
    private var installed = false
    @Volatile private var active = true

    @Synchronized fun install(): Boolean {
        if (installed) return true
        if (!active) return false
        if (!prefs.getBoolean(MessageMenuExtensionSettings.KEY_ENABLE, false)) return true
        try {
            val host = context.hostContext()
            val info = host.packageManager.getPackageInfo(host.packageName, 0)
            @Suppress("DEPRECATION")
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            check(info.versionName == "8.0.78" && code == 3180L) { "Unsupported version ${info.versionName}/$code" }
            val owner = requireNotNull(KavaReflector.loadClass("eu5.s0", context.hostClassLoader()))
            binding = requireNotNull(KavaReflector.findDeclaredField(owner, "t"))
            model = requireNotNull(KavaReflector.findDeclaredField(owner, "x"))
            popup = requireNotNull(KavaReflector.findDeclaredField(owner, "m"))
            anchorField = requireNotNull(KavaReflector.findDeclaredField(owner, "f"))
            flag = requireNotNull(KavaReflector.findDeclaredField(owner, "F"))
            check(binding.type.isInterface && flag.type == java.lang.Boolean.TYPE)
            bindMethod = KavaReflector.declaredMethods(binding.type).single {
                it.returnType == Void.TYPE && it.parameterTypes.contentEquals(arrayOf(View::class.java, Integer.TYPE, MenuItem::class.java))
            }
            val show = requireNotNull(KavaReflector.findDeclaredMethod(owner, "p", Integer.TYPE, Integer.TYPE))
            check(show.returnType == java.lang.Boolean.TYPE)
            val create = SingleMessageMenuLocator.menuCreateMethods(context) { message, error -> HLog.e("$TAG $message", error) }
            check(create.isNotEmpty()) { "Menu creation callback unavailable" }
            create.forEach { method ->
                hook(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val frame = frames.get() ?: return
                        if (param.hasThrowable() || param.args.getOrNull(0) !== KavaReflector.readField(model, frame.owner)) return
                        attempt("captureAfterCreate") { wrapBinding(frame) }
                    }
                })
            }
            hook(requireNotNull(KavaReflector.findDeclaredMethod(PopupWindow::class.java, "setContentView", View::class.java)), object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val frame = target(param.thisObject) ?: return
                    val content = param.args.getOrNull(0) as? View ?: return
                    attempt("setContentView") { reflow(frame, content) }
                }
            })
            hook(requireNotNull(KavaReflector.findDeclaredMethod(PopupWindow::class.java, "showAtLocation", View::class.java, Integer.TYPE, Integer.TYPE, Integer.TYPE)), object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val frame = target(param.thisObject) ?: return
                    attempt("beforeShow") {
                        val window = param.thisObject as PopupWindow
                        if (!frame.changed) window.contentView?.let { reflow(frame, it) }
                        if (frame.changed) fitPosition(window, param)
                    }
                }
            })
            hook(show, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!active || Looper.myLooper() != Looper.getMainLooper() || !prefs.getBoolean(MessageMenuExtensionSettings.KEY_ENABLE, false)) return
                    val frame = Frame(param.thisObject, frames.get())
                    frames.set(frame)
                    param.setObjectExtra(EXTRA, frame)
                    attempt("captureBeforeShow") { wrapBinding(frame) }
                }
                override fun afterHookedMethod(param: MethodHookParam) {
                    val frame = param.getObjectExtra(EXTRA) as? Frame ?: return
                    try {
                        if (param.hasThrowable() || param.result != true) frame.undo?.invoke()
                    } catch (error: Throwable) { HLog.e("$TAG stage=rollback", error) }
                    finally {
                        if (frame.changed) KavaReflector.writeField(flag, frame.owner, frame.originalFlag)
                        var current = KavaReflector.readField(binding, frame.owner)
                        var remaining = frame.originals.size
                        while (current != null && frame.originals.containsKey(current) && remaining-- > 0) {
                            current = frame.originals[current]
                        }
                        val installedCallback = KavaReflector.readField(binding, frame.owner)
                        if (installedCallback != null && frame.originals.containsKey(installedCallback)) {
                            if (!KavaReflector.writeField(binding, frame.owner, current)) HLog.e("$TAG stage=restoreBinding failed")
                        }
                        frame.originals.clear()
                        frame.views.clear()
                        frame.undo = null
                        if (frame.previous == null) frames.remove() else frames.set(frame.previous)
                    }
                }
            })
            installed = true
            HLog.i("$TAG stage=install version=8.0.78/3180 mode=grid configurable=true")
            return true
        } catch (error: Throwable) {
            hooks.forEach { HookRegistry.get().unhook(it) }; hooks.clear()
            HLog.e("$TAG stage=install", error)
            return false
        }
    }

    private fun wrapBinding(frame: Frame) {
        val original = KavaReflector.readField(binding, frame.owner)
        if (original != null && frame.originals.containsKey(original)) return
        val proxy = Proxy.newProxyInstance(binding.type.classLoader, arrayOf(binding.type)) { receiver, method, args ->
            when {
                method.name == "equals" && method.parameterTypes.size == 1 -> receiver === args?.getOrNull(0)
                method.name == "hashCode" && method.parameterTypes.isEmpty() -> System.identityHashCode(receiver)
                method.name == "toString" && method.parameterTypes.isEmpty() -> "HchatMenuGridBinding"
                else -> {
                    val result = if (original != null) try {
                        KavaReflector.invokeOrThrow(method, original, *(args ?: emptyArray()))
                    } catch (error: InvocationTargetException) { throw error.targetException } else null
                    if (method.name == bindMethod.name && method.parameterTypes.contentEquals(bindMethod.parameterTypes) && frames.get() === frame) {
                        val view = args?.getOrNull(0) as? View
                        val item = args?.getOrNull(2) as? MenuItem
                        if (view != null && item != null) frame.views[item] = view
                    }
                    result
                }
            }
        }
        check(KavaReflector.writeField(binding, frame.owner, proxy)) { "Cannot install binding proxy" }
        frame.originals[proxy] = original
    }
    private fun target(receiver: Any?): Frame? {
        if (!active || Looper.myLooper() != Looper.getMainLooper()) return null
        val frame = frames.get() ?: return null
        return frame.takeIf { receiver === KavaReflector.readField(popup, frame.owner) }
    }

    private fun reflow(frame: Frame, content: View) {
        if (frame.changed) return
        val menu = KavaReflector.readField(model, frame.owner) as? Menu ?: error("Menu model unavailable")
        val items = (0 until menu.size()).map { menu.getItem(it) }.filter { it.isVisible }
        if (items.isEmpty()) return
        val allViews = items.map { frame.views[it] ?: error("Capture incomplete: visible=${items.size} captured=${frame.views.size}") }
        check(allViews.toSet().size == allViews.size) { "Duplicate item Views" }
        val nativeRows = ROW_IDS.map { content.findViewById<View>(it) as? LinearLayout ?: error("Missing native row ${Integer.toHexString(it)}") }
        val parent = nativeRows.first().parent as? LinearLayout ?: error("Unsupported row parent")
        check(parent.orientation == LinearLayout.VERTICAL && nativeRows.all { it.parent === parent && it.orientation == LinearLayout.HORIZONTAL })
        // Resource order is not necessarily visual order; native separators may sit between rows.
        val rows = nativeRows.sortedBy { parent.indexOfChild(it) }
        val indices = rows.map { parent.indexOfChild(it) }
        check(indices.all { it >= 0 } && indices.distinct().size == rows.size) { "Invalid native row indices" }
        check(allViews.all { it.parent == null || it.parent in rows }) { "Unexpected item parent" }
        val columns = MessageMenuExtensionSettings.columns(prefs)
        val maxRows = MessageMenuExtensionSettings.maxRows(prefs)
        val count = (allViews.size + columns - 1) / columns
        val anchor = KavaReflector.readField(anchorField, frame.owner) as? View ?: error("Anchor unavailable")
        val outer = parent.parent as? LinearLayout ?: error("Unsupported popup root")
        check(outer === content) { "Unexpected popup hierarchy" }
        val parentIndex = outer.indexOfChild(parent)
        val parentParams = parent.layoutParams
        val separators = (0 until parent.childCount).map { parent.getChildAt(it) }.filter { it !in rows }
        val separatorVisibility = separators.associateWith { it.visibility }
        val window = KavaReflector.readField(popup, frame.owner) as? PopupWindow ?: error("Popup unavailable")
        val childSnapshots = rows.map { row -> (0 until row.childCount).map { row.getChildAt(it) } }
        check(childSnapshots.flatten().all { it in allViews }) { "Uncaptured row child" }
        val rowParams = rows.map { LinearLayout.LayoutParams(it.layoutParams) }
        val rowVisibility = rows.map { it.visibility }
        val itemParams = allViews.associateWith { it.layoutParams }
        val resizedChildren = IdentityHashMap<View, ViewGroup.LayoutParams>()
        var viewport: ScrollView? = null
        val extra = mutableListOf<LinearLayout>()
        frame.originalFlag = KavaReflector.readField(flag, frame.owner)
        frame.originalHeight = window.height; frame.originalWidth = window.width
        val restore = {
            viewport?.let { scroll ->
                scroll.removeView(parent); outer.removeView(scroll)
                if (parent.parent == null) outer.addView(parent, parentIndex, parentParams)
            }
            resizedChildren.forEach { (view, params) -> view.layoutParams = params }
            separatorVisibility.forEach { (view, visibility) -> view.visibility = visibility }
            extra.forEach { it.removeAllViews(); parent.removeView(it) }
            rows.forEach { it.removeAllViews() }
            allViews.forEach { view -> (view.parent as? ViewGroup)?.removeView(view); itemParams[view]?.let { view.layoutParams = it } }
            rows.forEachIndexed { index, row ->
                row.layoutParams = rowParams[index]; row.visibility = rowVisibility[index]
                childSnapshots[index].forEach { child -> row.addView(child) }
            }
            window.height = frame.originalHeight; window.width = frame.originalWidth
            KavaReflector.writeField(flag, frame.owner, frame.originalFlag)
            frame.changed = false
        }
        frame.undo = restore
        try {
            // Native setContentView precedes native measurement. Use this anchor's current window.
            val metrics = anchor.resources.displayMetrics
            val visibleFrame = Rect().also { anchor.getWindowVisibleDisplayFrame(it) }
            if (visibleFrame.isEmpty) visibleFrame.set(0, 0, metrics.widthPixels, metrics.heightPixels)
            val gap = (12 * metrics.density).toInt()
            val available = (visibleFrame.width() - 2 * gap).coerceAtLeast(columns)
            val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            content.measure(unspecified, unspecified)
            allViews.forEach { it.measure(unspecified, unspecified) }
            val horizontalPadding = parent.paddingLeft + parent.paddingRight +
                rows.first().paddingLeft + rows.first().paddingRight + content.paddingLeft + content.paddingRight
            val nativeItemWidth = allViews.maxOf { it.measuredWidth }.coerceAtLeast(1)
            val geometry = MessageMenuGridGeometry.width(available, horizontalPadding,
                rows.first().paddingLeft + rows.first().paddingRight, columns, nativeItemWidth)
            val itemWidth = geometry.item
            val rowWidth = geometry.row
            // c_6's fixed-width inner LinearLayout must shrink with its outer clickable View.
            allViews.forEach { view ->
                if (view is LinearLayout) {
                    for (index in 0 until view.childCount) {
                        val child = view.getChildAt(index)
                        val lp = child.layoutParams
                        if (lp.width > itemWidth) {
                            resizedChildren[child] = lp
                            child.layoutParams = LinearLayout.LayoutParams(lp).apply { width = ViewGroup.LayoutParams.MATCH_PARENT }
                        }
                        // Also fit fixed-size icons inside the now-narrower native inner container.
                        if (child is LinearLayout) {
                            for (innerIndex in 0 until child.childCount) {
                                val inner = child.getChildAt(innerIndex)
                                val innerParams = inner.layoutParams
                                val innerWidth = (itemWidth - child.paddingLeft - child.paddingRight).coerceAtLeast(1)
                                if (innerParams.width > innerWidth) {
                                    resizedChildren[inner] = innerParams
                                    inner.layoutParams = LinearLayout.LayoutParams(innerParams).apply {
                                        width = innerWidth
                                        if (innerParams.height == innerParams.width) height = innerWidth
                                    }
                                }
                            }
                        }
                    }
                }
                view.measure(View.MeasureSpec.makeMeasureSpec(itemWidth, View.MeasureSpec.EXACTLY), unspecified)
            }
            val rowHeight = allViews.maxOf { it.measuredHeight } + rows.first().paddingTop + rows.first().paddingBottom
            check(rowHeight > 0) { "Unmeasured native items" }
            rows.forEach { it.removeAllViews() }
            while (rows.size + extra.size < count) {
                val row = LinearLayout(content.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = rows.first().gravity
                    setPadding(
                        rows.first().paddingLeft,
                        rows.first().paddingTop,
                        rows.first().paddingRight,
                        rows.first().paddingBottom
                    )
                }
                parent.addView(row, indices.last() + 1 + extra.size, LinearLayout.LayoutParams(rowWidth, rowHeight))
                extra.add(row)
            }
            (rows + extra).forEachIndexed { index, row ->
                row.visibility = if (index < count) View.VISIBLE else View.GONE
                row.layoutParams = LinearLayout.LayoutParams(rowWidth, rowHeight)
                allViews.drop(index * columns).take(columns).forEach { view ->
                    row.addView(view, LinearLayout.LayoutParams(itemWidth, rowHeight - row.paddingTop - row.paddingBottom))
                }
            }
            separators.forEach { separator ->
                val index = parent.indexOfChild(separator)
                val precedingRows = rows.count { parent.indexOfChild(it) < index }
                separator.visibility = if (precedingRows in 1 until count) View.VISIBLE else View.GONE
            }
            parent.measure(View.MeasureSpec.makeMeasureSpec(rowWidth + parent.paddingLeft + parent.paddingRight, View.MeasureSpec.EXACTLY), unspecified)
            val chromeHeight = content.paddingTop + content.paddingBottom +
                (0 until outer.childCount).map { outer.getChildAt(it) }
                    .filter { it !== parent && it.visibility != View.GONE }.sumOf { it.measuredHeight }
            val maxHeight = (visibleFrame.height() - 2 * gap - chromeHeight).coerceAtLeast(1)
            val viewportHeight = MessageMenuGridGeometry.viewport(parent.measuredHeight, rowHeight, maxRows,
                parent.paddingTop + parent.paddingBottom,
                separators.filter { it.visibility == View.VISIBLE }.sumOf { it.measuredHeight }, maxHeight)
            val scroll = ScrollView(content.context).apply {
                isFillViewport = false
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = true
            }
            viewport = scroll
            outer.removeView(parent)
            scroll.addView(parent, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            outer.addView(scroll, parentIndex, LinearLayout.LayoutParams(rowWidth + parent.paddingLeft + parent.paddingRight, viewportHeight))
            check(KavaReflector.writeField(flag, frame.owner, false)) { "Cannot override popup height flag" }
            window.height = ViewGroup.LayoutParams.WRAP_CONTENT
            window.width = ViewGroup.LayoutParams.WRAP_CONTENT
            frame.changed = true
            content.requestLayout()
        } catch (error: Throwable) {
            restore(); frame.undo = null; throw error
        }
    }
    private fun fitPosition(window: PopupWindow, param: XC_MethodHook.MethodHookParam) {
        val anchor = param.args[0] as? View ?: return
        val content = window.contentView ?: return
        val rect = Rect(); anchor.getWindowVisibleDisplayFrame(rect)
        if (rect.isEmpty) return
        val gravity = param.args[1] as? Int ?: return
        // Native reference uses absolute x/y; fail back to native coordinates for other gravities.
        if ((gravity and Gravity.HORIZONTAL_GRAVITY_MASK) !in listOf(0, Gravity.LEFT) ||
            (gravity and Gravity.VERTICAL_GRAVITY_MASK) !in listOf(0, Gravity.TOP)) return
        content.measure(View.MeasureSpec.makeMeasureSpec(rect.width(), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(rect.height(), View.MeasureSpec.AT_MOST))
        param.args[2] = (param.args[2] as Int).coerceIn(rect.left, maxOf(rect.left, rect.right - content.measuredWidth))
        param.args[3] = (param.args[3] as Int).coerceIn(rect.top, maxOf(rect.top, rect.bottom - content.measuredHeight))
    }
    private fun hook(method: Method, callback: XC_MethodHook) { hooks += HookRegistry.get().hook(method, callback) }
    private inline fun attempt(stage: String, block: () -> Unit) {
        try { block() } catch (error: Throwable) { HLog.e("$TAG stage=$stage native fallback", error) }
    }
    @Synchronized fun destroy() {
        active = false
        hooks.toList().forEach { hook -> attempt("destroy hook") { HookRegistry.get().unhook(hook) } }
        hooks.clear(); frames.remove(); installed = false
    }
    companion object {
        private const val TAG = "[Hchat:消息菜单扩展]"
        private const val EXTRA = "hchat.menuGrid.frame"
        private val ROW_IDS = listOf(0x7f0a48cf, 0x7f0a48d2, 0x7f0a48d1)
    }
}
