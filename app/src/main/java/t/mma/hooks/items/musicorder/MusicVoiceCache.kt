package t.mma.hooks.items.musicorder

import java.io.File
import java.security.MessageDigest

/** One worker owns this disk cache. Never cache URLs, credentials or compressed input. */
internal class MusicVoiceCache(private val root: File, private val now: () -> Long) {
    fun file(identity: String): File {
        root.mkdirs()
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(root, "$hash.silk")
    }
    fun duration(file: File): Int {
        if (!file.isFile || file.length() == 0L || now() - file.lastModified() > TTL_MS) return 0
        val duration = runCatching {
            File(file.path + ".duration").takeIf { it.isFile }
                ?.readText()?.toIntOrNull()?.takeIf { it > 0 }
        }.getOrNull() ?: return 0
        file.setLastModified(now())
        return duration
    }
    fun commit(file: File, duration: Int): Boolean {
        if (!file.isFile || file.length() <= 0 || file.length() > 32L * 1024 * 1024 || duration <= 0) return false
        File(file.path + ".duration").writeText(duration.toString())
        file.setLastModified(now())
        trim(file)
        return true
    }
    fun trim(keep: File? = null) {
        val files = root.listFiles()?.filter { it.extension == "silk" }?.sortedBy { it.lastModified() }.orEmpty()
        var bytes = files.sumOf { it.length() }
        var count = files.size
        files.forEach {
            if (it != keep && (now() - it.lastModified() > TTL_MS || count > 8 || bytes > 32L * 1024 * 1024)) {
                bytes -= it.length(); count--
                it.delete(); File(it.path + ".duration").delete()
            }
        }
    }
    companion object { private const val TTL_MS = 24L * 60 * 60 * 1000 }
}