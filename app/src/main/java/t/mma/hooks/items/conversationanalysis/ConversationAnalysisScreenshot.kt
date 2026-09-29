package t.mma.hooks.items.conversationanalysis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View
import java.io.File
import java.io.FileOutputStream

internal object ConversationAnalysisScreenshot {
    fun capture(view: View, directory: File, onResult: (File?) -> Unit) {
        view.post {
            val width = view.width
            val height = view.height
            if (width <= 0 || height <= 0) { onResult(null); return@post }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            Thread {
                val target = File(directory, "conversation_analysis_${System.currentTimeMillis()}.png")
                val ok = runCatching { directory.mkdirs(); FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; target.isFile && target.length() > 0 }.getOrDefault(false)
                bitmap.recycle()
                Handler(Looper.getMainLooper()).post { onResult(if (ok) target else null) }
            }.start()
        }
    }
}