package t.mma.hooks.items.moments

import android.content.SharedPreferences
import t.mma.hooks.api.core.WeChatApis
import t.mma.hooks.core.PreferenceRuntimeOwner
import t.mma.hooks.core.BaseFeature
import t.mma.hooks.core.DexInstallScheduler
import t.mma.hooks.core.FeatureContext
import t.mma.preferences.HchatStorage
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MomentsAutoRefreshFeature : BaseFeature() {
    private var runtime: MomentsAutoRefreshRuntime? = null

    override fun featureId(): String = ID

    override fun name(): String = "朋友圈自动刷新"

    override fun onFeatureInit(context: FeatureContext) {
        registerSettingsProvider(MomentsAutoRefreshSettingsProvider())
    }

    override fun onFeatureInstall(context: FeatureContext) {
        val prefs = HchatStorage.preferences(context.hostContext(), MomentsAutoRefreshSettings.PREFS_NAME)
        val owner = PreferenceRuntimeOwner(prefs, setOf(MomentsAutoRefreshSettings.KEY_ENABLE),
            enabled = { prefs.getBoolean(MomentsAutoRefreshSettings.KEY_ENABLE, false) },
            create = { resources ->
                val installed = MomentsAutoRefreshRuntime(context, ::logError)
                runtime = installed
                resources.register { installed.destroy(); if (runtime === installed) runtime = null }
                val ticket = DexInstallScheduler.installation(ID + ":runtime", name(), DexInstallScheduler.Stage.WARMUP)
                resources.register { ticket.close() }
                ticket.schedule { installed.start(); true }
            }, onError = { logError("自动刷新热启停失败", it) })
        trackResource { owner.close() }
        owner.start()
    }
    override fun onFeatureDestroy(context: FeatureContext) {
        runtime?.destroy()
        runtime = null
    }

    companion object {
        const val ID = "moments_auto_refresh"
    }
}

private class MomentsAutoRefreshRuntime(
    context: FeatureContext,
    private val logger: (String, Throwable?) -> Unit
) {
    private val prefs = HchatStorage.preferences(context.hostContext(), MomentsAutoRefreshSettings.PREFS_NAME)
    @Volatile private var destroyed = false
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MomentsAutoRefreshSettings.KEY_ENABLE) reconfigure()
    }
    private var scheduler: ScheduledExecutorService? = null
    private var lastAttemptAt = 0L

    @Synchronized
    fun start() {
        if (destroyed) return
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        reconfigure()
    }

    @Synchronized
    fun destroy() {
        if (destroyed) return
        destroyed = true
        try { prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener) }
        finally { stopScheduler() }
    }

    @Synchronized
    private fun reconfigure() {
        if (destroyed || !prefs.getBoolean(MomentsAutoRefreshSettings.KEY_ENABLE, false)) {
            stopScheduler()
            return
        }
        if (scheduler?.isShutdown == false) return
        lastAttemptAt = 0L
        scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "Hchat-MomentsAutoRefresh").apply { isDaemon = true }
        }.also { it.scheduleWithFixedDelay(::tick, 1, 1, TimeUnit.SECONDS) }
    }

    @Synchronized
    private fun stopScheduler() {
        scheduler?.shutdown()
        scheduler = null
    }

    private fun tick() {
        if (destroyed || !prefs.getBoolean(MomentsAutoRefreshSettings.KEY_ENABLE, false)) return
        if (prefs.getBoolean(MomentsAutoRefreshSettings.KEY_TIME_WINDOW_ENABLE, false) &&
            !isInMomentsTimeWindow(
                prefs.getString(MomentsAutoRefreshSettings.KEY_START_TIME, MomentsAutoRefreshSettings.DEFAULT_START_TIME).orEmpty(),
                prefs.getString(MomentsAutoRefreshSettings.KEY_END_TIME, MomentsAutoRefreshSettings.DEFAULT_END_TIME).orEmpty()
            )
        ) return
        val intervalMillis = prefs.getInt(
            MomentsAutoRefreshSettings.KEY_INTERVAL_SECONDS,
            MomentsAutoRefreshSettings.DEFAULT_INTERVAL_SECONDS
        ).coerceAtLeast(0) * 1000L
        val now = System.currentTimeMillis()
        if (now - lastAttemptAt < intervalMillis) return
        lastAttemptAt = now
        runCatching { WeChatApis.snsApi()?.refreshTimeline() == true }
            .onSuccess {
                if (!it) lastAttemptAt = now - intervalMillis + minOf(intervalMillis, RETRY_DELAY_MS)
                saveResult(now, if (it) "刷新请求已提交" else "刷新请求提交失败")
            }
            .onFailure {
                lastAttemptAt = now - intervalMillis + minOf(intervalMillis, RETRY_DELAY_MS)
                saveResult(now, "刷新异常：${it.message.orEmpty()}")
                logger("朋友圈自动刷新失败", it)
            }
    }

    private fun saveResult(time: Long, result: String) {
        if (destroyed) return
        prefs.edit()
            .putLong(MomentsAutoRefreshSettings.KEY_LAST_TIME, time)
            .putString(MomentsAutoRefreshSettings.KEY_LAST_RESULT, result)
            .apply()
    }

    companion object {
        private const val RETRY_DELAY_MS = 30_000L
    }
}
