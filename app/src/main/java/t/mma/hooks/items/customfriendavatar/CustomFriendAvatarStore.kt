package t.mma.hooks.items.customfriendavatar
import t.mma.utils.HLog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import t.mma.preferences.HchatStorage
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object CustomFriendAvatarStore {
    private data class CacheEntry(val modified: Long, val size: Long, val bitmap: Bitmap)

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val checked = ConcurrentHashMap<String, Long>()

    private val preloader = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "Hchat-AvatarPreload").apply { isDaemon = true }
    }
    private val preloadStarted = java.util.concurrent.atomic.AtomicBoolean()
    fun preload(context: Context) {
        if (!preloadStarted.compareAndSet(false, true)) return
        val app = context.applicationContext ?: context
        preloader.execute {
            configuredFriends(app).forEach { id -> runCatching { loadBitmap(app, id) } }
        }
    }
    @JvmStatic
    fun configuredFriends(context: Context): Set<String> {
        val saved = CustomFriendAvatarSettings.preferences(context)
            .getStringSet(CustomFriendAvatarSettings.KEY_CONFIGURED_FRIENDS, emptySet())
            .orEmpty()
        return saved.filterTo(linkedSetOf()) { avatarFile(context, it).isFile }
    }

    @JvmStatic
    fun hasAvatar(context: Context, wxid: String?): Boolean {
        val id = wxid?.trim().orEmpty()
        return id.isNotEmpty() && avatarFile(context, id).isFile
    }

    @JvmStatic
    fun avatarPath(context: Context, wxid: String?): String {
        val id = wxid?.trim().orEmpty()
        return id.takeIf(String::isNotEmpty)?.let { avatarFile(context, it).absolutePath }.orEmpty()
    }

    @JvmStatic
    fun loadBitmap(context: Context, wxid: String?): Bitmap? {
        val id = wxid?.trim().orEmpty()
        if (id.isEmpty()) return null
        val now = android.os.SystemClock.elapsedRealtime()
        val cached = cache[id]?.bitmap?.takeUnless { it.isRecycled }
        if (now - (checked[id] ?: Long.MIN_VALUE / 2) < 2000L) return cached
        if (checked.size > 256) checked.clear()
        checked[id] = now
        val file = avatarFile(context, id)
        if (!file.isFile || file.length() <= 0L) return null
        cache[id]?.takeIf {
            it.modified == file.lastModified() && it.size == file.length() && !it.bitmap.isRecycled
        }?.let { return it.bitmap }
        val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
            ?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
            ?: return null
        cache[id] = CacheEntry(file.lastModified(), file.length(), decoded)
        return decoded
    }

    @JvmStatic
    fun loadNotificationBitmap(context: Context, wxid: String?): Bitmap? {
        if (!CustomFriendAvatarSettings.notificationsEnabled(context)) return null
        return loadBitmap(context, wxid)
    }

    @JvmStatic
    @Synchronized
    fun saveFromUri(context: Context, wxid: String, uri: Uri): Boolean {
        return saveFromUri(context, wxid, uri, trackConfiguredFriend = true)
    }

    @JvmStatic
    @Synchronized
    fun saveFromUri(
        context: Context,
        wxid: String,
        uri: Uri,
        trackConfiguredFriend: Boolean
    ): Boolean {
        val id = wxid.trim()
        if (id.isEmpty()) return false
        return runCatching {
            val source = decodeBounded(context, uri) ?: error("无法解析图片")
            val edge = minOf(source.width, source.height)
            val bitmap = if (source.width == edge && source.height == edge) {
                source
            } else {
                Bitmap.createBitmap(
                    source,
                    (source.width - edge) / 2,
                    (source.height - edge) / 2,
                    edge,
                    edge
                )
            }
            val target = avatarFile(context, id)
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, target.name + ".tmp")
            FileOutputStream(temporary).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    error("头像写入失败")
                }
                output.fd.sync()
            }
            if (!temporary.isFile || temporary.length() <= 0L) error("头像文件为空")
            runCatching {
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            }.getOrElse {
                if (target.exists() && !target.delete()) error("旧头像删除失败")
                if (!temporary.renameTo(target)) error("头像替换失败")
            }
            if (trackConfiguredFriend) {
                val ids = configuredFriends(context).toMutableSet().apply { add(id) }
                CustomFriendAvatarSettings.preferences(context).edit()
                    .putStringSet(CustomFriendAvatarSettings.KEY_CONFIGURED_FRIENDS, ids)
                    .apply()
            }
            cache[id] = CacheEntry(target.lastModified(), target.length(), bitmap)
            checked[id] = android.os.SystemClock.elapsedRealtime()
            true
        }.getOrElse {
            HLog.e("$TAG 保存自定义头像失败: wxid=$id, error=${it.message}", it)
            false
        }
    }

    @JvmStatic
    @Synchronized
    fun remove(context: Context, wxid: String): Boolean {
        val id = wxid.trim()
        if (id.isEmpty()) return false
        val file = avatarFile(context, id)
        val deleted = !file.exists() || file.delete()
        val ids = configuredFriends(context).toMutableSet().apply { remove(id) }
        CustomFriendAvatarSettings.preferences(context).edit()
            .putStringSet(CustomFriendAvatarSettings.KEY_CONFIGURED_FRIENDS, ids)
            .apply()
        cache.remove(id)
        checked.remove(id)
        return deleted
    }

    @JvmStatic
    fun invalidate(wxid: String?) {
        wxid?.trim()?.takeIf { it.isNotEmpty() }?.let { cache.remove(it); checked.remove(it) }
    }

    private fun avatarFile(context: Context, wxid: String): File {
        val directory = File(HchatStorage.storageDir(context), DIRECTORY_NAME)
        return File(directory, sha256(wxid) + ".png")
    }

    private fun decodeBounded(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_DIMENSION || bounds.outHeight / sample > MAX_DIMENSION) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        return CharArray(digest.size * 2).also { chars ->
            digest.forEachIndexed { index, byte ->
                val unsigned = byte.toInt() and 0xff
                chars[index * 2] = hex[unsigned ushr 4]
                chars[index * 2 + 1] = hex[unsigned and 0xf]
            }
        }.concatToString()
    }

    private const val TAG = "[Hchat:CustomFriendAvatar]"
    private const val DIRECTORY_NAME = "custom_friend_avatars"
    private const val MAX_DIMENSION = 1024
}
