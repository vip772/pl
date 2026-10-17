package t.mma.hooks.items.homesidepanel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import t.mma.preferences.HchatStorage
import t.mma.utils.HLog

internal object SidePanelImages {
    private fun file(context: Context, key: String) = File(File(HchatStorage.storageDir(context), "side_panel"), "$key.jpg")
    fun saveFromUri(context: Context, key: String, uri: Uri): Boolean = runCatching {
        require(key == "photo" || key == "background")
        val target = file(context, key)
        target.parentFile?.mkdirs()
        val source = File(target.parentFile, "$key.source")
        val temporary = File(target.parentFile, "$key.tmp")
        try {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                source.outputStream().use { output ->
                    val buffer = ByteArray(8192); var total = 0L
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        total += count; require(total <= 32L * 1024 * 1024)
                        output.write(buffer, 0, count)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0)
            var sample = 1
            while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 2400 ||
                (bounds.outWidth.toLong() / sample) * (bounds.outHeight / sample) > 2_000_000) sample *= 2
            val bitmap = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("无法解码图片")
            try { temporary.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) } }
            finally { bitmap.recycle() }
            java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            HomeSidePanelSettings.preferences(context).edit().putLong(
                if (key == "photo") SidePanelAppearance.PHOTO else SidePanelAppearance.BACKGROUND, System.currentTimeMillis()
            ).apply()
            true
        } finally { source.delete(); temporary.delete() }
    }.getOrElse { HLog.e("[Hchat:HomeSidePanel] 图片导入失败", it); false }
    fun load(context: Context, key: String): Bitmap? = runCatching {
        BitmapFactory.decodeFile(file(context, key).path)
    }.getOrNull()
    fun clear(context: Context, key: String) {
        file(context, key).delete()
        HomeSidePanelSettings.preferences(context).edit().putLong(
            if (key == "photo") SidePanelAppearance.PHOTO else SidePanelAppearance.BACKGROUND, System.currentTimeMillis()
        ).apply()
    }
}