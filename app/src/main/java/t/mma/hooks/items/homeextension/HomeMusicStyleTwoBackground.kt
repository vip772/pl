package t.mma.hooks.items.homeextension

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import t.mma.preferences.HchatStorage
import t.mma.utils.HLog
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object HomeMusicStyleTwoBackground {
    const val SLOT = "home_music_style2_background"
    private fun file(context: Context) = File(HchatStorage.resourceDirectory(context, "home_music"), "style2_background.jpg")

    private const val REVISION = "music_style2_background_revision"
    fun revision(context: Context): Long = HomeExtensionSettings.preferences(context).getLong(REVISION, 0L)
    private fun changed(context: Context) { HomeExtensionSettings.preferences(context).edit().putLong(REVISION, maxOf(System.currentTimeMillis(), revision(context) + 1)).apply() }
    fun load(context: Context): Bitmap? = runCatching {
        val source = file(context)
        if (!source.isFile) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600 ||
            bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) > 2_500_000L) sample *= 2
        BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrElse { HLog.e("[Hchat:HomeMusic] 样式2背景读取失败", it); null }

    fun save(context: Context, uri: Uri): Boolean = runCatching {
        val target = file(context)
        val source = File(target.parentFile, "style2_background.import")
        val temp = File(target.parentFile, "style2_background.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> source.outputStream().use { output ->
                val buffer = ByteArray(8192); var total = 0L
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    total += count; require(total <= 32L * 1024 * 1024) { "图片不能超过32MB" }
                    output.write(buffer, 0, count)
                }
            } } ?: error("无法读取图片")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无效图片" }
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600 ||
                bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) > 2_500_000L) sample *= 2
            val bitmap = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: error("无法解码图片")
            try { temp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) } }
            finally { bitmap.recycle() }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            changed(context)
            true
        } finally { source.delete(); temp.delete() }
    }.getOrElse { HLog.e("[Hchat:HomeMusic] 样式2背景导入失败", it); false }

    fun clear(context: Context): Boolean = runCatching {
        val target = file(context)
        check(!target.exists() || target.delete()) { "无法清除背景图片" }
        changed(context)
        true
    }.getOrElse { HLog.e("[Hchat:HomeMusic] 样式2背景清除失败", it); false }
}