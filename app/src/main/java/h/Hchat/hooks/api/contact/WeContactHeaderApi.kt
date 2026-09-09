package h.Hchat.hooks.api.contact

import android.app.Activity
import android.content.ContextWrapper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import de.robv.android.xposed.XC_MethodHook
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.HLog
import h.Hchat.utils.KavaReflector
import java.util.concurrent.CopyOnWriteArrayList

/** Shared insertion point for rows rendered by NormalProfileHeaderPreference. */
object WeContactHeaderApi {
    interface Provider {
        fun getHeaderText(activity: Activity): String?
        fun getHeaderColorSpec(activity: Activity): String? = null
    }
    private const val TAG = "Hchat:WeContactHeaderApi"
    private const val ROW_TAG = "Hchat:ContactHeaderRow"
    private const val HEADER_CLASS = "com.tencent.mm.plugin.profile.ui.NormalProfileHeaderPreference"
    private val providers = CopyOnWriteArrayList<Provider>()
    @Volatile private var installed = false

    @Synchronized
    fun install(classLoader: ClassLoader) {
        if (installed) return
        val clazz = KavaReflector.loadClass(HEADER_CLASS, classLoader) ?: run { HLog.e("[$TAG] 未找到资料页头部类"); return }
        val method = KavaReflector.findMethodRecursive(clazz, "onBindView", View::class.java)
            ?: run { HLog.e("[$TAG] 未找到 onBindView"); return }
        HookRegistry.get().hook(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                runCatching { bind(param.thisObject as? View.OnLongClickListener, param.args.firstOrNull() as? View) }
                    .onFailure { HLog.e("[$TAG] 注入资料页头部失败", it) }
            }
        })
        installed = true
    }

    fun addProvider(provider: Provider) { providers.addIfAbsent(provider) }
    fun removeProvider(provider: Provider) { providers.remove(provider) }

    private fun bind(header: View.OnLongClickListener?, root: View?) {
        if (header == null || root == null || root.tag == null) return
        val viewRoot = root as? ViewGroup ?: return
        findViews(viewRoot) { it.tag == ROW_TAG }.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        if (providers.isEmpty()) return
        var context = root.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        val activity = context as? Activity ?: return
        val templates = findViews(viewRoot) { it is TextView && listenerInfoLongClick(it) === header }.filterIsInstance<TextView>()
        val parent = templates.map { it.parent }.distinct().singleOrNull() as? LinearLayout ?: return
        if (parent.orientation != LinearLayout.VERTICAL) return
        val template = templates.firstOrNull() ?: return
        val touch = listenerInfoTouch(template)
        providers.forEach { provider ->
            val text = provider.getHeaderText(activity) ?: return@forEach
            val row = TextView(template.context).apply {
                id = View.generateViewId(); tag = ROW_TAG
                layoutParams = LinearLayout.LayoutParams(template.layoutParams as LinearLayout.LayoutParams)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, template.textSize); setTextColor(template.textColors)
                typeface = template.typeface; gravity = template.gravity; includeFontPadding = template.includeFontPadding
                textDirection = template.textDirection
                setPaddingRelative(template.paddingStart, template.paddingTop, template.paddingEnd, template.paddingBottom)
                setLineSpacing(template.lineSpacingExtra, template.lineSpacingMultiplier); this.text = text
                applyColorSpec(this, provider.getHeaderColorSpec(activity))
                if (touch != null) setOnTouchListener(touch)
                setOnLongClickListener { val ownId = id; try { id = template.id; header.onLongClick(this) } finally { id = ownId } }
            }
            parent.addView(row)
        }
    }

    private fun applyColorSpec(view: TextView, raw: String?) {
        val parts = raw.orEmpty().split(',').mapNotNull { runCatching { Color.parseColor(it.trim()) }.getOrNull() }.take(2)
        if (parts.isEmpty()) return
        val end = parts.getOrElse(1) { parts[0] }
        view.paint.shader = if (parts[0] == end) null else LinearGradient(0f, 0f, 1000f, 0f, parts[0], end, Shader.TileMode.CLAMP)
        if (parts[0] == end) view.setTextColor(parts[0])
    }
    private fun listenerInfo(view: View): Any? = KavaReflector.readField(view, "mListenerInfo")
    private fun listenerInfoLongClick(view: View): Any? = listenerInfo(view)?.let { KavaReflector.readField(it, "mOnLongClickListener") }
    private fun listenerInfoTouch(view: View): View.OnTouchListener? = listenerInfo(view)?.let { KavaReflector.readField(it, "mOnTouchListener") as? View.OnTouchListener }
    private fun findViews(root: ViewGroup, predicate: (View) -> Boolean): List<View> {
        val result = ArrayList<View>()
        fun walk(view: View) { if (predicate(view)) result += view; if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i)) }
        walk(root); return result
    }
}