package t.mma.hooks.items.autocache

import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.model.WeChatMessage
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.preferences.HchatStorage
import t.mma.ui.FeatureSettingsProvider
import t.mma.ui.SimpleFeatureSettingsProvider
import t.mma.utils.HLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object AutoCacheSettings {
    const val PREFS_NAME = "Hchat_auto_cache_config"
    const val KEY_ENABLE = "enable"
    const val KEY_CACHE_TYPE = "cache_type"
    const val TYPE_MEDIA = "media"
    const val TYPE_MEDIA_PDF = "media_pdf"
    const val TYPE_ALL = "all"

    fun normalizeType(value: String?): String = when (value) {
        TYPE_MEDIA -> TYPE_MEDIA
        TYPE_MEDIA_PDF -> TYPE_MEDIA_PDF
        else -> TYPE_ALL
    }

    fun accepts(message: WeChatMessage, type: String?): Boolean {
        if (message.isImage() || message.isVideo()) return true
        if (!message.isFile()) return false
        return when (normalizeType(type)) {
            TYPE_MEDIA -> false
            TYPE_MEDIA_PDF -> message.getFileMsg()?.ext?.trim()?.removePrefix(".")?.equals("pdf", ignoreCase = true) == true
            else -> true
        }
    }
}

class AutoCacheFeature : BaseFeature() {
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "Hchat-AutoCache").apply { isDaemon = true }
    }
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val stopped = AtomicBoolean(false)
    private val lastSubmitted = ConcurrentHashMap<String, Long>()
    @Volatile private var native: NativeMediaCache? = null
    private val observing = AtomicBoolean(false)
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
            val downloader = native ?: NativeMediaCache(context).also { native = it }
            val ready = try {
                downloader.install()
            } catch (error: Throwable) {
                HLog.e("[Hchat:AutoCache] 定位原生入口失败，保留消息监听等待补装", error)
                false
            }
            if (!ready) HLog.e("[Hchat:AutoCache] 部分原生入口未就绪，已就绪媒体继续处理，等待补装")
            observer.install()
            if (observing.compareAndSet(false, true)) {
                HLog.i("[Hchat:AutoCache] 新消息监听已接入 enabled=${prefs.getBoolean(AutoCacheSettings.KEY_ENABLE, false)}")
                trackSubscription(observer.subscribe { event ->
                    if (stopped.get() || event.outgoing || !prefs.getBoolean(AutoCacheSettings.KEY_ENABLE, false)) return@subscribe
                    val message = event.storedMessage ?: event.message ?: return@subscribe
                    if (!message.isIncoming() || !AutoCacheSettings.accepts(message,
                            prefs.getString(AutoCacheSettings.KEY_CACHE_TYPE, AutoCacheSettings.TYPE_ALL))) return@subscribe
                    val id = when {
                        message.msgSvrId > 0 -> "s${message.msgSvrId}"
                        message.msgId > 0 -> "l${message.msgId}"
                        else -> return@subscribe
                    }
                    val account = WeChatApis.account()?.selfWxId().orEmpty()
                    if (account.isBlank()) return@subscribe
                    val key = "$account:${message.talker}:$id"
                    if (pending.add(key)) {
                        HLog.i("[Hchat:AutoCache] 收到媒体 type=${message.type} local=${message.msgId} server=${message.msgSvrId}")
                        enqueue(context, message, account, key, 0)
                    }
                })
            }
            ready
        }
    }

    private fun enqueue(context: FeatureContext, initial: WeChatMessage, account: String, key: String, attempt: Int) {
        if (stopped.get()) { pending.remove(key); return }
        worker.schedule({
            val enabled = HchatStorage.preferences(context.hostContext(), AutoCacheSettings.PREFS_NAME)
                .getBoolean(AutoCacheSettings.KEY_ENABLE, false)
            if (stopped.get() || !enabled || WeChatApis.account()?.selfWxId() != account) {
                pending.remove(key)
                lastSubmitted.remove(key)
                return@schedule
            }
            try {
                val store = WeChatApis.message().store()
                val message = initial.msgId.takeIf { it > 0 }?.let { store?.getMessageById(it) }
                    ?: initial.msgSvrId.takeIf { it > 0 }?.let { store?.getMessageBySvrId(initial.talker, it) }
                    ?: initial
                val cacheType = HchatStorage.preferences(context.hostContext(), AutoCacheSettings.PREFS_NAME)
                    .getString(AutoCacheSettings.KEY_CACHE_TYPE, AutoCacheSettings.TYPE_ALL)
                if (!AutoCacheSettings.accepts(message, cacheType)) {
                    pending.remove(key)
                    lastSubmitted.remove(key)
                    return@schedule
                }
                if (native?.complete(message) == true) {
                    HLog.i("[Hchat:AutoCache] 下载完成 msgId=${message.msgId} type=${message.type}")
                    pending.remove(key)
                    lastSubmitted.remove(key)
                    return@schedule
                }
                val now = android.os.SystemClock.elapsedRealtime()
                val last = lastSubmitted[key]
                if (last == null || now - last >= 60_000L) {
                    val submitted = native?.submit(message) == true
                    if (submitted) lastSubmitted[key] = now
                    if (submitted || attempt == 0 || attempt % 60 == 0) {
                        HLog.i("[Hchat:AutoCache] 下载提交=$submitted msgId=${message.msgId} type=${message.type} attempt=$attempt")
                    }
                }
                if (attempt < 720) {
                    // 上传占位、元数据未落库和网络未就绪时持续补查，最多一小时。
                    enqueue(context, initial, account, key, attempt + 1)
                } else {
                    pending.remove(key)
                    lastSubmitted.remove(key)
                    HLog.e("[Hchat:AutoCache] 等待原生下载超时 msgId=${message.msgId} type=${message.type}")
                }
            } catch (error: Throwable) {
                if (attempt == 0 || attempt % 60 == 0) HLog.e("[Hchat:AutoCache] 原生下载异常 msgId=${initial.msgId}", error)
                if (attempt < 720) enqueue(context, initial, account, key, attempt + 1)
                else {
                    pending.remove(key)
                    lastSubmitted.remove(key)
                }
            }
        }, if (attempt == 0) 500L else 5000L, TimeUnit.MILLISECONDS)
    }

    override fun onFeatureDestroy(context: FeatureContext) {
        stopped.set(true)
        worker.shutdownNow()
        pending.clear()
        lastSubmitted.clear()
        native = null
    }
    companion object { const val ID = "auto_cache" }
}

private class AutoCacheSettingsProvider : SimpleFeatureSettingsProvider(
    AutoCacheFeature.ID, "自动缓存", "接收图片、视频和任意文件文档后自动触发微信原生下载",
    FeatureSettingsProvider.CATEGORY_ENHANCE, true
)