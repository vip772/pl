package t.mma.hooks.items.autocache

import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.api.model.WeChatMessage
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import android.content.SharedPreferences
import t.mma.hooks.core.FeatureResourceScope
import t.mma.utils.KavaReflector
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
    const val KEY_CHATS = "applicable_chats"

    fun acceptsChat(talker: String?, selected: Set<String>): Boolean =
        selected.isEmpty() || (!talker.isNullOrBlank() && talker in selected)
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
    private var runningResources: FeatureResourceScope? = null
    override fun featureId(): String = ID
    override fun name(): String = "自动缓存"
    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(AutoCacheSettingsProvider())
    }
    override fun onFeatureInstall(context: FeatureContext) {
        val preferences = HchatStorage.preferences(context.hostContext(), AutoCacheSettings.PREFS_NAME)
        var active = true
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AutoCacheSettings.KEY_ENABLE || key == null) {
                synchronized(this) { if (active) updateRuntime(context, preferences) }
            }
        }
        trackResource {
            synchronized(this) {
                active = false
                preferences.unregisterOnSharedPreferenceChangeListener(listener)
                stopRuntime()
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        updateRuntime(context, preferences)
    }
    @Synchronized
    private fun updateRuntime(context: FeatureContext, preferences: SharedPreferences) {
        if (!preferences.getBoolean(AutoCacheSettings.KEY_ENABLE, false)) {
            stopRuntime()
            return
        }
        if (runningResources != null) return
        val resources = FeatureResourceScope { logError("自动缓存释放失败", it) }
        runningResources = resources
        try {
            val runtime = AutoCacheRuntime(context) { subscription ->
                resources.register {
                    KavaReflector.invoke(KavaReflector.findMethod(subscription.javaClass, "unsubscribe"), subscription)
                }
            }
            resources.register { runtime.shutdown() }
            runtime.start()
        } catch (error: Throwable) {
            stopRuntime()
            logError("自动缓存启动失败", error)
        }
    }
    private fun stopRuntime() {
        val resources = runningResources
        runningResources = null
        resources?.close()
    }
    companion object { const val ID = "auto_cache" }
}
internal class AutoCacheRuntime(
    private val context: FeatureContext,
    private val registerSubscription: (Any) -> Unit
) {
    private val installation = DexInstallScheduler.installation(
        AutoCacheFeature.ID, "自动缓存", DexInstallScheduler.Stage.WARMUP
    )
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "Hchat-AutoCache").apply { isDaemon = true }
    }
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val stopped = AtomicBoolean(false)
    private val lastSubmitted = ConcurrentHashMap<String, Long>()
    @Volatile private var native: NativeMediaCache? = null
    private val observing = AtomicBoolean(false)
    fun start() {
        if (stopped.get()) return
        val prefs = HchatStorage.preferences(context.hostContext(), AutoCacheSettings.PREFS_NAME)
        installation.schedule {
            if (stopped.get()) return@schedule true
            val observer = WeChatApis.message().observe() ?: return@schedule false
            if (!observer.isAvailable) return@schedule false
            val downloader = synchronized(this) {
                if (stopped.get()) return@schedule true
                native ?: NativeMediaCache(context).also { native = it }
            }
            val ready = try {
                downloader.install()
            } catch (error: Throwable) {
                HLog.e("[Hchat:AutoCache] 定位原生入口失败，保留消息监听等待补装", error)
                false
            }
            if (!ready) HLog.e("[Hchat:AutoCache] 部分原生入口未就绪，已就绪媒体继续处理，等待补装")
            if (stopped.get()) return@schedule true
            observer.install()
            if (observing.compareAndSet(false, true)) {
                HLog.i("[Hchat:AutoCache] 新消息监听已接入 enabled=${prefs.getBoolean(AutoCacheSettings.KEY_ENABLE, false)}")
                registerSubscription(observer.subscribe { event ->
                    if (stopped.get() || event.outgoing || !prefs.getBoolean(AutoCacheSettings.KEY_ENABLE, false)) return@subscribe
                    val message = event.storedMessage ?: event.message ?: return@subscribe
                    if (!message.isIncoming() || !AutoCacheSettings.accepts(message,
                            prefs.getString(AutoCacheSettings.KEY_CACHE_TYPE, AutoCacheSettings.TYPE_ALL))) return@subscribe
                    if (!AutoCacheSettings.acceptsChat(message.talker,
                            prefs.getStringSet(AutoCacheSettings.KEY_CHATS, emptySet()).orEmpty())) return@subscribe
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
        if (stopped.get()) { pending.remove(key); lastSubmitted.remove(key); return }
        try {
            worker.schedule({
                try {
                    if (stopped.get()) {
                        pending.remove(key)
                        lastSubmitted.remove(key)
                        return@schedule
                    }
                    val enabled = HchatStorage.preferences(context.hostContext(), AutoCacheSettings.PREFS_NAME)
                        .getBoolean(AutoCacheSettings.KEY_ENABLE, false)
                    if (!enabled || WeChatApis.account()?.selfWxId() != account) {
                        pending.remove(key)
                        lastSubmitted.remove(key)
                        return@schedule
                    }
                    val store = WeChatApis.message().store()
                    val message = initial.msgId.takeIf { it > 0 }?.let { store?.getMessageById(it) }
                        ?: initial.msgSvrId.takeIf { it > 0 }?.let { store?.getMessageBySvrId(initial.talker, it) }
                        ?: initial
                    val cacheType = HchatStorage.preferences(context.hostContext(), AutoCacheSettings.PREFS_NAME)
                        .getString(AutoCacheSettings.KEY_CACHE_TYPE, AutoCacheSettings.TYPE_ALL)
                    val selectedChats = HchatStorage.preferences(context.hostContext(), AutoCacheSettings.PREFS_NAME)
                        .getStringSet(AutoCacheSettings.KEY_CHATS, emptySet()).orEmpty()
                    if (!AutoCacheSettings.acceptsChat(message.talker, selectedChats) ||
                        !AutoCacheSettings.accepts(message, cacheType)) {
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
                        if (stopped.get()) return@schedule
                        val submitted = native?.submit(message) == true
                        synchronized(this) {
                            if (submitted && !stopped.get()) lastSubmitted[key] = now
                        }
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
        } catch (error: java.util.concurrent.RejectedExecutionException) {
            pending.remove(key)
            lastSubmitted.remove(key)
            if (!stopped.get()) HLog.e("[Hchat:AutoCache] 下载检查入队失败", error)
        }
    }
    @Synchronized
    fun shutdown() {
        if (!stopped.compareAndSet(false, true)) return
        installation.close()
        worker.shutdownNow()
        pending.clear()
        lastSubmitted.clear()
        native = null
    }
}

private class AutoCacheSettingsProvider : SimpleFeatureSettingsProvider(
    AutoCacheFeature.ID, "自动缓存", "接收图片、视频和任意文件文档后自动触发微信原生下载",
    FeatureSettingsProvider.CATEGORY_ENHANCE, true
)