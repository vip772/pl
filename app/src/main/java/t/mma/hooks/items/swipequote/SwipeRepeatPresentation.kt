package t.mma.hooks.items.swipequote

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

    fun fingerprint(context: Context, confirm: () -> Unit): AlertDialog {
        val root = FrameLayout(context)
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

    /** Local vector strokes, with no bitmap cache, animation, sensor or authentication. */
    private class FingerprintView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(106, 96, 82)
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND
        }
        private val ridges = List(5) { index ->
            val inset = index * 3.2f
            Path().apply {
                moveTo(12f + inset, 43f - index)
                cubicTo(5f + inset, 9f + inset, 57f - inset, 5f + inset, 52f - inset, 34f)
                cubicTo(51f - inset, 45f, 47f - inset, 50f, 44f - inset, 53f)
            }
        }
        private val center = Path().apply {
            moveTo(31f, 31f)
            cubicTo(33f, 42f, 29f, 48f, 24f, 53f)
        }
        init {
            contentDescription = "确认复读"
            isClickable = true
            isFocusable = true
            val fill = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(250, 246, 235)) }
            val mask = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
            background = RippleDrawable(ColorStateList.valueOf(0x226A6052), fill, mask)
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
