package h.Hchat.hooks.api.contact

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import h.Hchat.dexkit.DexMethodCache
import h.Hchat.hooks.core.FeatureContext
import h.Hchat.hooks.core.HookRegistry
import h.Hchat.utils.HLog
import h.Hchat.utils.KavaReflector
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.lang.ref.WeakReference
import java.lang.reflect.Method
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
    private const val BIND_ANCHOR = "[onBindView] never attach!"
    private const val CACHE_PREFS = "Hchat_contact_header_method_cache"
    private const val CACHE_METHOD = "normal_profile_header_bind_v1"
    private val providers = CopyOnWriteArrayList<Provider>()
    @Volatile private var installed = false
    @Volatile private var currentActivity: WeakReference<Activity>? = null
    @Volatile private var currentHeader: WeakReference<View.OnLongClickListener>? = null
    @Volatile private var currentRoot: WeakReference<View>? = null
    @Volatile private var currentGroupId: String? = null
    @Volatile private var currentMemberId: String? = null

    @Synchronized
    fun install(context: FeatureContext): Boolean {
        if (installed) return true
        val method = locateBindMethod(context) ?: run {
            HLog.e("[$TAG] 未找到资料页头部绑定方法")
            return false
        }
        return runCatching {
            HookRegistry.get().hook(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    runCatching { bind(param.thisObject as? View.OnLongClickListener, param.args.firstOrNull() as? View) }
                        .onFailure { HLog.e("[$TAG] 注入资料页头部失败", it) }
                }
            })
            installed = true
            true
        }.onFailure { HLog.e("[$TAG] 安装资料页头部 Hook 失败", it) }.getOrDefault(false)
    }

    fun addProvider(provider: Provider) { providers.addIfAbsent(provider) }
    fun removeProvider(provider: Provider) { providers.remove(provider) }

    private fun locateBindMethod(context: FeatureContext): Method? {
        val prefs = DexMethodCache.prefs(context.hostContext(), CACHE_PREFS)
        val runtimeKey = DexMethodCache.runtimeKey(context.hostContext(), context.hostClassLoader())
        DexMethodCache.load(prefs, runtimeKey, context.hostClassLoader(), CACHE_METHOD)
            ?.takeIf(::isBindMethod)?.let { return it }
        val found = runCatching {
            context.dexKitBridge().findMethod(
                FindMethod().apply {
                    matcher(MethodMatcher().apply {
                        declaredClass(HEADER_CLASS)
                        paramTypes(View::class.java)
                        returnType("void")
                        usingStrings(listOf(BIND_ANCHOR))
                    })
                }
            ).asSequence()
                .mapNotNull { runCatching { it.getMethodInstance(context.hostClassLoader()) }.getOrNull() }
                .firstOrNull(::isBindMethod)
        }.onFailure { HLog.e("[$TAG] DexKit 定位资料页头部绑定方法失败", it) }.getOrNull()
        if (found == null) DexMethodCache.clear(prefs, runtimeKey, CACHE_METHOD)
        else DexMethodCache.save(prefs, runtimeKey, CACHE_METHOD, found)
        return found
    }

    private fun isBindMethod(method: Method): Boolean =
        method.declaringClass.name == HEADER_CLASS &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            View::class.java.isAssignableFrom(method.parameterTypes[0])

    fun refreshCurrentProfile(groupId: String, memberId: String) {
        val activity = currentActivity?.get() ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        if (currentGroupId != groupId || currentMemberId != memberId) return
        val header = currentHeader?.get() ?: return
        val root = currentRoot?.get() ?: return
        root.post { bind(header, root) }
    }

    private fun bind(header: View.OnLongClickListener?, root: View?) {
        if (header == null || root == null) return
        val viewRoot = root as? ViewGroup ?: return
        findViews(viewRoot) { it.tag == ROW_TAG }.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        if (providers.isEmpty()) return
        var context = root.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        val activity = context as? Activity ?: return
        currentActivity = WeakReference(activity)
        currentHeader = WeakReference(header)
        currentRoot = WeakReference(root)
        currentGroupId = activity.intent?.getStringExtra("Contact_ChatRoomId")
            ?: activity.intent?.getStringExtra("RoomInfo_Id")
        currentMemberId = activity.intent?.getStringExtra("room_member")
            ?: activity.intent?.getStringExtra("Contact_User")
            ?: activity.intent?.getStringExtra("Chat_User")
        val templates = findViews(viewRoot) {
            it is TextView && listenerInfoLongClick(it) === header
        }.filterIsInstance<TextView>()
        val parent = templates.map { it.parent }.distinct().singleOrNull() as? LinearLayout ?: return
        if (parent.orientation != LinearLayout.VERTICAL) return
        val template = templates.firstOrNull() ?: return
        val touch = listenerInfoTouch(template)
        providers.forEach { provider ->
            val text = provider.getHeaderText(activity) ?: return@forEach
            val row = TextView(template.context).apply {
                id = View.generateViewId()
                tag = ROW_TAG
                layoutParams = LinearLayout.LayoutParams(template.layoutParams as LinearLayout.LayoutParams)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, template.textSize)
                setTextColor(template.textColors)
                typeface = template.typeface
                gravity = template.gravity
                includeFontPadding = template.includeFontPadding
                textDirection = template.textDirection
                setPaddingRelative(template.paddingStart, template.paddingTop, template.paddingEnd, template.paddingBottom)
                setLineSpacing(template.lineSpacingExtra, template.lineSpacingMultiplier)
                this.text = text
                applyColorSpec(this, provider.getHeaderColorSpec(activity))
                if (touch != null) setOnTouchListener(touch)
                setOnLongClickListener {
                    val ownId = id
                    try {
                        id = template.id
                        header.onLongClick(this)
                    } finally {
                        id = ownId
                    }
                }
            }
            parent.addView(row)
        }
    }

    private fun applyColorSpec(view: TextView, raw: String?) {
        val parts = raw.orEmpty().split(',')
            .mapNotNull { runCatching { Color.parseColor(it.trim()) }.getOrNull() }
            .take(2)
        if (parts.isEmpty()) return
        val end = parts.getOrElse(1) { parts[0] }
        view.paint.shader = if (parts[0] == end) null else
            LinearGradient(0f, 0f, 1000f, 0f, parts[0], end, Shader.TileMode.CLAMP)
        if (parts[0] == end) view.setTextColor(parts[0])
    }

    private fun listenerInfo(view: View): Any? = KavaReflector.readField(view, "mListenerInfo")
    private fun listenerInfoLongClick(view: View): Any? =
        listenerInfo(view)?.let { KavaReflector.readField(it, "mOnLongClickListener") }
    private fun listenerInfoTouch(view: View): View.OnTouchListener? =
        listenerInfo(view)?.let { KavaReflector.readField(it, "mOnTouchListener") as? View.OnTouchListener }

    private fun findViews(root: ViewGroup, predicate: (View) -> Boolean): List<View> {
        val result = ArrayList<View>()
        fun walk(view: View) {
            if (predicate(view)) result += view
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return result
    }
}
