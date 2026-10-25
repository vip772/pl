package t.mma.hooks.api.media

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Per-dialog request only: no queued send or shared callback survives cancellation. */
internal suspend fun <T : Any> awaitFavoriteVoiceFile(
    resolve: () -> T?,
    startDownload: suspend () -> Boolean,
    downloading: () -> Unit,
    attempts: Int = 120,
    pollMillis: Long = 500L
): T {
    currentCoroutineContext().ensureActive()
    resolve()?.let { return it }
    downloading()
    check(startDownload()) { "无法启动收藏语音下载，请检查网络后重试" }
    repeat(attempts) {
        delay(pollMillis)
        currentCoroutineContext().ensureActive()
        resolve()?.let { return it }
    }
    error("收藏语音下载超时，请检查网络或资源是否有效后重试")
}
