package t.mma.hooks.items.moments

import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import t.mma.event.Events
import t.mma.hooks.core.*
import t.mma.hooks.items.antirecall.DynamicRecallGradient
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier
import java.util.WeakHashMap

class MomentsCardFeature : BaseFeature() {
    override fun featureId() = ID
    override fun name() = "朋友圈卡片模式"
    override fun isEnabled(context: FeatureContext) = true

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(object : SimpleFeatureSettingsProvider(
            ID, name(), "独立动态卡片、6dp 间距与三色渐变描边",
            FeatureSettingsProvider.CATEGORY_PRACTICAL, true
        ) {}, enabledCheck = {
            HchatStorage.preferences(context.hostContext(), MomentsCardSettings.PREFS_NAME)
                .getBoolean(MomentsCardSettings.ENABLE, false)
        })
    }

    override fun onFeatureInstall(context: FeatureContext) {
        if (context.loadPackageParam().processName != context.loadPackageParam().packageName) return
        val prefs = HchatStorage.preferences(context.hostContext(), MomentsCardSettings.PREFS_NAME)
        val current = java.util.concurrent.atomic.AtomicReference<MomentsCardRuntime?>()
        val owner = PreferenceRuntimeOwner(
            prefs, setOf(MomentsCardSettings.ENABLE, MomentsCardSettings.BORDER, MomentsCardSettings.COLORS),
            enabled = { prefs.getBoolean(MomentsCardSettings.ENABLE, false) },
            create = { resources ->
                // Parse once per enabled/settings interval, never during scroll or draw.
                val colors = DynamicRecallGradient.parts(
                    prefs.getString(MomentsCardSettings.COLORS, MomentsCardSettings.DEFAULT_COLORS).orEmpty()
                ).map(Color::parseColor).toIntArray()
                val runtime = MomentsCardRuntime(
                    context, prefs.getBoolean(MomentsCardSettings.BORDER, false), colors
                ) { logError("朋友圈卡片模式", it) }
                current.set(runtime)
                resources.register {
                    try { runtime.close() } finally { current.compareAndSet(runtime, null) }
                }
                runtime.install()
            },
            onError = { logError("朋友圈卡片启停失败", it) }, restartOnChange = true
        )
        trackResource { owner.close() }
        owner.start()
        subscribe(Events.DexReady::class.java) { current.get()?.install() }
    }

    companion object { const val ID = "moments_card_mode" }
}

/** 8078 evidence: BaseTimeLineItem.t/n and fk4.m2.h. No generic View/scroll hooks. */
internal class MomentsCardRuntime(
    private val context: FeatureContext,
    private val border: Boolean,
    private val colors: IntArray,
    private val report: (Throwable) -> Unit
) : AutoCloseable {
    private val hooks = OwnedHooks(report)
    private val main = Handler(Looper.getMainLooper())
    // State is owned by the installed drawable, never strongly by this index.
    // Host drawables may retain their View through Drawable.Callback.
    private val originals = WeakHashMap<View, WeakReference<Original>>()
    @Volatile private var closed = false
    private var legacyCreateInstalled = false
    private var legacyInstalled = false
    private var improveInstalled = false

    @Synchronized fun install() {
        if (closed) return
        val loader = context.loadPackageParam().classLoader
        if (!legacyInstalled) runCatching {
            val cls = loader.loadClass(LEGACY)
            val holder = loader.loadClass(HOLDER)
            val rootField = holder.getDeclaredField("h").apply {
                check(type == View::class.java); isAccessible = true
            }
            val create = cls.declaredMethods.single {
                it.name == "t" && it.returnType == View::class.java &&
                    it.parameterTypes.map { p -> p.name } == listOf(
                        "android.app.Activity", HOLDER, "int",
                        "com.tencent.mm.plugin.sns.ui.et", "com.tencent.mm.plugin.sns.storage.SnsInfo"
                    )
            }
            val bind = cls.declaredMethods.single {
                it.name == "n" && !Modifier.isAbstract(it.modifiers) &&
                    it.returnType == Void.TYPE && it.parameterTypes.map { p -> p.name } == listOf(
                        HOLDER, "int", "com.tencent.mm.plugin.sns.storage.SnsInfo",
                        "com.tencent.mm.protocal.protobuf.TimeLineObject", "int",
                        "com.tencent.mm.plugin.sns.ui.et", "com.tencent.mm.plugin.sns.ui.rt"
                    )
            }
            // Creation is needed for first bind; subsequent binds reuse the same drawable.
            if (!legacyCreateInstalled) {
                check(hooks.hook(create, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!closed && !param.hasThrowable()) (param.result as? View)?.let { style(it) }
                }
            }) != null)
                legacyCreateInstalled = true
            }
            check(hooks.hook(bind, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (closed || param.hasThrowable()) return
                    runCatching { (rootField.get(param.args[0]) as? View)?.let { style(it) } }
                        .onFailure(report)
                }
            }) != null)
            legacyInstalled = true
        }.onFailure(report)
        if (!improveInstalled) runCatching {
            val cls = loader.loadClass("fk4.m2")
            check(ViewGroup::class.java.isAssignableFrom(cls))
            val divider = cls.getMethod("getDivider").apply { check(returnType == View::class.java) }
            val bind = cls.getDeclaredMethod(
                "h", loader.loadClass("vv5.s0"), loader.loadClass("lk4.b"), Integer.TYPE
            ).apply { check(returnType == java.lang.Boolean.TYPE) }
            check(hooks.hook(bind, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    // h() returns isItemChanged, not bind success (8078 m2.p/h).
                    // Unchanged/payload binds still need styling after settings are enabled.
                    if (closed || param.hasThrowable()) return
                    runCatching {
                        val view = param.thisObject as View
                        style(view, divider.invoke(view) as? View)
                    }.onFailure(report)
                }
            }) != null)
            improveInstalled = true
        }.onFailure(report)
    }

    private fun style(view: View, divider: View? = null) {
        if (closed || Looper.myLooper() != Looper.getMainLooper()) return
        val existing = originals[view]?.get()
        if (existing != null) {
            existing.divider?.get()?.let { if (it.visibility != View.GONE) it.visibility = View.GONE }
            return
        }
        if (originals.size >= MAX_VIEWS) return
        val density = view.resources.displayMetrics.density
        val night = view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val side = (6 * density + .5f).toInt()
        val gap = (MomentsCardSettings.GAP_DP * density + .5f).toInt()
        val inset = (MomentsCardSettings.CONTENT_INSET_DP * density + .5f).toInt()
        val original = Original(
            view.background, view.foreground?.let { java.lang.ref.WeakReference(it) },
            view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom,
            divider?.let { java.lang.ref.WeakReference(it) }, divider?.visibility ?: View.GONE,
            view.elevation, view.translationZ
        )
        originals[view] = WeakReference(original)
        // Keep host descendants, tags, listeners, clipping and layout-param types untouched.
        // Remove row Z shadows only; restore both original values when ownership ends.
        view.elevation = 0f
        view.translationZ = 0f
        // Preserve contrasting gutters around the requested fixed card color.
        val fill = MomentsCardDrawable(
            density, gap, side, MomentsCardSettings.CARD_BACKGROUND, null, original,
            surround = if (night) 0xFF191919.toInt() else 0xFFF2F2F2.toInt()
        )
        original.installedBackground = WeakReference(fill)
        view.background = fill
        if (border) {
            val stroke = MomentsCardDrawable(density, gap, side, Color.TRANSPARENT, colors)
            view.foreground = original.foreground?.get()?.let { LayerDrawable(arrayOf(it, stroke)) } ?: stroke
        }
        // Reserve interior space separately from the 6dp inter-card gutter.
        // The 32dp rounded foreground must not cross the native bottom/end comment action.
        view.setPadding(original.left + side + inset, original.top + inset,
            original.right + side + inset, original.bottom + gap + inset)
        divider?.visibility = View.GONE
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        hooks.close()
        // A single main-thread release; never post per row or retain an Activity globally.
        val restore = Runnable {
            originals.entries.toList().forEach { (view, reference) ->
                val old = reference.get() ?: return@forEach
                runCatching {
                    if (view.background === old.installedBackground?.get()) {
                        view.background = old.background
                        if (border) view.foreground = old.foreground?.get()
                        view.setPadding(old.left, old.top, old.right, old.bottom)
                        view.elevation = old.elevation
                        view.translationZ = old.translationZ
                        old.divider?.get()?.visibility = old.dividerVisibility
                    }
                }.onFailure(report)
            }
            originals.clear()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) restore.run() else main.post(restore)
    }

    private data class Original(
        val background: Drawable?, val foreground: java.lang.ref.WeakReference<Drawable>?,
        val left: Int, val top: Int, val right: Int, val bottom: Int,
        val divider: java.lang.ref.WeakReference<View>?, val dividerVisibility: Int,
        val elevation: Float, val translationZ: Float,
        var installedBackground: WeakReference<MomentsCardDrawable>? = null
    )

    companion object {
        private const val LEGACY = "com.tencent.mm.plugin.sns.ui.item.BaseTimeLineItem"
        private const val HOLDER = "com.tencent.mm.plugin.sns.ui.item.BaseTimeLineItem\$BaseViewHolder"
        private const val MAX_VIEWS = 192
    }
}

/** Static GPU-friendly round-rect: no animation, allocation, IO or invalidation loop in draw. */
internal class MomentsCardDrawable(
    private val density: Float, private val gap: Int, private val side: Int,
    fill: Int, private val colors: IntArray?,
    // Keeps restoration state alive exactly as long as the host owns this drawable.
    @Suppress("unused") private val restorationState: Any? = null,
    private val surround: Int = Color.TRANSPARENT
) : Drawable() {
    private val rect = RectF()
    private val surroundRect = RectF()
    private val surroundPaint = if (surround == Color.TRANSPARENT) null else
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surround; style = Paint.Style.FILL }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Paint alpha modulates shader colors: a transparent unused fill hides the stroke.
        color = if (colors == null) fill else Color.WHITE
        style = if (colors == null) Paint.Style.FILL else Paint.Style.STROKE
        strokeWidth = MomentsCardSettings.STROKE_DP * density
    }
    private val radius = MomentsCardSettings.CORNER_RADIUS_DP * density
    private var drawRadius = radius
    override fun onBoundsChange(bounds: Rect) {
        surroundRect.set(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
        val half = if (colors == null) 0f else paint.strokeWidth / 2
        rect.set(bounds.left + side + half, bounds.top + half, bounds.right - side - half, bounds.bottom - gap - half)
        // Match the inner stroke curve to the fill; clamp short rows to a capsule.
        drawRadius = minOf((radius - half).coerceAtLeast(0f),
            rect.width().coerceAtLeast(0f) / 2, rect.height().coerceAtLeast(0f) / 2)
        if (colors != null && rect.width() > 0) {
            paint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, colors, null, Shader.TileMode.CLAMP)
        }
    }
    override fun draw(canvas: Canvas) {
        surroundPaint?.let { canvas.drawRect(surroundRect, it) }
        if (!rect.isEmpty) canvas.drawRoundRect(rect, drawRadius, drawRadius, paint)
    }
    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha; surroundPaint?.alpha = alpha; invalidateSelf()
    }
    override fun setColorFilter(filter: ColorFilter?) {
        paint.colorFilter = filter; surroundPaint?.colorFilter = filter; invalidateSelf()
    }
    @Deprecated("Drawable opacity is deprecated")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
