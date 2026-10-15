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
}

class AutoCacheFeature : BaseFeature() {
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "Hchat-AutoCache").apply { isDaemon = true }
    }
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val stopped = AtomicBoolean(false)
    private val lastSubmitted = ConcurrentHashMap<String, Long>()
    private var native: NativeMediaCache? = null
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
            if (!downloader.install()) {
                HLog.e("[Hchat:AutoCache] 原生下载入口未全部就绪，等待调度补装")
                return@schedule false
            }
            observer.install()
            trackSubscription(observer.subscribe { event ->
                if (stopped.get() || event.outgoing || !prefs.getBoolean(AutoCacheSettings.KEY_ENABLE, false)) return@subscribe
                val message = event.storedMessage ?: event.message ?: return@subscribe
                if (!message.isIncoming() || (!message.isImage() && !message.isVideo() && !message.isFile())) return@subscribe
                val id = when {
                    message.msgSvrId > 0 -> "s${message.msgSvrId}"
                    message.msgId > 0 -> "l${message.msgId}"
                    else -> return@subscribe
                }
                val account = WeChatApis.account()?.selfWxId().orEmpty()
                if (account.isBlank()) return@subscribe
                val key = "$account:${message.talker}:$id"
                if (pending.add(key)) enqueue(context, message, account, key, 0)
            })
            true
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
                if (native?.complete(message) == true) {
                    pending.remove(key)
                    lastSubmitted.remove(key)
                    return@schedule
                }
                val now = android.os.SystemClock.elapsedRealtime()
                val last = lastSubmitted[key]
                if (last == null || now - last >= 60_000L) {
                    if (native?.submit(message) == true) lastSubmitted[key] = now
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