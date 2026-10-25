package t.mma.hooks.items.homeextension

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import t.mma.preferences.HchatStorage
import t.mma.utils.HLog
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Fixed managed path: no external URI/path is required after import or restore. */
internal object CalendarBackground {
    const val SLOT = "home_calendar_background"
    const val REVISION = "calendar_background_revision"
    private fun file(context: Context) = File(HchatStorage.resourceDirectory(context, "home_calendar"), "background.jpg")
    private var cachedVersion: String? = null
    private var cached: Bitmap? = null
    fun hasImage(context: Context) = file(context).isFile
    @Synchronized fun save(context: Context, uri: Uri): Boolean = runCatching {
        val target = file(context)
        val source = File(target.parentFile, "background.import")
        val temp = File(target.parentFile, "background.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                source.outputStream().use { output ->
                    val buffer = ByteArray(8192); var total = 0L
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        total += count; require(total <= 32L * 1024 * 1024) { "图片不能超过32MB" }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("无法读取图片")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无效图片" }
            var sample = 1
            while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 1440 ||
                bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) > 2_000_000L) sample *= 2
            val bitmap = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("无法解码图片")
            try { temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) } }
            finally { bitmap.recycle() }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            cachedVersion = null; cached = null
            changed(context)
            true
        } finally { source.delete(); temp.delete() }
    }.getOrElse { HLog.e("[Hchat:HomeCalendar] 背景图片导入失败", it); false }
    @Synchronized fun clear(context: Context): Boolean = runCatching {
        val target = file(context)
        check(!target.exists() || target.delete()) { "无法清除日历背景" }
        cachedVersion = null; cached = null
        changed(context); true
    }.getOrElse { HLog.e("[Hchat:HomeCalendar] 清除背景失败", it); false }
    private fun changed(context: Context) {
        HomeExtensionSettings.preferences(context).edit().putLong(REVISION, System.currentTimeMillis()).apply()
    }
    /** Called on worker, never during draw. Cached bitmaps may still be used by old views: do not recycle them. */
    @Synchronized fun load(context: Context): Bitmap? = runCatching {
        val source = file(context)
        if (!source.isFile) { cachedVersion = null; cached = null; return@runCatching null }
        val version = "${source.path}:${source.lastModified()}:${source.length()}"
        if (cachedVersion == version) return@runCatching cached
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 1440 ||
            bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) > 2_000_000L) sample *= 2
        BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })?.also {
            cachedVersion = version; cached = it
        }
    }.getOrElse { HLog.e("[Hchat:HomeCalendar] 读取背景失败", it); null }
}

/** Center crop only the background; never clips today's decoration or changes touch. */
internal class CalendarBackgroundDrawable(bitmap: Bitmap, private val radius: Float, opacity: Int) : Drawable() {
    private val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val imageWidth = bitmap.width.toFloat()
    private val imageHeight = bitmap.height.toFloat()
    private val rect = RectF()
    private val matrix = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        this.shader = this@CalendarBackgroundDrawable.shader
        alpha = opacity.coerceIn(0, 255)
    }
    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        val scale = maxOf(bounds.width() / imageWidth, bounds.height() / imageHeight)
        matrix.setScale(scale, scale)
        matrix.postTranslate(bounds.left + (bounds.width() - imageWidth * scale) / 2f,
            bounds.top + (bounds.height() - imageHeight * scale) / 2f)
        shader.setLocalMatrix(matrix)
    }
    override fun draw(canvas: Canvas) { canvas.drawRoundRect(rect, radius, radius, paint) }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
