package t.mma.hooks.items.swipequote

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import t.mma.hooks.items.chattoolbar.ChatToolbarSettings
import android.view.WindowManager
import android.widget.FrameLayout

/** Presentation only. Every mode uses the caller's single guarded confirmation action. */
internal object SwipeRepeatPresentation {
    enum class Mode { ORIGINAL, GENTLE, FINGERPRINT }
    const val ORIGINAL = "你最好是真的在复读，而不是误碰，艹！"
    const val GENTLE = "宝宝，刚刚我说话有点凶，没吓到你吧[害羞]这次你是真的要复读么？"

    fun choose(): Mode = Mode.entries[java.util.concurrent.ThreadLocalRandom.current().nextInt(Mode.entries.size)]

    /** Verified 8.0.78 native smiley service: ej().fj(Context, CharSequence). */
    fun gentleText(context: Context, loader: ClassLoader): CharSequence {
        val type = loader.loadClass("com.tencent.mm.smiley.x1")
        val instance = type.getDeclaredMethod("ej").invoke(null)
        return type.getDeclaredMethod("fj", Context::class.java, CharSequence::class.java)
            .invoke(instance, context, GENTLE) as CharSequence
    }

    fun fingerprint(context: Activity, confirm: () -> Unit): AlertDialog {
        val root = FingerprintOverlay(context)
        val button = FingerprintView(context)
        val size = (64 * context.resources.displayMetrics.density + 0.5f).toInt()
        root.addView(button, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        val dialog = AlertDialog.Builder(context).create()
        dialog.setOnShowListener {
            dialog.setContentView(root, android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT))
            styleFingerprint(dialog)
        }
        root.setOnClickListener { dialog.cancel() }
        button.setOnClickListener {
            if (dialog.isShowing) {
                dialog.dismiss()
                confirm()
            }
        }
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        return dialog
    }

    fun styleFingerprint(dialog: AlertDialog) {
        val window = dialog.window ?: return
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.decorView.setPadding(0, 0, 0, 0)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    }

    /** Positions from the actual chat toolbar, then the native input row when disabled. */
    private class FingerprintOverlay(private val activity: Activity) : FrameLayout(activity) {
        private fun footer(view: View): View? {
            if (view.visibility != View.VISIBLE || !view.isShown) return null
            if (view.javaClass.name == "com.tencent.mm.pluginsdk.ui.chat.ChatFooter") return view
            if (view is ViewGroup) for (index in 0 until view.childCount) {
                footer(view.getChildAt(index))?.let { return it }
            }
            return null
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            val decor = activity.window.decorView
            val toolbar = decor.findViewWithTag<View>(ChatToolbarSettings.TOOLBAR_TAG)
                ?.takeIf { it.isShown && it.height > 0 }
            val anchor = toolbar ?: footer(decor)
            val origin = IntArray(2)
            getLocationOnScreen(origin)
            val location = IntArray(2)
            anchor?.getLocationOnScreen(location)
            val anchorTop = if (anchor != null) location[1] - origin[1] else height
            val gap = (100 * resources.displayMetrics.density + 0.5f).toInt()
            val button = getChildAt(0)
            val y = (anchorTop - gap - button.measuredHeight).coerceAtLeast(paddingTop)
            val x = (width - button.measuredWidth) / 2
            button.layout(x, y, x + button.measuredWidth, y + button.measuredHeight)
        }
    }

    /** Local vector strokes, with no bitmap cache, animation, sensor or authentication. */
    private class FingerprintView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.25f
            strokeCap = Paint.Cap.ROUND
        }
        private val ridges = List(6) { index ->
            val inset = index * 3f
            Path().apply {
                moveTo(10f + inset, 38f)
                cubicTo(4f + inset, 2f + inset, 59f - inset, 2f + inset, 54f - inset, 34f)
                cubicTo(54f - inset, 43f, 48f - inset, 51f, 45f - inset, 53f)
            }
        }
        private val center = Path().apply {
            moveTo(31f, 30f)
            cubicTo(35f, 40f, 29f, 50f, 25f, 54f)
            moveTo(23f, 37f)
            cubicTo(24f, 44f, 20f, 49f, 17f, 51f)
            moveTo(16f, 40f)
            cubicTo(16f, 44f, 13f, 47f, 12f, 48f)
        }
        init {
            contentDescription = "确认复读"
            isClickable = true
            isFocusable = true
            val fill = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(91, 138, 206), Color.rgb(27, 66, 143))).apply {
                shape = GradientDrawable.OVAL
                setStroke((3 * resources.displayMetrics.density + 0.5f).toInt(), Color.rgb(255, 152, 0))
            }
            val mask = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), fill, mask)
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val checkpoint = canvas.save()
            canvas.scale(width / 64f, height / 64f)
            ridges.forEach { canvas.drawPath(it, paint) }
            canvas.drawPath(center, paint)
            canvas.restoreToCount(checkpoint)
        }
    }
}
