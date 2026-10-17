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
    fun photoFile(context: Context, key: String = "photo"): File? {
        val directory = file(context,key).parentFile!!
        val name = HomeSidePanelSettings.preferences(context).getString("panel_${key}_file",null)
        return name?.takeIf { it == "$key.image" || it == "$key.video" }?.let { File(directory,it) }?.takeIf { it.isFile }
            ?: file(context,key).takeIf { it.isFile }
    }
    private fun savePhoto(context: Context, key: String, uri: Uri): Boolean = runCatching {
        require(key == "photo" || key in SidePanelAppearance.extraPhotos(context))
        val directory = file(context,key).parentFile!!.apply { mkdirs() }
        val video = context.contentResolver.getType(uri)?.startsWith("video/") == true
        val target = File(directory,if(video) "$key.video" else "$key.image")
        val temporary = File(directory,"$key.import")
        try {
            context.contentResolver.openInputStream(uri)!!.use { input -> temporary.outputStream().use { output ->
                val buffer=ByteArray(8192);var total=0L
                while(true) { val count=input.read(buffer);if(count<0) break
                    total+=count;require(total <= if(video) 128L*1024*1024 else 32L*1024*1024)
                    output.write(buffer,0,count)
                }
            } }
            if(video) {
                val metadata=android.media.MediaMetadataRetriever()
                try {
                    metadata.setDataSource(temporary.path)
                    require((metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0)>0)
                    require((metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0)>0)
                } finally { metadata.release() }
            } else {
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                BitmapFactory.decodeFile(temporary.path,bounds)
                require(bounds.outWidth>0 && bounds.outHeight>0)
                // API 27 Movie decodes GIF frames at source size.
                if(android.os.Build.VERSION.SDK_INT<28) require(bounds.outWidth.toLong()*bounds.outHeight <= 2_000_000L)
            }
            require(key == "photo" || key in SidePanelAppearance.extraPhotos(context))
            java.nio.file.Files.move(temporary.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            HomeSidePanelSettings.preferences(context).edit().putString("panel_${key}_file",target.name)
                .putLong(SidePanelAppearance.photoRevision(key),System.currentTimeMillis()).apply()
            listOf("$key.jpg","$key.image","$key.video").filter { it!=target.name }.forEach { File(directory,it).delete() }
            true
        } finally { temporary.delete() }
    }.getOrElse { HLog.e("[Hchat:HomeSidePanel] 照片媒体导入失败",it);false }
    fun saveFromUri(context: Context, key: String, uri: Uri): Boolean {
        if(SidePanelAppearance.isPhoto(key)) return savePhoto(context,key,uri)
        return runCatching {
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
                if (SidePanelAppearance.isPhoto(key)) SidePanelAppearance.photoRevision(key) else SidePanelAppearance.BACKGROUND, System.currentTimeMillis()
            ).apply()
            true
        } finally { source.delete(); temporary.delete() }
    }.getOrElse { HLog.e("[Hchat:HomeSidePanel] 图片导入失败", it); false }
    }
    fun load(context: Context, key: String): Bitmap? = runCatching {
        BitmapFactory.decodeFile(file(context, key).path)
    }.getOrNull()
    fun clear(context: Context, key: String) {
        file(context, key).delete()
        if(SidePanelAppearance.isPhoto(key)) {
            listOf("$key.image","$key.video").forEach { java.io.File(file(context,key).parentFile,it).delete() }
            HomeSidePanelSettings.preferences(context).edit().remove("panel_${key}_file").remove(SidePanelAppearance.photoRevision(key)).apply()
        }
        HomeSidePanelSettings.preferences(context).edit().putLong(
            if (SidePanelAppearance.isPhoto(key)) SidePanelAppearance.photoRevision(key) else SidePanelAppearance.BACKGROUND, System.currentTimeMillis()
        ).apply()
    }
}