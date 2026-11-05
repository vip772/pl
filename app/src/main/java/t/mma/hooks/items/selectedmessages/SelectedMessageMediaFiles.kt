package t.mma.hooks.items.selectedmessages

import t.mma.hooks.api.core.WeChatApis
import java.io.File

/** Readable files for module send, Moments and system share; never raw tokens. */
internal object SelectedMessageMediaFiles {
    private fun readable(path: String): String? = path.trim().takeIf {
        it.isNotEmpty() && File(it).let { file -> file.isFile && file.canRead() && file.length() > 0L }
    }
    private fun candidates(snapshot: SelectedMessageSnapshot) =
        listOf(snapshot.retransmit?.fileName.orEmpty(), snapshot.imagePath)
            .map(String::trim).filter(String::isNotEmpty).distinct()
    fun image(snapshot: SelectedMessageSnapshot): String {
        val paths = candidates(snapshot)
        paths.firstNotNullOfOrNull(::readable)?.let { return it }
        val api = WeChatApis.media()?.images() ?: return ""
        snapshot.nativeMessage?.let { native ->
            readable(api.resolveBestAvailablePath(native))?.let { return it }
        }
        return paths.firstNotNullOfOrNull { readable(api.resolvePathToken(it)) }.orEmpty()
    }
    fun video(snapshot: SelectedMessageSnapshot): String {
        val paths = candidates(snapshot)
        paths.firstNotNullOfOrNull(::readable)?.let { return it }
        val api = WeChatApis.media()?.videos() ?: return ""
        return paths.firstNotNullOfOrNull { readable(api.resolvePathToken(it)) }.orEmpty()
    }
}
