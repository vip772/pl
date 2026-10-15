package t.mma.hooks.items.autocache

import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.media.WeChatImageApi
import t.mma.hooks.api.model.WeChatMessage
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider
import t.mma.utils.HchatMediaDownloader
import t.mma.utils.HLog
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object AutoCacheSettings {
    const val PREFS_NAME = "Hchat_auto_cache_config"
    const val KEY_ENABLE = "enable"
}

class AutoCacheFeature : BaseFeature() {
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "Hchat-AutoCache").apply { isDaemon = true }
    }
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val stopped = AtomicBoolean(false)
    private val slots = Semaphore(3)
    override fun featureId(): String = ID
    override fun name(): String = "自动缓存"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(AutoCacheSettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), AutoCacheSettings.PREFS_NAME)
        DexInstallScheduler.schedule(ID, name(), DexInstallScheduler.Stage.WARMUP) {
            if (stopped.get()) return@schedule true
            val observer = WeChatApis.message().observe() ?: return@schedule false
            if (!observer.isAvailable) return@schedule false
            observer.install()
            trackSubscription(observer.subscribe { event ->
                if (stopped.get() || event.outgoing || !prefs.getBoolean(AutoCacheSettings.KEY_ENABLE, false)) {
                    return@subscribe
                }
                val message = event.storedMessage ?: event.message ?: return@subscribe
                if (!message.isIncoming() || (!message.isImage() && !message.isVideo() && !message.isFile())) {
                    return@subscribe
                }
                val id = when {
                    message.msgSvrId > 0 -> "s${message.msgSvrId}"
                    message.msgId > 0 -> "l${message.msgId}"
                    else -> return@subscribe
                }
                val account = WeChatApis.account()?.selfWxId().orEmpty()
                if (account.isBlank()) return@subscribe
                val key = "$account:${message.talker}:$id"
                // 有界队列，避免群聊集中接收附件时无限占用内存。
                synchronized(pending) {
                    if (pending.size >= 512) {
                        HLog.e("[Hchat:AutoCache] 待下载队列已满，跳过消息 ${message.msgId}")
                        return@subscribe
                    }
                    if (!pending.add(key)) return@subscribe
                }
                enqueue(context, message, key, 0)
            })
            true
        }
    }

    private data class Request(
        val md5: String,
        val url: String,
        val aesKey: String,
        val type: Int,
        val length: Long,
        val name: String,
        val category: String
    )

    private fun request(message: WeChatMessage): Request? {
        if (message.isImage()) {
            val image = message.getImageMsg() ?: return null
            val original = image.bigImgUrl.isNotBlank()
            val url = if (original) image.bigImgUrl else image.midImgUrl
            if (url.isBlank() || image.key.isBlank()) return null
            return Request(image.md5, url, image.key, if (original) 1 else 2,
                (if (original) image.bigLength else image.midLength).toLong(), "image.jpg", "Image")
        }
        if (message.isVideo()) {
            val video = message.getVideoMsg()
            // 接收视频的正文可能没有 XML，必须读取原生 VideoInfo。
            val info = message.imagePath.takeIf { it.isNotBlank() }
                ?.let { WeChatApis.media().videos()?.resolveDownloadInfo(it) }
            val url = info?.cdnUrl?.takeIf { it.isNotBlank() } ?: video?.cdnVideoUrl.orEmpty()
            val aes = info?.aesKey?.takeIf { it.isNotBlank() } ?: video?.aesKey.orEmpty()
            if (url.isBlank() || aes.isBlank()) return null
            return Request(info?.md5?.takeIf { it.isNotBlank() } ?: video?.md5.orEmpty(),
                url, aes, 4, info?.totalLength?.takeIf { it > 0 } ?: video?.length ?: 0L,
                "video.mp4", "Video")
        }
        val file = message.getFileMsg() ?: return null
        if (file.url.isBlank() || file.key.isBlank()) return null
        // 8.0.78 NetSceneDownloadAppAttach: 普通附件 5，大文件 7；
        // AppMessage parser 在 totallen > 25 MiB 时同样设置 islargefilemsg。
        val large = Regex("<islargefilemsg>\\s*(?:<!\\[CDATA\\[)?([0-9]+)")
            .find(message.content)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return Request(file.md5, file.url, file.key,
            if (large != 0 || file.size > 25L * 1024 * 1024) 7 else 5, file.size,
            file.fileName.ifBlank { file.title }.ifBlank { "file.${file.ext.ifBlank { "bin" }}" }, "File")
    }

    private fun enabled(context: FeatureContext): Boolean = !stopped.get() &&
        HchatStorage.preferences(context.hostContext(), AutoCacheSettings.PREFS_NAME)
            .getBoolean(AutoCacheSettings.KEY_ENABLE, false)

    private fun enqueue(context: FeatureContext, initial: WeChatMessage, key: String, attempt: Int) {
        if (stopped.get()) { pending.remove(key); return }
        worker.schedule({
            if (!enabled(context) || WeChatApis.account()?.selfWxId() != key.substringBefore(':')) {
                pending.remove(key)
                return@schedule
            }
            if (!slots.tryAcquire()) {
                enqueue(context, initial, key, attempt)
                return@schedule
            }
            var handedToDownload = false
            try {
                val store = WeChatApis.message().store()
                val message = initial.msgId.takeIf { it > 0 }?.let { store?.getMessageById(it) }
                    ?: initial.msgSvrId.takeIf { it > 0 }?.let { store?.getMessageBySvrId(initial.talker, it) }
                    ?: initial
                val request = request(message)
                if (request == null || WeChatApis.media()?.images()?.canDownloadCdn() != true) {
                    slots.release()
                    retry(context, initial, key, attempt, "媒体元数据未就绪")
                    return@schedule
                }
                val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                val safeName = request.name.replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_").take(120)
                val dir = HchatMediaDownloader.hchatDir(context.hostContext(), "AutoCache/${request.category}")
                val target = File(dir, "${hash}_$safeName")
                if (target.isFile && target.length() > 0 &&
                    (request.length <= 0 || target.length() >= request.length)) {
                    slots.release()
                    pending.remove(key)
                    return@schedule
                }
                val temp = File(dir, ".${hash}.${System.nanoTime()}.part")
                temp.delete()
                val finished = AtomicBoolean(false)
                var timeout: ScheduledFuture<*>? = null
                fun finish(file: File?, error: String?) {
                    if (!finished.compareAndSet(false, true)) return
                    timeout?.cancel(false)
                    slots.release()
                    if (file != null && file.isFile && file.length() > 0 &&
                        (request.length <= 0 || file.length() >= request.length)) {
                        if (!file.renameTo(target)) {
                            HLog.e("[Hchat:AutoCache] 保存缓存失败: ${target.name}")
                        }
                    } else {
                        HLog.e("[Hchat:AutoCache] 下载失败: ${error ?: "文件长度不完整"}")
                    }
                    // 下载器超时后可能仍在写入，不能重复提交同路径任务。
                    pending.remove(key)
                }
                handedToDownload = true
                timeout = worker.schedule({ finish(null, "下载超过十分钟") }, 10, TimeUnit.MINUTES)
                val callback = object : WeChatImageApi.DownloadCallback {
                    override fun onSuccess(file: File) { finish(file, null) }
                    override fun onError(message: String) { finish(null, message) }
                }
                val submitted = WeChatApis.media().images()?.downloadCdn(
                    request.md5, request.url, request.aesKey, temp.absolutePath,
                    request.type, callback
                ) == true
                if (!submitted) finish(null, "CDN 任务提交失败")
            } catch (error: Throwable) {
                if (!handedToDownload) slots.release()
                HLog.e("[Hchat:AutoCache] 自动缓存异常", error)
                pending.remove(key)
            }
        }, if (attempt == 0) 500L else 1500L, TimeUnit.MILLISECONDS)
    }

    private fun retry(context: FeatureContext, message: WeChatMessage, key: String, attempt: Int, reason: String) {
        if (attempt < 20 && enabled(context)) enqueue(context, message, key, attempt + 1)
        else {
            pending.remove(key)
            HLog.e("[Hchat:AutoCache] 放弃缓存消息 ${message.msgId}: $reason")
        }
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        stopped.set(true)
        worker.shutdownNow()
        pending.clear()
    }
    companion object { const val ID = "auto_cache" }
}

private class AutoCacheSettingsProvider : SimpleFeatureSettingsProvider(
    AutoCacheFeature.ID, "自动缓存", "自动保存接收的图片、视频与文件文档",
    FeatureSettingsProvider.CATEGORY_ENHANCE, true
)
